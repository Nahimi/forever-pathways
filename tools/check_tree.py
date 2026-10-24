"""Validate tree.json against an item list and report how the locks resolve.

Usage:
    python tools/check_tree.py [tree.json] [items.json] [--dump node_id] [--json out.json]

items.json is a flat JSON list of item ids ("ns:path"). The mod writes a fresh one
to config/foreverpathways/items.json whenever you run /pathways rebuild, and a
snapshot lives in preview/items.json.

The resolution rules mirror uk.foreverserver.pathways.LockResolver exactly:
  1. "ns:path" exact ids, in file order (first node wins)
  2. "re:<regex>" and "mod:<ns>", nodes taken from the outermost ring inwards
  3. "rest:<ns>" claims whatever is left in that namespace
  4. "rest:*" claims whatever is left in any namespace
Items matched by "ignore" are never locked.
"""
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def load(argv):
    args = [a for a in argv if not a.startswith("--")]
    tree_path = Path(args[0]) if args else ROOT / "src/main/resources/data/foreverpathways/tree.json"
    items_path = Path(args[1]) if len(args) > 1 else ROOT / "preview/items.json"
    tree = json.loads(tree_path.read_text(encoding="utf-8"))
    items = sorted(set(json.loads(items_path.read_text(encoding="utf-8"))))
    return tree, items


def spec_matcher(spec):
    if spec.startswith("re:"):
        rx = re.compile(spec[3:])
        return lambda i: rx.fullmatch(i) is not None
    if spec.startswith("mod:"):
        ns = spec[4:] + ":"
        return lambda i: i.startswith(ns)
    return lambda i: i == spec


def resolve(tree, items):
    ignore_m = [spec_matcher(s) for s in tree.get("ignore", [])]
    ignored = {i for i in items if any(m(i) for m in ignore_m)}
    pool = [i for i in items if i not in ignored]
    known = set(pool)
    owner = {}
    claims = defaultdict(list)
    warnings = []

    def claim(node_id, item):
        if item in owner:
            return False
        owner[item] = node_id
        claims[node_id].append(item)
        return True

    nodes = tree["nodes"]
    for n in nodes:
        for s in n.get("locks", []):
            if s.startswith(("re:", "mod:", "rest:")):
                continue
            if s in ignored:
                continue
            if s not in known:
                warnings.append(f"{n['id']}: unknown item {s}")
                continue
            if not claim(n["id"], s):
                warnings.append(f"{n['id']}: {s} already claimed by {owner[s]}")
    order = sorted(range(len(nodes)), key=lambda k: (-nodes[k]["ring"], k))
    for k in order:
        n = nodes[k]
        for s in n.get("locks", []):
            if not s.startswith(("re:", "mod:")):
                continue
            m = spec_matcher(s)
            hit = 0
            for i in pool:
                if i not in owner and m(i):
                    claim(n["id"], i)
                    hit += 1
            if hit == 0:
                warnings.append(f"{n['id']}: '{s}' claimed nothing")
    for n in nodes:
        for s in n.get("locks", []):
            if s.startswith("rest:") and s != "rest:*":
                ns = s[5:] + ":"
                for i in pool:
                    if i not in owner and i.startswith(ns):
                        claim(n["id"], i)
    for n in nodes:
        if "rest:*" in n.get("locks", []):
            for i in pool:
                if i not in owner:
                    claim(n["id"], i)
    return claims, owner, ignored, warnings


def ancestors(tree, node_id):
    by = {n["id"]: n for n in tree["nodes"]}
    seen = set()
    stack = [node_id]
    while stack:
        cur = by.get(stack.pop())
        if cur is None:
            continue
        for key in ("requires", "also"):
            p = cur.get(key)
            if p and p not in seen:
                seen.add(p)
                stack.append(p)
    return seen


def structure(tree):
    errors = []
    by = {n["id"]: n for n in tree["nodes"]}
    if len(by) != len(tree["nodes"]):
        errors.append("duplicate node ids")
    paths = {p["id"] for p in tree["paths"]} | {"core", "seal"}
    for n in tree["nodes"]:
        if n["path"] not in paths:
            errors.append(f"{n['id']}: unknown path {n['path']}")
        for key in ("requires", "also"):
            p = n.get(key)
            if p and p not in by:
                errors.append(f"{n['id']}: {key} {p} does not exist")
        p = by.get(n.get("requires"))
        if p and n["path"] != "seal" and p["ring"] >= n["ring"]:
            errors.append(f"{n['id']}: ring {n['ring']} is not outside its parent {p['id']} (ring {p['ring']})")
        a = by.get(n.get("also"))
        if a and a["ring"] > n["ring"]:
            errors.append(f"{n['id']}: also-node {a['id']} sits further out (ring {a['ring']})")
        if n.get("slot") and n["slot"] not in tree.get("slots", {}):
            errors.append(f"{n['id']}: slot {n['slot']} has no limit")
    seals = sorted((n for n in tree["nodes"] if n.get("seal")), key=lambda n: n["seal"]["opensRing"])
    outer = max(n["ring"] for n in tree["nodes"])
    for a, b in zip(seals, seals[1:]):
        if a["seal"].get("throughRing", a["seal"]["opensRing"]) != b["seal"]["opensRing"] - 1:
            errors.append(f"{a['id']}: its band should end at ring {b['seal']['opensRing'] - 1}, where {b['id']} starts")
    if seals and seals[-1]["seal"].get("throughRing", seals[-1]["seal"]["opensRing"]) != outer:
        errors.append(f"{seals[-1]['id']}: the last seal should cover up to the outer ring {outer}")
    return errors


TASK_ITEM_KINDS = ("mine", "use", "obtain", "craft")

# Blocks that generate in the overworld and break by hand or with the first pickaxe. Each one must
# open in the Core or on ring 1, because BlockGuard refuses to break a locked block: anything
# later would be a wall in the world the player cannot clear.
NATURAL = [
    r"minecraft:(dirt|coarse_dirt|rooted_dirt|grass_block|podzol|mycelium|dirt_path|mud|sand|red_sand|gravel|clay|snow|snow_block|ice|packed_ice|blue_ice)",
    r"minecraft:(stone|deepslate|andesite|diorite|granite|tuff|calcite|sandstone|red_sandstone|dripstone_block|pointed_dripstone|smooth_basalt|cobblestone|mossy_cobblestone)",
    r"minecraft:(cactus|sugar_cane|vine|lily_pad|moss_block|moss_carpet|short_grass|tall_grass|fern|large_fern|dead_bush|seagrass|kelp|sea_pickle|sweet_berries|glow_lichen|hanging_roots|big_dripleaf|small_dripleaf|spore_blossom|pink_petals|cobweb|bee_nest|azalea|flowering_azalea|pumpkin|melon|bone_block)",
    r"minecraft:(dandelion|poppy|blue_orchid|allium|azure_bluet|.*_tulip|oxeye_daisy|cornflower|lily_of_the_valley|sunflower|lilac|rose_bush|peony|brown_mushroom|red_mushroom|brown_mushroom_block|red_mushroom_block|mushroom_stem)",
    r"minecraft:.*_leaves", r"minecraft:(dead_)?(tube|brain|bubble|fire|horn)_coral(_block|_fan)?",
    r"minecraft:(coal_ore|deepslate_coal_ore|copper_ore|deepslate_copper_ore)",
    r"minecraft:(oak|spruce|birch|jungle|acacia|dark_oak|mangrove|cherry)_log", r"minecraft:(mangrove_roots|muddy_mangrove_roots|bamboo)",
    r"minecraft:(white|orange|yellow|brown|red|light_gray)?_?terracotta",
    r"(dynamictrees|dynamictreescompat|dtterralith|dynamictreesplus):.*_branch(?<!crimson_branch)(?<!warped_branch)",
    r"terrain_slabs:(?!(basalt|crimson_nylium|warped_nylium|netherrack|soul_sand|soul_soil|terrain_blackstone|endstone|terrain_prismarine)_slab).*",
    r"projectvibrantjourneys:(?!(cindercane|crimson_nettle|warped_nettle|charred_bones|nettle_soup)$).*",
    r"spawn:(algal_sand|blasted_sand|broad_grass|short_windswept_grass|tall_windswept_grass|fallen_leaves|date_leaves|dangling_date_leaves|red_algae|red_algae_block|hazy_algae_block|barnacle|barnacle_cluster|big_sea_pickle|sea_star|sea_urchin|tropical_wildflowers|ant_mound|anthill|reefstone|algal_reefstone|blasted_sandstone|date_log|rotten_log)",
    r"spawn:(dead_)?(heart|reed|spike|thorn|wax)_coral(_block|_fan)?", r"spawn:.*_dragon_reeds",
    r"cnb:.*_(minipad_flower|waterlily)", r"farmersdelight:wild_.*", r"supplementaries:wild_flax",
    r"create:(asurine|crimsite|limestone|ochrum|scorchia|scoria|veridium)",
]


def descendants(tree, node_id):
    kids = defaultdict(set)
    for n in tree["nodes"]:
        for key in ("requires", "also"):
            if n.get(key):
                kids[n[key]].add(n["id"])
    seen, stack = set(), [node_id]
    while stack:
        for k in kids.get(stack.pop(), ()):
            if k not in seen:
                seen.add(k)
                stack.append(k)
    return seen


def deadlocks(tree, owner, items=(), only=None):
    """A task whose target is locked where the player cannot reach it first can never finish.

    Hard (DEADLOCK): the target is locked by the task's own node, by a node that needs this one, by
    a node further out than this one (or, for a seal, inside its own band), or by a fork or school
    node off the route, which the player may have closed. Soft (note): locked by a reachable node
    beyond ring 1 that is not on the route, so the player has to buy it first.
    """
    hard, soft = [], []
    by = {n["id"]: n for n in tree["nodes"]}
    for n in tree["nodes"]:
        if only and n["id"] != only:
            continue
        t = n.get("task")
        if not t or t["kind"] not in TASK_ITEM_KINDS:
            continue
        route = ancestors(tree, n["id"]) | {"forever:core"}
        below = descendants(tree, n["id"]) | {n["id"]}
        seal = n.get("seal")
        limit = seal["opensRing"] - 1 if seal else n["ring"]
        for target in t.get("target", "").split(","):
            target = target.strip()
            if not target:
                continue
            if target.startswith("re:"):
                rx = re.compile(target[3:])
                matched = [i for i in items if rx.fullmatch(i)]
                if not matched:
                    hard.append(f"{n['id']}: task target {target} matches no item")
                    continue
                whos = {owner.get(i) for i in matched}
            else:
                whos = {owner.get(target)}
            problems = []
            for who in whos:
                if who is None or who in route:
                    problems.append(None)
                    continue
                w = by[who]
                if who in below or w["ring"] > limit or (w.get("slot") and not seal):
                    problems.append(("hard", who))
                elif w["ring"] > 1:
                    problems.append(("soft", who))
                else:
                    problems.append(None)
            if None in problems:
                continue
            kinds = {p[0] for p in problems}
            names = ", ".join(sorted({p[1] for p in problems}))
            if kinds == {"hard"}:
                hard.append(f"{n['id']}: task needs {target}, locked by {names}, which it can never have first")
            else:
                soft.append(f"{n['id']}: task needs {target}, locked by {names} (off its route)")
    return hard, soft


BOUNTY_KINDS = {
    "walk", "sprint", "swim", "crouch", "climb", "fly", "boat", "minecart", "horse", "strider", "travel", "jump",
    "sleep", "breed", "enchant", "fish", "trade", "mobs", "mine", "kill", "use", "craft", "obtain", "plant", "eat",
    "hold", "brew", "tame", "underwater",
}
BOUNTY_ITEM_KINDS = ("mine", "use", "obtain", "craft", "plant", "eat", "hold")
POTIONS = {"swiftness", "slowness", "healing", "harming", "fire_resistance", "night_vision", "water_breathing",
           "strength", "regeneration", "poison", "invisibility", "leaping", "weakness", "slow_falling",
           "turtle_master", "luck", "wind_charged", "weaving", "oozing", "infested"}


def bounty_problems(tree, owner, items):
    """Bounties join a player's pool once their node is owned, so everything they need must be open by then.

    Every item a bounty names (to mine, craft, plant, eat or hold) must exist and be locked by nothing, the
    bounty's own node, or a node on that node's route. Mobs are checked against spawn eggs when there is one.
    """
    out = []
    by = {n["id"]: n for n in tree["nodes"]}
    known = set(items)
    seen = set()
    tiers = len(tree.get("bountyTiers", [])) or 6
    for b in tree.get("bounties", []):
        bid = b["id"]
        if bid in seen:
            out.append(f"bounty {bid}: duplicate id")
        seen.add(bid)
        req = b.get("requires") or "forever:core"
        if req not in by:
            out.append(f"bounty {bid}: requires {req}, which does not exist")
            continue
        if b["kind"] not in BOUNTY_KINDS:
            out.append(f"bounty {bid}: unknown kind {b['kind']}")
        if not 1 <= b.get("tier", 1) <= tiers:
            out.append(f"bounty {bid}: tier {b.get('tier')} out of range")
        if not 1 <= b.get("days", 1) <= 3:
            out.append(f"bounty {bid}: {b.get('days')} days is outside 1 to 3")
        if b.get("amount", 0) < 1 or b.get("reward", 0) < 1:
            out.append(f"bounty {bid}: amount and reward must be at least 1")
        route = ancestors(tree, req) | {req, "forever:core"}
        targets = [t.strip() for t in b.get("target", "").split(",") if t.strip()]
        if b["kind"] in ("kill", "tame", "brew") or b["kind"] in BOUNTY_ITEM_KINDS:
            if not targets:
                out.append(f"bounty {bid}: {b['kind']} needs a target")
        if b["kind"] == "brew":
            for t in targets:
                name = t.split(":")[-1]
                for pre in ("long_", "strong_"):
                    if name.startswith(pre):
                        name = name[len(pre):]
                if name not in POTIONS:
                    out.append(f"bounty {bid}: {t} is not a known potion")
        if b["kind"] in ("kill", "tame"):
            for t in targets:
                egg = t + "_spawn_egg"
                ns = t.split(":")[0]
                if ns == "minecraft" and egg not in known and t not in ("minecraft:wither",):
                    out.append(f"bounty {bid}: no spawn egg for {t}; check the entity id")
        if b["kind"] in BOUNTY_ITEM_KINDS:
            for t in targets:
                if t.startswith("re:"):
                    rx = re.compile(t[3:])
                    matched = [i for i in items if rx.fullmatch(i)]
                    if not matched:
                        out.append(f"bounty {bid}: {t} matches no item")
                    if all(owner.get(i) not in (None, *route) for i in matched):
                        out.append(f"bounty {bid}: every match of {t} is locked off its route")
                    continue
                if t not in known:
                    if b["kind"] != "mine":  # mined targets are blocks, and crops have no item of their own
                        out.append(f"bounty {bid}: {t} is not an item")
                    continue
                who = owner.get(t)
                if who is not None and who not in route:
                    out.append(f"bounty {bid}: {t} is locked by {who}, which {req} does not need")
    return out


def perk_problems(tree):
    """Every stat's perks must add up to exactly its maximum minus its base."""
    out = []
    stats = tree.get("stats", {})
    totals = Counter()
    for n in tree["nodes"]:
        for k, v in (n.get("perks") or {}).items():
            if k not in stats:
                out.append(f"{n['id']}: perk {k} is not a stat")
            totals[k] += v
    for k, s in stats.items():
        want = round(s["max"] - s["base"], 4)
        if round(totals[k], 4) != want:
            out.append(f"stat {k}: perks add up to {totals[k]:.2f}, but max - base is {want:.2f}")
    return out


def late_natural(tree, owner, items):
    """Natural blocks that open after ring 1."""
    by = {n["id"]: n for n in tree["nodes"]}
    rx = [re.compile(p) for p in NATURAL]
    out = []
    for i in items:
        if not any(r.fullmatch(i) for r in rx):
            continue
        who = owner.get(i)
        if who and by[who]["ring"] > 1:
            out.append(f"{i} is natural but opens on ring {by[who]['ring']} ({who})")
    return out


def levels(tree):
    """How many ordinary nodes sit inside each seal, counting the fork and school limits."""
    rows = []
    nodes = [n for n in tree["nodes"] if n["path"] not in ("core", "seal")]
    for s in [n for n in tree["nodes"] if n.get("seal")]:
        ring = s["seal"]["opensRing"]
        inside = [n for n in nodes if n["ring"] < ring]
        slots = Counter(n["slot"] for n in inside if n.get("slot"))
        closed = set()
        for slot, count in slots.items():
            extra = [n["id"] for n in inside if n.get("slot") == slot][tree["slots"].get(slot, count):]
            closed.update(extra)
        # anything that can only be reached through a closed slot node is out of reach too
        changed = True
        while changed:
            changed = False
            for n in inside:
                if n["id"] not in closed and (n.get("requires") in closed or n.get("also") in closed):
                    closed.add(n["id"])
                    changed = True
        reachable = len(inside) - len(closed)
        rows.append((s["title"], s["seal"]["level"], reachable))
    return rows


def main():
    tree, items = load(sys.argv[1:])
    claims, owner, ignored, warnings = resolve(tree, items)
    errors = structure(tree)
    print(f"{len(tree['nodes'])} nodes, {len(items)} items, {len(ignored)} ignored, {len(owner)} locked")
    for e in errors:
        print("ERROR", e)
    for w in warnings:
        print("warn ", w)
    hard, soft = deadlocks(tree, owner, items)
    for d in hard:
        print("DEADLOCK", d)
    for d in soft:
        print("note ", d)
    for d in bounty_problems(tree, owner, items):
        print("BOUNTY", d)
        hard.append(d)
    perks = perk_problems(tree)
    for d in perks:
        print("PERKS", d)
    known = set(items)
    for n in tree["nodes"]:
        if n.get("item") and n["item"] not in known:
            print("warn ", f"{n['id']}: icon {n['item']} is not an item")
    for b in tree.get("bounties", []):
        if b.get("item") and b["item"] not in known:
            print("warn ", f"bounty {b['id']}: icon {b['item']} is not an item")
    natural = late_natural(tree, owner, items)
    for d in natural:
        print("NATURAL", d)
    print("\nSeals (level needed / ordinary nodes available inside):")
    for title, need, have in levels(tree):
        flag = "" if have >= need * 1.4 else "  <- tight"
        print(f"  {title:26} {need:3} / {have}{flag}")
    print("\nItems per node:")
    for n in tree["nodes"]:
        c = claims.get(n["id"], [])
        sample = ", ".join(i.split(":")[1] for i in c[:6])
        print(f"  r{n['ring']} {n['id']:32} {len(c):5}  {sample}")
    rest_ns = Counter(i.split(":")[0] for i, who in owner.items() if who == "forever:curiosities")
    print("\nCuriosities by namespace:", dict(rest_ns))
    cost = sum(n["cost"] for n in tree["nodes"])
    reward = sum((n.get("task") or {}).get("reward", 0) for n in tree["nodes"])
    disc = sum(d["reward"] for d in tree.get("discoveries", []))
    bounties = tree.get("bounties", [])
    print(f"\nEconomy: the tree costs {cost} shards. Node tasks pay {reward} once and discoveries {disc} once.")
    print(f"Bounties: {len(bounties)}, {tree['rules'].get('bountySlots', 2)} offered a day.")
    for tier in sorted({b.get("tier", 1) for b in bounties}):
        group = [b for b in bounties if b.get("tier", 1) == tier]
        avg = sum(b["reward"] for b in group) / len(group)
        print(f"  tier {tier}: {len(group):3} bounties, {min(b['reward'] for b in group)} to "
              f"{max(b['reward'] for b in group)} shards (mean {avg:.1f}), {min(b['days'] for b in group)} to "
              f"{max(b['days'] for b in group)} days")
    for k, st in tree.get("stats", {}).items():
        print(f"  {st['title']:16} {round(st['base'] * 100)}% to {round(st['max'] * 100)}%")
    for arg in sys.argv:
        if arg.startswith("--dump="):
            nid = arg.split("=", 1)[1]
            print("\n".join(claims.get(nid, [])))
        if arg.startswith("--json="):
            Path(arg.split("=", 1)[1]).write_text(json.dumps({k: v for k, v in claims.items()}), encoding="utf-8")
    sys.exit(1 if errors or hard or natural or perks else 0)


if __name__ == "__main__":
    main()
