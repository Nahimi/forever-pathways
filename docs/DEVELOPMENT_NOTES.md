# Forever Pathways

The progression tree for the Forever Server (NeoForge 1.21.1, ProgressiveStages 3).
Press **O**, or click the Pathways tab (the gold ring with an amethyst core) in Legendary Tabs. The map opens as
a window the same way the other tabs do, with the tab row along its top edge, so you can switch straight
back to your inventory or any other tab.

## How it plays

- **Rings.** The Core sits in the middle. Four paths start on ring 1: Survivor, Ranger, Builder and
  Homesteader. The Technician grows out of the Builder on ring 2 and the Magician out of the
  Apothecary on ring 6. Rings 1 to 14 are tiers (Stone Age, Copper Age, Iron Age, Hearth, Gilded Age, Diamond Age, The Deep, The
  Nether, Ashen Age, Industry, Relics, Flight, The Arcane, High Voltage). Ring 15 holds the sixteen
  End Remastered eyes.
- **Seals.** Seven seals lock everything from ring 2 outwards. Each one is drawn as a shaded band
  over exactly the rings it covers, with a padlock at twelve o'clock. Some cover one ring, some
  cover two. A seal needs a **Pathway Level** (the number of nodes you own) and one large task.
  Hold its padlock to break it, and the band lifts off the rings underneath.
- **Focal Shards.** You start with 2, and surviving earns nothing. Focal Shards come from tasks, bounties
  and one-off discoveries (vanilla advancements).
- **Tasks.** Every task belongs to a node or a seal. It only starts counting once its node is
  open, meaning everything except the task and the Focal Shards is met. Finishing it pays its shards
  once and lets you buy the node. It never comes back.
- **Bounties.** 111 timed jobs in six tiers, modelled on the vanilla advancements: kill mobs,
  mine ore, plant seeds, eat food, craft something hard, brew a potion, befriend a wolf, hold a
  stack of something rare in your hand, walk, swim, row, ride a minecart or stay underwater.
  Each one joins your **pool** when you own its node, so the pool grows with the tree.
  - At each dawn, each of your two bounty slots that is empty gets a random bounty from your
    pool. Higher tiers come up a little more often.
  - Each bounty has a deadline of 1 to 3 dawns. If time runs out it simply leaves, and its slot
    refills at the next dawn.
  - A finished bounty's slot stays empty until the next dawn, so finishing both means waiting
    for tomorrow.
  - Tier I pays 1 or 2 Focal Shards for small jobs (kill 8 spiders, plant 20 seeds, walk 1,500 blocks), rising to 5 to 8 for tier VI.
- **Stats.** Everyone starts slow and weak: 70% movement speed, 40% breaking speed, 80% boat speed
  and 50% attack damage. Perks on nodes raise them to 120%, 100%, 100% and 110%.
- **Death.** You lose 1 Focal Shard, every running task and bounty loses 25% of its progress, and they
  are all frozen for 10 minutes. A bounty's deadline keeps running while frozen. Unlocked
  nodes are never taken away.
- **Forks.** Hunter and Scout exclude each other until you buy Second Path. You may master
  three magic schools, five after Archmage.

## The early game

The first three tiers work on vanilla and follow Tough Beginnings when it is installed:

| Where | Node | What it opens |
| --- | --- | --- |
| Core | | Raw flint, flint shards, the flint knife, plant fibre, fibre rope and the pile of fibre (Tough Beginnings), Vibrant Journeys rocks and twigs, leaves, flint, the soft ground, wild plants, coral, raw food, healing herbs and purified water. No trees. |
| Stone Age (ring 1) | Woodcutter | The flint axe, blocks of wood and tough blocks of wood (Tough Beginnings), the wooden tools and every tree: logs, Dynamic Trees branches (Terralith and Spawn trees included), planks and wooden blocks, the crafting table, chests, barrels, ladders, the campfire. |
| Stone Age (ring 1) | Quarry | The stone pickaxe, cobblestone (four rocks make one), the natural stones, the furnace, coal, torches, and copper ore to smelt into ingots. Needs Woodcutter too. |
| Copper Age (ring 2) | Copper Tools | The stone axe, copper blocks, the lightning rod. Task: smelt 48 copper ingots. |
| Copper Age (ring 2) | Prospector | Iron ore and raw iron. Task: mine 96 copper ore. |
| Copper Age (ring 2) | Gardener, Earthworks, Skirmisher | The other first tools: the stone hoe, the stone shovel and the stone sword. |
| Iron Age (ring 3) | Iron | Iron ingots, nuggets, blocks and the blast furnace. Task: mine 160 iron ore. Needs Copper Tools too. |

There are no wooden or flint tools besides the knife and the axe. Every ore opens one node before the
metal it gives (copper in Quarry, iron in Prospector, gold, redstone, lapis and diamond in Deep
Prospector), so every "mine this ore" task can actually be done.

## Stats and perks

| Stat | Start | Max | Where the perks are |
| --- | --- | --- | --- |
| Movement speed | 70% | 120% | Scout, Outfitter, Long Stride (+5%); Pathfinder, Fleet Foot (+10%); Windrunner (+15%) |
| Breaking speed | 40% | 100% | Copper Tools (+5%), Iron Tools (+10%), Diamond Tools (+15%), Netherite Gear (+15%); Calloused Hands, Strong Back, Tireless (+5% each) |
| Attack damage | 50% | 110% | Skirmisher, Brawler, Battlemage, Helvar (+5%); Veteran (+10%); Warrior, Champion (+15%) |
| Boat speed | 80% | 100% | Oarsman, Sea Legs, Boatwright, Angler (+5% each) |
| Maximum health | 10 hearts | 20 hearts | The Constitution path: twenty nodes, +5% each, which is half a heart apiece |

Constitution is optional and costly: twenty nodes, 1,179 Focal Shards in all, running from ring 2 out to
ring 14. A second chain branches off Second Wind so the path can fill two nodes on some rings. It opens
no items; every node asks for a task, from surviving five days without dying to surviving sixty.

Movement and breaking speed are modifiers on the vanilla attributes (breaking uses
`player.block_break_speed`, so it slows mining and tree felling alike). Attack damage multiplies the
whole hit, weapon included. Boat speed is a new attribute, `foreverpathways:boat_speed`, which
scales the push a boat gets from its rider. Creative and spectator players are left at 100%.
The numbers live in `tree.json` under `stats` and each node's `perks`; the checker makes sure each
stat's perks add up to exactly its maximum.

## Enchanting

Enchanting is tiered by the strength of what the table will offer, not by naming individual
enchantments, so mod enchantments are covered too.

| Node | Ring | Table offers up to |
| --- | --- | --- |
| Enchanting | 7 | level 5, the weak enchantments only |
| Library | 9 | level 15 |
| Grand Library | 10 | level 30, everything |

`EnchantPowerMixin` clamps the three offers an enchanting table shows, and clears the clue on any
offer it clamps so the table never promises what it will not give. Creative players are not capped.

## Motion

Plates drift on their own phase, scale on a spring when hovered or held, and tremble as a hold closes.
Holding a node closes a ring round it, tightens a glow, spirals motes in and fills the line from its
parent. When the node opens it throws a wobbling ripple with trailing rings and sparks, plates across a wide
part of the map are shoved by the passing wavefront, and light runs down the line it came from. A hold
takes 1.9 seconds. The code is `client/Motion.java`
and the motion block in `preview/index.html`.

## Locks

A locked item cannot be used, placed, eaten, swung, worn or attacked with, and a locked block cannot
be broken or opened. `BlockGuard` refuses on the client first, using ProgressiveStages' own client
lock cache, so nothing is predicted (no ghost blocks, no eating animation, no lost items), and then
refuses again on the server. Locked armour is taken off within a second.

Both sides work out which node locks an item from `tree.json` itself, and the server pushes each
player their owned nodes on join and whenever they change. So the client never predicts a break
it is not allowed: a locked block will not even start to crack.

**Doors, trapdoors, fence gates, buttons, levers and bells can always be used**, locked or not
(`alwaysUsable` in `tree.json`). Unlocking them is what lets you craft, place and break them.
ProgressiveStages' own `block_block_interaction` would keep them shut, so Pathways switches that
option off in `config/progressivestages.toml` at start-up (it logs when it does) and refuses other
locked blocks itself. Restart once after the first launch.

Because a locked block cannot be broken, every block that generates on the surface or in the first
caves opens in the Core or on ring 1: soft ground, plants, leaves, rocks, Terrain Slabs, Spawn's ground
cover and coral, Create's natural stones, and every tree branch. Nether ground opens with the Nether
node and End stone with The End.

In `config/progressivestages.toml`, set `starting_stages = ["forever:core"]`; the old
`forever:survivor` id no longer exists. (Pathways also grants the core itself on first join.)

Version 0.10.0 adds Monobank, renames shards to **Focal Shards** (with an icon of their own), splits the
healing items across five nodes instead of one, expands Homesteader from 17 nodes to 25, and raises every
node and seal cost by a quarter. Tipped and spectral arrows moved off Fletcher to Alchemical Arrows, which
needs Brewing. Canteens and the water purifier are metalwork now and wait for Copper Tools, and bandages
moved from the Core to Herbalist; only healing herbs and purified water bottles are still free. Node ids are unchanged from 0.9.x apart from the new ones, so a save carries over, but the
new nodes start unowned and their items are locked until bought.

Version 0.9.0 makes tasks one-off again and replaces the repeatable bounties with timed daily ones.
Tasks you already finished stay finished. Old bounty progress is dropped, and the first two bounties
are dealt on the next update after you join. Node ids changed in 0.7.0 and 0.8.0 (magic and the
perk chains were split into new nodes), so a fresh world is best.

Operators can run `/pathways bounties <players>` to throw away a player's bounties and deal two new
ones at once, which is handy for testing.

## Boss eyes

Six eyes come only from Bosses' Rise bosses. The mod adds the eye to the boss's drops when a player
lands the killing blow, and strips those eyes from every loot table (chests, mob drops, fishing) and
every villager and wandering trader trade:

| Boss | Eye | Chance |
| --- | --- | --- |
| Pirate captain | Rogue Eye | 34% each |
| Helvar | Cursed Eye | 100% |
| Skor | Cold Eye | 100% |
| Sirok | Old Eye | 100% |
| Nerakyss | Guardian Eye | 100% |
| The Ashlord | Nether Eye | 100% |

The list lives in `tree.json` under `bossDrops`. The other ten eyes still come from End Remastered.
End Remastered's own config sets how many eyes the portal needs; set it to 16 so every eye on the
outer ring matters.

## Legendary Tabs

`data/legendarytabs/tabs/pathways.json` registers the tab. The window is `min(screen width - 24, 480)`
by `min(screen height - 52, 300)`, and `PathwaysScreen` uses the same sums, so the tab row lines up
with the window's top edge at every GUI scale.

## Files

| File | What it is |
| --- | --- |
| `src/main/resources/data/foreverpathways/tree.json` | The whole tree: nodes, locks, tasks, seals, rules, discoveries. Edit this. |
| `tools/check_tree.py` | Validates the tree and shows exactly which items each node locks. |
| `tools/build_preview.py` | Rebuilds `preview/data.js`. Open `preview/index.html` to see the map in a browser. |
| `preview/items.json` | A snapshot of every item id in the pack, used by the two tools. |

## Stage files

The mod writes the ProgressiveStages files itself, from `tree.json` and the live item
registry, into `config/progressivestages/stages/`. It does this at start-up whenever the
tree or the mod list has changed. **Restart the server once after that happens**, so
ProgressiveStages loads the new files. The log says when a restart is needed.

Old stage folders tagged `forever` that are no longer in the tree are removed. Stage
folders from anything else are left alone.

Commands (operator level 2):

- `/pathways rebuild` forces the files to be rewritten and refreshes `config/foreverpathways/items.json`.
- `/pathways shards <players> <amount>` gives (or, with a negative amount, takes) Focal Shards.

In creative mode, holding a node toggles it without costing anything.

## Editing tree.json

Each node has an `id`, a `title`, a `path`, a `ring`, an optional `requires` (its parent on
the map) and `also` (a second requirement drawn as a dashed line), a `cost`, an icon `item`,
an `opens` description, an optional `task`, and `locks`.

A child must sit on a higher ring than its parent. Run `python tools/check_tree.py` after
every change. It catches broken links, specs that lock nothing and seal levels that cannot be
reached. It fails on any task that can never finish, because its target is locked by the node
itself, by a node that needs it, by a node further out or by a fork the player may have closed. It
also fails on any natural block that opens after ring 1 (the list is `NATURAL` at the top of
the checker).

**Lock specs.** Each item ends up owned by exactly one node:

1. `"minecraft:iron_ingot"` exact ids win first.
2. `"re:<regex>"` (matched against `namespace:path`) and `"mod:<namespace>"` come next, with nodes on outer rings claiming first.
3. `"rest:<namespace>"` takes whatever is left of that mod.
4. `"rest:*"` takes anything left from any mod. Curiosities uses this, so new mods start there.

Anything matched by the top-level `ignore` list is never locked.

**Task kinds.** These kinds need no target:

- **Movement, in blocks:** `walk`, `sprint`, `swim`, `crouch`, `climb`, `fly`, `travel` (every way of moving added together).
- **Riding, in blocks:** `boat`, `minecart`, `horse`, `strider`.
- **Underwater:** `underwater`, in seconds with your head under water.
- **Counts:** `jump`, `sleep`, `breed`, `enchant`, `fish`, `trade`, `brew`, `anvil`, `furnace`, `raid`, `mobs`.
- **`streak`:** days survived in one life.

These take a `target`, a comma-separated list of ids where `re:` entries are allowed:

- **Blocks broken:** `mine`.
- **Entities killed:** `kill`.
- **Items:** `use`, `craft`, `obtain`. `plant` and `eat` are the same as `use`, since planting a
  seed and eating food both count as using the item. `craft` also counts smithing.
- **Held:** `hold`, how many of the target items are in the main hand at once.
- **Tamed:** `tame`, animals of the target types tamed (wolves, cats, parrots, horses).
- **Brewed:** `brew` with a target counts potions of those types taken out of a brewing stand, for
  example `minecraft:swiftness,minecraft:long_swiftness,minecraft:strong_swiftness`. Without a
  target it counts uses of a brewing stand, as before.
- **`advancement`:** the target is an advancement id.

**Seals** are nodes with `"path": "seal"` and a `seal` block: `level`, `opensRing` and `throughRing`.
The bands must line up with no gaps, and the last one must reach the eye ring; the checker tells you if not.

**Slots.** A node with `"slot": "school"` counts towards the limit in the top-level `slots`. A node
with `"slotBonus": {"school": 2}` raises that limit for whoever owns it.

**Rules.** The `rules` block sets the starting Focal Shards (2), the shards per day (0), the length of
a day, the death penalties and `bountySlots`, the number of bounties dealt each dawn (2).

**Bounties** are listed under `bounties`: `id`, `tier` (1 to 6, named in `bountyTiers`), `title`,
`kind`, `target`, `amount`, `reward`, `days` (1 to 3 dawns to finish it), `requires` (the node that
adds it to the pool) and an icon `item`. The checker makes sure every item a bounty names exists
and is unlocked by its node or a node on that node's route.

## 0.11.0

- Glass bottles moved from Brewing (ring 10) to Copper Tools (ring 2).
- New Constitution path: extra hearts, optional and expensive.
- Spuddie Talkie (ring 3) and Walkie-Talkie (ring 4) split from Radio; both need Copper Tools.
- Items that sat outside the tree are now locked: the mace and trial items (Trial Chambers), totems and
  enchanted golden apples (Undying), the Create chromatics, flywheel, propeller and nozzle, magic cloth,
  hogskin, speed boots, the Dead King's phylactery, and the Supplementaries gold and netherite fittings.
- New motion pass on the ring map.

## 0.24.0

**Pixel-art interface.** The ring screen and the HUD notices are redrawn on the GUI pixel grid so they
scale like the rest of Minecraft's interface instead of smearing between screen pixels.

- **Sprites** (`textures/gui/sprites/pathways/`, nine-slice via `.mcmeta`): window, inset well, panel,
  banner, tabs, buttons, scrollbar thumb, padlock. Every frame has chamfered (diagonal) corners; the
  window and banner carry a stud on each diagonal. Generated by `tools/build_sprites.py`.
- **Repeating textures** (`textures/gui/pathways/`): the map lattice (scrolls with the map), the panel's
  dot pattern and the meander divider, recoloured from a patterned pixel tile pack. One blit each.
- **`Pix`**: pixel-grid drawing into the single GUI quad buffer. Midpoint-circle dashed rings, Bresenham
  links, stepped arcs, scanline seal bands, chamfered bevelled plates. Consecutive pixels merge into one
  quad and everything is culled to the visible map before it is emitted.
- **Node plates** snap to 8, 12, 20 or 24 pixels so item icons draw at whole scales (8 or 16 pixels).
  The map opens on rings 1 and 2, where plates are big enough for icons.
- **Unlock animation**: a cross flash, two stepped diamonds and eight spark pixels, capped at four bursts
  at once and skipped off-screen. Plates the wavefront crosses hop a pixel or two; the old per-node
  `drift` (an allocation and an `exp` per node per frame) is gone.
- **Panel**: sprite tabs that drop their counts on narrow screens, a framed search well, the Grimoire as
  a small book button in the header, wrapped text where long lines used to run off the edge.
- **HUD**: popups and the completion banner use the same frames, with an inset icon well.

Measured with a software render of the real screen code at 1920x1080: about 4,300 quads per frame,
down from 5,100, and the same draw calls apart from item icons.

## 0.23.0

**Waystones in three tiers, and the Monoliths.** The world's Monoliths each hold a waystone, so using one is
now an early skill, while making them stays late.

| Node | Ring | Road | Needs | Gives |
| --- | --- | --- | --- | --- |
| Explorer (new) | 5 | Ranger, after Cartographer | Apothecary as well; task: travel 15,000 blocks | Wake and travel between waystones. No crafting, placing or breaking |
| Attunement | 11 | Magician | | Craft, place and break waystones; warp stones, warp dust, shards, warp, return and bound scrolls |
| Warp Network | 13 (was 12) | Magician | | Sharestones, portstones, warp plates, the portal scroll, the twinbound feather, the epitaph |

The ender chest moved from Warp Network to The End (ring 11), where the ender pearls and eyes are.

New tree field `uses` on a node: block specs its owner may open and use though another node locks them.
Placing, breaking and crafting still wait for the owner. `BlockGuard.onRightClickBlock` checks it on both sides.

Grimoire: A7. Getting About gains The Monoliths and Your Own Waystones pages and the waystone and warp stone
recipes; Chapter V has one line about the Monoliths. Still finishable on vanilla: Level 242, 8,526 shards.

## 0.22.0

**The Nether ladder, reworked around ruined portals.** Portals already stand in the world waiting to be lit, so
the Nether's ground now arrives with the Seal of Gold, and its fire, diamond tools and netherite come later.

| Node | Was | Now | Notes |
| --- | --- | --- | --- |
| Obsidian | 7 | 6 | After Deep Prospector. Place obsidian you find; mining it still needs a diamond pickaxe |
| Nether | 8 | 6 | After Gold, needs Obsidian as well. Basic blocks only: netherrack, nylium, basalt, blackstone, soul sand and soil, magma, quartz, glowstone, nether gold, nether wart, nether brick |
| Radio | 8 | 6 | Now under the Seal of Gold |
| Infernal (new) | | 8 | After Nether; task: kill 40 blazes. Blaze rods and powder, the blaze burner, fire charges, wither skeleton skulls, gilded blackstone, the respawn anchor, ancient debris |
| Diamond Tools | 7 | 8 | Straight after Diamond |
| Diamond Armour | 8 | 8 | Now straight after Diamond rather than after Diamond Tools |
| Brass | 8 | 8 | Needs Infernal as well, for the blaze burner |
| Beacon, Ancient Portal, The End | 10, 10, 11 | same | Now follow Infernal |
| Netherite | 9 | 12 | After Infernal; scrap, ingots, blocks, template, lodestone |
| Netherite Gear | 10 | 13 | |

Ancient debris sits with Infernal so the Netherite task (mine 16 ancient debris) can be done before the node is
bought. Still finishable on vanilla: Pathway Level 241, 8,496 shards.

## 0.21.0

**Envelope, Chalk, Thief and Scholar.**

| Node | Ring | Road | Needs | Holds |
| --- | --- | --- | --- | --- |
| Chalk | 2 | Survivor, after Quarry | | All of Chalk: chalk in every colour and the chalk box |
| Post | 3 | Technician, after Technician | Task: craft 24 paper | Letter and quill, letters, address tags, the mailbox, every pigeonhole, the paper box |
| Parcels | 4 | Technician, after Post | Iron as well | Packages, seal stamps (soulbound too), payback boxes and tags, lost mail, the rest of Envelope |

Create's Packaging (ring 9) now needs Parcels as well, so the pigeons come before the frogports.
Mailboxes and pigeonholes have block entities, so they are guarded machines until Post. Anything the
Automated Supply Service crafts by post is still locked if its node is not owned.

Thief and Scholar add no items, so nothing is locked. The Grimoire gains A9. Post & Pigeons, A10. Chalk and
A11. Villages (Thief's crime and reputation). All four are listed as optional dependencies. Still finishable
on vanilla: Pathway Level 240, 8,438 shards.

## 0.20.0

**Progress popups and completion banners.** While you play, Pathways now shows:

- a small popup at the right edge when a task or bounty moves forward, with its icon, a bar and the count.
  Repeated progress on the same job updates the one popup rather than stacking. Short goals (64 or fewer)
  report every step; long ones such as distance or stone mined report each 5%. Up to three at a time.
- a banner at the top centre when a task or bounty is complete: "TASK COMPLETE" in gold or "BOUNTY COMPLETE"
  in green, the title, the node or tier, and the shards paid. Several in a row queue up.
- a sound on completion.

Each is a per-player switch, all on by default, kept in `config/foreverpathways-client.toml`. They can be
flipped at the bottom of the ring's Tasks tab, or from Mods, Forever Pathways, Config. With the banner off,
a chat line reports the completion instead, so nothing finishes silently. The old action-bar "Task done"
and chat "Bounty done" lines are gone, replaced by the above. Expiry and new-bounty chat lines are unchanged.

Server side, `TaskBook` sends a small `NotifyPayload` only when progress really moves, remembering the last
value shown per player in memory (cleared on logout and on `/pathways reset`).

## 0.19.3

**Fire starters no longer light lanterns.** `canLightLanterns` stays at the mod's own default of false.
The patcher now sets it back to false if 0.19.2 had switched it on. Restart to apply.

## 0.19.2

**Light lasts longer.** Hardcore Torches burns a torch out in 40 minutes and a lantern in two hours,
which is short for an evening on this server.
`StageWriter.patchTorchConfig` nudges four settings (and keeps `canLightLanterns` off, see 0.19.3) on first run:

| Setting | Mod default | Now |
| --- | --- | --- |
| `defaultTorchFuel`, `defaultSoulTorchFuel` | 48,000 ticks (40 min) | 72,000 (1 hour) |
| `defaultLanternFuel`, `defaultSoulLanternFuel` | 144,000 ticks (2 hours) | 288,000 (4 hours) |

It only rewrites a value that is still at the mod's own default, so anything you have tuned yourself
is left alone, and it logs exactly what it changed. A restart applies it, same as the
ProgressiveStages patch.

## 0.19.1

**Dashed requirement lines only show on the node you are reading or hovering.** Drawing every "needs
this as well" line at once turned the map into a cat's cradle. One line, brighter, for the node under
the cursor; nothing otherwise. Preview mirrors it.

**Hardcore Torches is in the tree.**

| Node | Ring | Holds |
| --- | --- | --- |
| Quarry | 1 | Unlit, lit, smouldering and burnt torches, soul torches, the fire starter, the unlit campfire |
| Husbandry | 2 | Animal fat, which fuels the oil can |
| Ironworks | 4 | Lanterns and soul lanterns, the oil can, the bandolier |
| Larder | 4 | The stove |

Torches are the first light anyone makes, so they sit with the stone and coal that pay for them
rather than behind a node of their own. The lanterns need iron nuggets and the oil can needs iron and
string, so those wait for Ironworks.

**Chapter I gains a torch section**, flagged `mod:hardcore_torches`: torches are crafted unlit, burn
down to smouldering and then burnt, are relit from a torch, campfire, flint and steel or fire starter,
and go out in water. With grids for the unlit torch and the fire starter, and a note on lanterns, oil
cans and animal fat.

## 0.19.0

**The Legendary Tabs row is reverted, and here is why it duplicated.** `TabsMenu.initScreenButtons`
adds its buttons to `screen.children()` on every `ScreenEvent.Init.Post` and never removes what is
already there. A vanilla screen clears its children when it re-initialises, so this is normally fine;
Patchouli's book re-runs its init as you navigate without clearing, so a second row appears at the
position the first init computed. Nothing on our side can stop that, so the Grimoire tab no longer
registers Patchouli's screens: it sits in the row on the inventory and Pathways screens, opens the
book, and the book fills the screen on its own.

**The Grimoire is now five chapters and eight appendices.**

| | |
| --- | --- |
| I. Early Game | The flint chain, pebbles, nuggets, with grids |
| II. The Ring | Unlocking, the ring map, seals, locks, death |
| III. Gaining Shards | Tasks, bounties, achievements |
| IV. The Sixteen Eyes | Where each comes from, and that a boss cannot be wasted |
| V. Your Past | The only flavour text left, two paragraphs |
| A1 to A8 | Beds & Sleep, Thirst & Cold, Butchery, Machines, Electricity, Magic, Getting About, Smoke |

The ring-by-ring walkthrough category is gone; the node reference, the seals and the eye list stay.
The book is down to about 10,200 words from 11,400, with the per-mod appendices carrying their own
`mod:` flags as before.

**The unlock banner is gone.** No more panel naming what a node opened: the ripple and the plate
turning gold already say it. `Notice`, `notices`, `NOTICE_NANOS` and `drawNotice` are removed.

**Four new commands**, all at permission level 2:

```
/pathways give <players> <amount>     grant Focal Shards
/pathways take <players> <amount>     remove them, never below zero
/pathways set <players> <amount>      set the exact number
/pathways reset <players>             wipe a player's whole run
```

`reset` revokes every node, clears every task baseline, claim and bounty slot, zeroes shards and
unsets the init flag, so the next tick sets that player up as a new arrival with their starting
shards. There is no undo. The older `/pathways shards <players> <amount>` still works and still takes
a negative number.

## 0.18.2

**Advanced Chimneys is in the tree**, as three nodes on the Builder road, each placed where its
ingredients are:

| Node | Ring | Holds | Needs |
| --- | --- | --- | --- |
| Chimneys | 4 | Stone, cobblestone, small and large brick chimneys | after Brickwork |
| Flues | 5 | Glass and metal chimneys, pipes, ducts, vents | Ironworks as well |
| Draught | 7 | The stone and metal pumps, plus `rest:adchimneys` as the backstop | Mechanisms as well |

Pumps want a hopper and a repeater, which is why Draught waits for Mechanisms on ring 7; pipes and
ducts want iron ingots and nuggets, so Flues waits for Ironworks. The mod adds twelve items in all
and every one is placed.

A new **Smoke** chapter in Not Vanilla explains what the mod does (chimneys vent, pipes carry, vents
release, pumps push and take redstone) and where each part unlocks. The three node entries each carry
a real crafting grid, since the recipe index now reads this jar too.

## 0.18.1

**A new chapter set: Not Vanilla.** Recipes alone do not tell a player what this world does
differently, so there are now eight short entries on exactly that, each carrying the mod flag for
what it describes so a smaller install shows fewer of them:

| Entry | Covers |
| --- | --- |
| The Hard Start | No wooden tools, pebbles, iron nuggets, reinforced sticks, and the flint way out |
| Thirst and Cold | Canteens, juices, bandages and plasters; the three grades of climate gear; seasons |
| Butchery | Carcasses and cuts, the four tools and where they unlock, then abattoir, charcuterie, taxidermy, shop |
| Machines | Create from cogs to brass, and electricity as its own earlier ladder |
| Magic | Late and dearer, three schools of eight, and the enchanting caps |
| Getting About | Waystones, horses and gear, airships, trains, and why your legs are slow |
| Sleep | Home beds, hammocks, the night bag, infested mansion beds |
| The End | Sixteen different eyes, and that bosses cannot be wasted |

**Node recipes cut back from four to one.** 421 grids was a catalogue, not a guide: each node now
shows one grid, for its own item, where that item has a recipe. 143 grids in all, with the full
early-game chain still laid out step by step in Getting Started.

## 0.18.0

**A boss cannot be fought before its kill would count.** Killing a Bosses' Rise boss early used to
waste the encounter: the eye dropped into a node you did not have, and the next boss was a long way
off. `BossGuard` now cancels incoming damage from a player to any of the six bosses until the node
whose task names that entity is reachable, which is the same moment its kills begin to count.

- Only damage from players is refused; the boss fights back and other mobs hurt it normally.
- Creative is exempt, and a player who has the node fights normally even beside one who does not.
- The refusal says which node it belongs to, about once every five seconds rather than every swing.
- Nothing is hardcoded: the entity-to-node map is built from `bossDrops` and the tasks in `tree.json`.

**The Grimoire shows recipes instead of describing them.** 421 crafting grids now, across 136 of the
227 node entries, each one a real recipe rather than prose: what a node unlocks, drawn as the grid you
will use.

The ids come from the mods themselves. `tools/index_recipes.py` reads every extracted mod jar's
`data/<mod>/recipe/` folder and writes `tools/recipe_index.json`, 1,624 items deep, so no id is
guessed. Vanilla ships inside the game jar and cannot be read that way, so its recipes come from a
conservative list in that script of items whose recipe id is simply the item id. Run it again when a
mod is added:

```
python3 tools/index_recipes.py <folder of extracted mod jars>
python3 tools/build_book.py
```

Each grid carries its own `mod:` flag, so a page for a mod you do not have hides rather than erroring.
The whole book is now about 11,300 words across 276 entries, most of it tables, grids and the ring
map rather than prose.

## 0.17.1

**A lock guarded far too much.** Looking at a locked block stopped you placing anything at all, and
breaking stone bare-handed fired the locked message and chime even though bare hands never drop stone.
That is fixed by narrowing what a lock protects.

| Thing | Before | Now |
| --- | --- | --- |
| Locked item in hand | cannot be used, placed, eaten, swung, worn | unchanged |
| Ordinary locked block | could not be broken or placed | break and place freely |
| Locked machine | could not be used, broken or placed | unchanged |
| Placing while looking at a locked block | refused, with a message | allowed |
| Breaking with an empty hand | message and chime | silent |

A block counts as a machine if it keeps a block entity, which catches furnaces, chests, brewing
stands, beacons, every Create machine and every modded one without a list to maintain. The handful of
vanilla workstations that keep no data (crafting table, loom, grindstone, stonecutter, anvil,
composter, cauldron and so on) are named in `guardedBlocks` in `tree.json`, alongside a few Create and
PowerGrid shapes.

Right-clicking is split properly now: a locked machine refuses only `setUseBlock`, so the item in your
hand still works and you can build against it. Breaking checks the held tool and the block separately,
and an empty hand on a plain block says nothing at all.

**ProgressiveStages' own switches are turned down with it.** They are global, so they were the other
half of the problem: `block_block_placement`, `block_item_pickup`, `block_item_hotbar`,
`block_item_mouse_pickup`, `block_item_inventory` and `block_interactions` are now switched off
alongside `block_block_interaction`, and Pathways does that work itself, more narrowly. Crafting stays
with ProgressiveStages, which is the part it does well. This also means locked items stop being
emptied out of your inventory, which is what the guide has always said would happen.

## 0.17.0

**Bounty progress was broken, and it was a data bug.** A slot remembered its bounty by position in
`tree.bounties`. 0.16.0 inserted thirteen new Tier I bounties into the middle of that list, so every
slot saved before the update pointed at a different bounty than the one it displayed: you killed the
pig and a different counter moved.

Slots now record *which* bounty they hold, by a stamp taken from the bounty's own id, and check it on
every read. If the list has moved, the slot finds its bounty again and repairs its position; if the
bounty is gone entirely, the slot is cleared rather than left pointing at a stranger. Slots dealt
before this update are stamped on first read, so nothing is lost.

`harness/Harness5.java` is a new test covering the whole path: deal "Hunt 4 pigs", award the kill,
check the statistic moves, check the screen reads 1 of 4, check four finishes it. Tasks and bounties
also update every second now instead of every five, because five seconds of nothing looks broken.

**The guide now teaches It's Harder Early properly.** Getting Started opens with the real chain, and
with the mod's own recipes rather than prose:

| Step | Recipe |
| --- | --- |
| 1 | Flint knife: flint and a stick |
| 2 | Cut grass, tall grass or ferns with the knife for grass string |
| 3 | Plant string: three grass string |
| 4 | Reinforced stick: a stick and two mud balls |
| 5 | Flint axe: flint, reinforced stick, plant string. Now wood |
| 6 | Cobblestone: four pebbles; stone tools take reinforced sticks |
| 7 | Raw iron: nine raw iron nuggets, and ore drops four to seven |

**Tasks are tasks and bounties are bounties.** The in-world renaming is gone from every page, generated
ones included: no labours, no errands, no bands, no pieces of the ring. The narrative is one short
chapter at the end, and Getting Started is about half the words it was.

**The Grimoire is properly part of Legendary Tabs.** Having its jar, the tab now uses what it
actually supports:

- `right_click_item` on `patchouli:guide_book`, so the tab opens the book the same way the item does,
  with no screen of ours in between. `GrimoireScreen` is deleted.
- `enabled_conditions` with `item_in_inventory`, so the tab appears only while the book is carried.
  This is what you asked for and what I could not confirm without the jar.
- `screen_sizes` for Patchouli's four book screens at 272x180, so the tab row stays visible inside
  the book instead of disappearing or doubling.

## 0.16.2

**Dreaming moved from ring 8 to ring 9 and now needs Brewing.** The bedbug eggs it holds are a
weapon: a bed they infest is cleaned only by a splash potion of Harming, which `ModEvents` checks for
by `MobEffects.HARM` on a thrown potion. At ring 8 the eggs would have arrived a whole band before
anyone could undo them, which on a shared server is a griefing window. They now arrive with the cure.

Natural infestation is separate and stays early: Sleep Tight only seeds beds in woodland mansions, by
its own `MANSION_INFESTATION_CHANCE`. That needs no cure, because breaking the bed and making another
works at any point; the potion only matters for saving a bed you have slept in enough to make a home
bed.

**New chapter: Beds and What Lives in Them.** What a home bed is, that the old manors are infested,
and the two ways to clean one, with the honest note that the potion waits for the ninth band.

## 0.16.1

**New stat curves.**

| Stat | Range | Steps |
| --- | --- | --- |
| Movement | 85% to 110% | 10 of 2.5%, from the second band to the fourteenth |
| Breaking | 25% to 100% | 15 of 5%, from the second band to the thirteenth |

Movement: Scout, Outfitter, Long Stride, **Sure Foot** (new, 5), Pathfinder, Fleet Foot, **Roadwise**
(new, 9), Windrunner, Wayfarer, Stormstride.

Breaking: Copper Tools 10%, Iron Tools 15%, Diamond Tools 15%, Netherite Gear 10%, with 5% each from
Calloused Hands, Strong Back, Tireless, **Rockbiter** (new, 11) and Stonebreaker. Two thirds of it is
in the tools, so the pick still matters more than the perk. Starting at 25% makes the first pick feel
like a real change rather than a small one.

**Sleep Tight is in the tree**, as three placements rather than one dump:

| Node | Ring | Holds |
| --- | --- | --- |
| Furnishings | 3 | All 16 hammocks, beside the couches. String and carpet, so it lands where its ingredients do, and it is craft-locked only: anyone may hang one and sleep in it |
| Canvas Works | 4 | The moon banner pattern |
| Dreaming, new | 8 | The night bag, dreamer's essence, bedbug eggs, and `rest:sleep_tight` as the backstop. Sleep 20 nights, and needs the Nether |

The night bag wants an echo shard and dreamer's essence wants blaze powder, which is why Dreaming
waits for the Nether. The bedbug spawn egg stays unlocked under the existing spawn-egg rule. Sleep
Tight is declared as another optional dependency.

Tree total is 8,342 shards.

## 0.16.0

**The Grimoire tab no longer draws over the book.** The tab declared Patchouli's own screens in
`target_screen_class` and `screen_sizes`, so Legendary Tabs painted its row on top of the open book
and doubled it up. Both are gone and `show_tabs_on_screen` is false: the tab opens the book, the book
fills the screen.

**The guide now opens with what to do, not why you are weak.** The Waking is reordered and cut:

| Entry | What it is |
| --- | --- |
| Your First Ten Minutes | Five numbered steps, which road to take first, then crafting pages for the table, wooden and stone picks, furnace, torch, chest, and the Grimoire itself |
| Taking a Piece Back | What a piece costs, the three sources of shards, when a labour starts counting, how to read the panel, what dying costs |
| Why You Are Here | The waking, in three paragraphs instead of six |
| Things Worth Making | Nine more recipes: stone tools, bed, campfire, rod, shield, bucket, shears |

Rings and Seals opens on a drawn map of the whole ring: every piece a dot coloured by its road, the
barred bands shaded red, a padlock on each seal. It is generated from `tree.json` by the snippet in
`tools/` alongside the book, so it cannot go stale.

**The whole book is set in bold**, which reads far better at book size. A plain `$()` reset would
drop the weight along with the colour, so every reset is rewritten to a `$(n)` macro that puts it
straight back.

**Stats rebalanced.**

| Stat | Was | Now |
| --- | --- | --- |
| Movement | 70% to 120% | 80% to 100%, full at the fourteenth band |
| Breaking | 40% to 100% | 50% to 100%, full at the thirteenth band |
| Horse | none | 80% to 100%, full at the fifth band |
| Attack, boat, health | unchanged | unchanged |

Horse speed is a new player attribute, `foreverpathways:horse_speed`, scaled onto a ridden horse by
`HorseSpeedMixin` on `getRiddenSpeed`. A loose horse, or one carrying someone else, is untouched.
Rider and Cartwright give it back in two steps.

Three new nodes carry the last of the speed out to the wall: **Wayfarer** (12) and **Stormstride**
(14) for movement, **Stonebreaker** (13) for breaking. The tool nodes still carry most of the digging
speed: 8% copper, 10% iron, 10% diamond, 8% netherite.

**The early game pays better.**

- The Seal of Copper asks for 200 stone instead of 600.
- Every Tier I kill bounty is halved: 4 spiders, 3 skeletons, 4 zombies, 2 creepers.
- Thirteen new Tier I bounties: hunt cows, pigs, chickens or sheep, swim 120 or 400, sleep 2 nights,
  jump 150, crouch 400, sprint 1,200, catch 3 fish, craft 32 torches, climb 120.

The pool is 124 bounties, 117 of them available on vanilla.

## 0.15.0

**Publishable on its own.** The whole tree can now be finished on vanilla plus ProgressiveStages,
and every other mod adds to it rather than being needed by it.

`Availability.java` runs once at load and checks every labour against the registries the game
actually has. A labour naming nothing present is dropped, and its node then costs only Focal Shards;
bounties naming nothing present leave the pool. Rings, costs, seals and locks are untouched, and a
node whose items are missing simply unlocks nothing. If the registries are not filled yet, nothing is
pruned, so an early load can never strip the tree.

On vanilla that drops 9 labours of 111 and 7 bounties of 111: the Seal of Current's copper cord,
Brass's zinc ore, the Grove's tree seeds, and the six Bosses' Rise kills. Everything else stands.

`tools/vanilla_check.py` proves it, by the same rule the mod uses:

```
python3 tools/vanilla_check.py                 # vanilla only
python3 tools/vanilla_check.py all             # the full pack
python3 tools/vanilla_check.py x create        # vanilla plus Create
```

Both report the tree completable at Pathway Level 227 for 7,979 shards, every eye included. The 9
school nodes left over are the deliberate slot limits, three of eight.

**One real blocker found and fixed.** The Undead Eye hung off Blood Robes and the Witch Eye off
Nature Robes, but only three of the eight schools can ever be owned, so most players could never fill
the End portal. They now hang off Trial Chambers and Alchemy, neither of which is exclusive. The
audit fails loudly if an eye ever becomes unreachable again.

**The mod now lists its dependencies.** 40 pack mods are declared `optional` in
`neoforge.mods.toml`, so they appear in the mod list and Pathways loads after them and sees their
items. Only NeoForge, Minecraft and ProgressiveStages are `required`, which is why the game loaded
without the rest before: they were never declared at all. Making them required would defeat the point.

**The Grimoire adapts to the install.** 153 of its 268 entries now carry a Patchouli `mod:` flag
taken from the item they show, so a node's entry appears only when the mod that fills it is there. The
seven chapters of The Waking, the eight of The Long Road, every seal and every category icon are
vanilla, so the spine of the guide is always present. Namespace and mod id differ for Icy's Better
Horses, which is mapped in `MODIDS` in `tools/build_book.py`; add any other divergent one there.

## 0.14.2

**Icy's Better Horses is in the tree.** Four nodes on the Ranger road, each with a horseback task,
and every craftable item placed where its ingredients actually exist.

| Node | Ring | Holds | Needs |
| --- | --- | --- | --- |
| Rider | 4 | Hoof shoes, beside the saddle and leather barding | Ride 4,000 blocks |
| Cartwright | 5 | The wheel and the horse cart | Ride 12,000 blocks |
| Tack | 6 | The upgraded saddle, the canister, the stabiliser | Gold as well |
| Veterinary | 9 | The medkit, and `rest:icys-better-horses` as the backstop | Brewing as well, ride 40,000 blocks |

The upgraded saddle wants a diamond, the canister wants gold and the medkit wants a regeneration
potion, which is why Tack sits on ring 6 and Veterinary waits for Brewing on ring 9. The nineteen
breed spawn eggs stay unlocked, caught by the tree's existing `re:.*_spawn_egg` rule, and the Stable
Handbook is on the `ignore` list beside the Grimoire, along with everything from Modonomicon that
holds it.

The guide picked all of this up on its own, since it is generated from the tree: 268 entries now.

Tree total is 8,555 shards.

## 0.14.1

**The Forever Grimoire.** A Patchouli guide to the whole tree, built from `tree.json` so it cannot
drift from the mod. 12 categories, 265 entries.

The book is written in character, as something left beside a player who has woken with no memory,
by whoever left it. There is no mention of the mod, the rings' internals or any design reasoning
anywhere in it: nodes are "pieces of the ring", rings are "bands", tasks are "labours", bounties are
"errands", and unlocking is "reclaiming" what was taken.

| Category | What is in it |
| --- | --- |
| The Waking | Waking with nothing, Focal Shards, what dying costs, why things slip out of your hands, your own body, binding power to things, and the order of the three crafts |
| The Long Road | Eight chapters, one per pair of bands, from Stone and Copper to the Eyes, each listing the pieces on those bands and what they cost |
| Rings and Seals | How the ring turns, then an entry per seal with what it bars, what it demands and what waits behind it |
| The Roads | A parent category with one child per road, and an entry for every piece: its band, cost, what must come first, what it asks, what it gives back |
| The Sixteen Eyes | The gate's sixteen hollows, how carrying an unreclaimed eye works, and an entry per bearer with a live rendered model of it |

Only the prose chapters are hand-written. Everything else is generated by `tools/build_book.py`, so
regenerate the book whenever the tree changes:

```
python3 tools/build_book.py
```

**Patchouli is an optional dependency, client side.** Without it the mod loads exactly as before,
and the guide tab and button are hidden rather than broken. `Grimoire` does the `ModList` check,
`GrimoireHook` is the one class that names a Patchouli type, and it is only ever reached once the
check has passed.

**Everyone is handed one on their first join.** `Grimoires.onFirstJoin` gives the book once, marked
in the player's persistent data, and `PlayerEvent.Clone` carries that mark through death so nobody
gets a second copy. It can also be crafted at any time from paper and an amethyst shard,
whatever other mods are installed. Patchouli items are on the tree's `ignore` list, so the guide is
never locked by the tree it describes.

**The guide only opens while the book is in your inventory.** `Grimoires.carried` checks the main
inventory and the off hand, and both the Legendary Tabs tab and the panel link go through it: lose
the book and the guide closes to you until you craft another.

Ways in: right-clicking the book (Patchouli's own behaviour); the **Legendary Tabs** tab at
`data/legendarytabs/tabs/grimoire.json`, which lists Patchouli in `required_mods` so it hides itself
without it, and declares sizes for Patchouli's four book screens (272x180) so the tab row is drawn
over the book itself; and a **Grimoire** link at the right of the Pathways panel's tab row.

Patchouli 1.21.1 wants `book.json` under `data/` and all content under `assets/`, and it throws if
`use_resource_pack` is false, so the book is laid out that way.

## 0.13.5

**Decoration is craft-locked, not use-locked.** Seating, Fittings, Furnishings, Trimmings, Lighting,
the Atelier and Bric-a-brac are marked `craftOnly` in `tree.json`. Anyone may carry, place, use and
break what those nodes hold, whatever they own; the node buys the recipe and nothing else. So a chair
someone else crafted is a chair you can sit on, and a locked sconce still lights a room.

How it works: a craft-only node writes no `rules.toml`, so ProgressiveStages never sees its items.
Its own switches for pickup, placement and the hotbar are global, and listing a chair there would stop
anyone sitting on one. `BlockGuard` ignores craft-only items entirely, and `CraftGuard` plus
`ResultSlotMixin` refuse the crafting grid's result slot instead. The recipe book hides the recipe as
well, and the tooltip reads "Cannot craft yet" with a line saying it is free to place.

Functional Supplementaries stays locked outright: the cannon, bombs, the quiver and the Gadgets node
are traps, weapons and machinery rather than decoration.

**Supplementaries is spread across rings 2 to 6, each item with its own trade.**

| Node | Ring | What moved in |
| --- | --- | --- |
| Fittings | 2 | Soap, ash, flint and feather blocks, the crank, statue, hat stand |
| Trimmings | 3 | Presents and trapped presents, flute, confetti popper, lunch basket, urn, music disc, way sign variants |
| Brickwork | 3 | The bellows, beside the kiln |
| Gearing | 3 | The wrench, beside Create's cogs |
| Boatwright | 3 | The jar boat |
| Animal Feed | 3 | Fodder |
| Ironworks | 4 | Cage, goblet, faucet, pulley, bubble blower, iron gate |
| Lighting | 4 | Fire pit, lumisene bottle and bucket |
| Cartographer | 4 | Antique ink, beside the globe and the quill |
| Bakery | 4 | Pancake, candy, sugar cube |
| Gadgets (new) | 5 | Relayer, clock and cog blocks, turntable, safe and key, lock block, wind vane, slidy block, crystal display, spring launcher, dispenser minecart, speaker block, illuminator, bamboo spikes. Needs Redstone |
| Taxidermy | 6 | The enderman and spider heads |

Bric-a-brac keeps `rest:supplementaries` as the backstop, so anything a Supplementaries update adds
still lands somewhere. Four items are left on it today: barnacles, the blackboard, the blast armour
trim and the dragon banner pattern.

Tree total is 8,477 shards.

## 0.13.4

- The Magician no longer grows from the Core. It branches off the Apothecary (ring 4) and opens on
  ring 6, so it sits beside the survival and herbal nodes it belongs with instead of running as a long
  spoke from the middle.
- Early electricity comes before any magic. Wiring is now ring 3 (from the Technician, with Copper
  Tools) and the Lighting Rig is ring 4, both well ahead of the Magician. Wiring no longer
  needs Redstone, because cord, wire and the potato battery do not use it.
- Switches (the LV and MV switches) leave the Lighting Rig and wait for Power Grid, since they are
  built from levers and levers stay locked behind Redstone on ring 5.
- The whole magic ladder moved out a ring. The Magician opens on ring 6, Study and Alchemy on ring 7,
  Apprentice Robes, Elixirs and Arcane Essence on ring 9, the eight schools and the Wizard Robes on
  ring 10, every school's robes on ring 11, every school's relics on ring 12. Eldritch (13) and the
  Grand Magus (14) are against the outer wall and stay. The Infirmary (ring 6) now comes a ring before
  Alchemy instead of level with it.
- The Technician moved from ring 1 to ring 2 and now needs the Builder, so technology starts from
  building rather than from the Core. Nothing on the path below it moved: Gearing, Wiring and the
  Spuddie Talkie are still ring 3. The path keeps its old prices, because a path's cost curve is now
  scaled from a pinned baseline instead of from whichever ring its root happens to sit on.
- Tree total is 8,453 shards.
- Holding a node to unlock it takes 1.9 seconds, down from 2.9.
- The unlock ripple is much bigger: it reaches 170 map units instead of 95, pushes two and a half
  times as hard, runs for 1.1 seconds instead of 0.6, and the push is concentrated in a travelling
  wavefront so plates are shoved as it passes them rather than all at once. The rings and sparks
  expand further and linger to 1.7 seconds.

## 0.13.3

- Movement and breaking speed are earned later and in smaller steps. Three quarters of the breaking
  speed now comes from the tool nodes themselves (Copper, Iron, Diamond, Netherite), so a better pick
  is what makes you quick, not a perk. The chains that used to pay +10% at rings 2 and 5 now pay +5%
  at rings 4, 7 and 9.
- Attack damage follows the same shape: small early, +15% at Warrior and Champion.
- Maximum health moves in half hearts. Each Constitution node gives +5%, which is 1 HP. The path is now
  twenty nodes spread from ring 2 to ring 14, so finishing it takes you from 10 hearts to 20.

## 0.13.1

Magic now spans the Seal of Gold to the Seal of the Arcane: the magician's first node sits on ring 5,
where the Seal of Gold opens, and the Grand Magus sits on ring 14, inside the band the Seal of the
Arcane locks. The magician's eight steps are spread evenly across that span. The ladder is 16 rings,
the eyes sit on ring 15, and the Seal of Mastery is gone. Enchanting needs Study again and Brewing
needs Alchemy, which works now that both sit inside the map.

## 0.13.0

The ladder was 19 rings, and the order of the three technologies is fixed.

| Ring | Tier | What lands there |
| --- | --- | --- |
| 5 | Gilded Age | Redstone: the first mechanical logic |
| 3 | Iron Age | Wiring: cord, wire, pins, the potato battery (moved in 0.13.4) |
| 4 | Hearth | Lighting Rig: bulbs, fixtures, meters, batteries (moved in 0.13.4) |
| 10 | Industry | Power Grid, and the first magic node |
| 11 | First Light | Study and Alchemy |
| 12 | Flight | Aeronautics, Offroad, and the magician's robes |
| 13 | The Arcane | Runecraft, the jeweller, the scribe |
| 14 | High Voltage | High Voltage, level with the schools and regalia |
| 15 | Relics | The relics and the archmage |
| 16 | Archmagery | The eldritch school |
| 17 | The Grand Design | The Grand Magus |
| 18 | Eye Ring | The sixteen eyes |

Redstone comes before cabling, cabling before generators, generators before high voltage. No magic
exists until the Power Grid is lit. Three new seals carry the longer ladder: Current (ring 11),
the Arcane (13) and Mastery (15).

Magic no longer pins its first node to 8 shards. Starting at ring 10 it pays the going rate for its
ring plus the magic markup, so the magician's first node is 26 and the Grand Magus is 98.

## 0.12.0

- The Nether moves in from ring 9 to ring 8, and netherite sits beyond it at ring 9. The tier names
  move with it: ring 6 is the Diamond Age, 7 the Deep, 8 the Nether, 9 the Ashen Age, 10 Industry.
- Blaze rods and the blaze burner come with the Nether, so Create's brass line is actually reachable.
- Electricity is staged: Wiring (ring 5), Lighting Rig (ring 6), Power Grid (ring 9), High Voltage (ring 10).
- Enchanting is tiered: Enchanting, Library, Grand Library.
- Every item in the pack now belongs to a node. Whatever a mod has left over lands on a sensible node
  through `rest:<namespace>`.
- Ordering fixes from the recipe audit: the compass and clock move to Redstone, the tripwire hook to Iron
  (which unblocks the crossbow), knives follow their metal, rose quartz follows nether quartz, and the
  iron and netherite spellbooks follow iron and netherite.
- Every item tooltip now says which node unlocks it, and whether a seal still covers it.
- Idle drift is gone from the ring map; plates only move when a ripple passes through them.
- A node now takes 2.9 seconds to hold.
