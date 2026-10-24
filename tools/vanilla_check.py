"""Prove the tree can be finished on a given set of mods, vanilla included.

Usage:
    python tools/vanilla_check.py [tree.json] [namespace ...]

With no namespaces it checks vanilla: minecraft only. Pass namespaces to add mods, or "all" to
check the full Forever Server pack.

This mirrors Availability.java: a labour naming nothing the install has is dropped, and its node then
costs only Focal Shards. The simulation then owns everything it can, breaks every seal whose level it
can reach, and repeats until nothing more opens. If anything is left over, the tree is not finishable
on that install and the reason is printed.
"""
import json
import re
import sys
from pathlib import Path

ITEM_KINDS = {"mine", "craft", "obtain", "hold", "use", "furnace"}
ENTITY_KINDS = {"kill", "tame", "mobs"}


def namespaces_of(target):
    out = set()
    for raw in (target or "").split(","):
        part = raw.strip()
        if not part:
            continue
        if part.startswith("#"):
            out.add("minecraft")
            continue
        if part.startswith("re:"):
            out |= set(re.findall(r"[a-z0-9_.-]+", part[3:].split(":")[0]))
            continue
        if ":" in part:
            out.add(part.split(":", 1)[0])
    return out


def survives(task, installed):
    """True when this labour can still be finished, by the same rule Availability.java uses."""
    if not task or not task.get("target"):
        return True
    if task["kind"] == "advancement":
        # A mod's advancement is only possible with that mod installed.
        return namespaces_of(task["target"]) <= installed | {"minecraft"}
    if task["kind"] not in ITEM_KINDS and task["kind"] not in ENTITY_KINDS:
        return True
    return bool(namespaces_of(task["target"]) & installed)


def main():
    tree_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
        "src/main/resources/data/foreverpathways/tree.json")
    tree = json.loads(tree_path.read_text(encoding="utf-8"))
    nodes = tree["nodes"]
    by_id = {n["id"]: n for n in nodes}
    args = sys.argv[2:]
    if args == ["all"]:
        installed = {"minecraft"}
        for node in nodes:
            installed |= namespaces_of(node.get("item"))
            for lock in node.get("locks", []):
                if lock.startswith(("mod:", "rest:")):
                    installed.add(lock.split(":", 1)[1])
                else:
                    installed |= namespaces_of(lock)
        installed.discard("*")
        installed -= {"re", "mod", "rest", "c", "fabric"}
        label = "the full pack"
    else:
        installed = {"minecraft"} | set(args)
        label = ", ".join(sorted(installed))

    dropped = [n for n in nodes if n.get("task") and not survives(n["task"], installed)]
    bounties = [b for b in tree["bounties"] if not survives(b, installed)]
    kept_bounties = [b for b in tree["bounties"] if survives(b, installed)]

    seals = [n for n in nodes if n.get("seal")]
    ordinary = [n for n in nodes if not n.get("seal") and n.get("requires")]

    owned = {tree.get("rules") and nodes[0]["id"]}
    owned = {n["id"] for n in nodes if not n.get("requires")}
    slots_used = {}

    def seal_for(ring):
        best = None
        for seal in sorted(seals, key=lambda s: s["seal"]["opensRing"]):
            if seal["seal"]["opensRing"] <= ring:
                best = seal
        return best

    level = 0
    changed = True
    while changed:
        changed = False
        for node in nodes:
            if node["id"] in owned:
                continue
            if node.get("requires") and node["requires"] not in owned:
                continue
            if node.get("also") and node["also"] not in owned:
                continue
            if node.get("seal"):
                if level < node["seal"]["level"]:
                    continue
            else:
                seal = seal_for(node["ring"])
                if seal and seal["id"] not in owned:
                    continue
            slot = node.get("slot")
            if slot:
                limit = tree["slots"].get(slot, 10 ** 9)
                extra = sum((by_id[i].get("slotBonus") or {}).get(slot, 0) for i in owned)
                if slots_used.get(slot, 0) >= limit + extra:
                    continue
                slots_used[slot] = slots_used.get(slot, 0) + 1
            owned.add(node["id"])
            if not node.get("seal") and node.get("requires"):
                level += 1
            changed = True

    # Nodes behind a slot are exclusive on purpose: three schools of eight, one Ranger fork. They are
    # not failures. What would be a failure is anything NEEDED sitting behind one, so those are listed
    # separately and only the rest counts.
    def behind_slot(node):
        seen = set()
        cur = node
        while cur is not None and cur["id"] not in seen:
            seen.add(cur["id"])
            if cur.get("slot"):
                return True
            cur = by_id.get(cur.get("requires"))
        return False

    missing = [n for n in nodes if n["id"] not in owned and not behind_slot(n)]
    exclusive = [n for n in nodes if n["id"] not in owned and behind_slot(n)]
    cost = sum(n["cost"] for n in nodes if n["id"] in owned)
    income = sum(n["task"]["reward"] for n in nodes
                 if n["id"] in owned and n.get("task") and survives(n["task"], installed))
    income += sum(d["reward"] for d in tree.get("discoveries", []))

    print(f"Install: {label} ({len(installed)} namespaces)")
    print(f"Labours dropped: {len(dropped)} of "
          f"{len([n for n in nodes if n.get('task')]) + len(dropped) - len(dropped)}")
    for node in dropped[:40]:
        print(f"   r{node['ring']:<2} {node['title']:<22} {node['task']['title']}")
    if len(dropped) > 40:
        print(f"   ... and {len(dropped) - 40} more")
    print(f"Bounties: {len(kept_bounties)} kept, {len(bounties)} dropped")
    print(f"Nodes reachable: {len(owned)} of {len(nodes)}"
          + (f", plus {len(exclusive)} left by choice behind a slot" if exclusive else ""))
    if missing:
        print("NOT COMPLETABLE. Left over:")
        for node in missing[:30]:
            why = []
            if node.get("requires") and node["requires"] not in owned:
                why.append("needs " + node["requires"])
            if node.get("also") and node["also"] not in owned:
                why.append("also needs " + node["also"])
            if node.get("seal") and level < node["seal"]["level"]:
                why.append(f"level {node['seal']['level']} > {level}")
            print(f"   r{node['ring']:<2} {node['title']:<22} {', '.join(why) or 'slot full'}")
        return 1
    eyes = [n for n in nodes if n["ring"] == max(x["ring"] for x in nodes) and not n.get("seal")]
    unreachable_eyes = [n for n in eyes if n["id"] not in owned]
    if unreachable_eyes:
        print("WARNING: these eyes cannot be reached, so the End portal cannot be filled:")
        for node in unreachable_eyes:
            print(f"   {node['title']} needs {node['requires']}")
        return 1
    print(f"COMPLETABLE. Final Pathway Level {level}, {cost} Focal Shards to buy the whole tree, "
          f"every eye included.")
    print(f"One-off income from labours and discoveries: {income}. The rest comes from bounties: "
          f"{len(kept_bounties)} in the pool, 2 offered a dawn.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
