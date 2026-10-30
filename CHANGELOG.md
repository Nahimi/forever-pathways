# Changelog

## 0.37.1

- Mining and gathering bounties ask for half as much too: Mine 64 stone, Mine 24 iron ore, Clear 32 leaves. Their
  rewards are unchanged. A bounty already running takes its new, smaller goal at once.

## 0.37.0

- Every seventh day survived in one life pays 2 Focal Shards on top of that day's shard ("rules.weekDays" and
  "rules.weekShards").
- The Create nodes sit closer in: Waterworks ring 3 (straight after Technician), Andesite Casing 4, Milling, Pressing
  and Fans 5, Belts, Crushing, Fluids and Kinetic Tools 6, Logistics and Contraptions 7, Moving Machines 8, Trains and
  Simulated 9, Chromatics, Offroad and Aeronautics 10 (from 12). Brass stays at 8, since it needs Infernal. Costs follow
  their new rings.
- Every mining, smelting, harvesting and tree-felling task on a node or seal asks for half as much: Mine 100 stone,
  Smelt 24 copper ingots, Mine 48 copper ore.
- Deliveries ask for 32 at most. Common things ask for 16 (1 shard) or 32 (2 shards).

## 0.36.2

- Deliveries of wooden things take any wood, in any mixture: 64 Wooden Trapdoors counts oak, jungle, dark oak and every
  other wood alike. The same goes for planks, logs, doors, slabs, stairs, fences, fence gates, buttons, pressure plates,
  signs, hanging signs and boats. The groups are "delivery.anyOf" in tree.json, by item tag.
- Wood blocks (the six-sided bark blocks, stripped or not, and nether hyphae) are never asked for.
- Bounty and task text too long for a small screen now slides along so all of it can be read, on the Tasks tab and in
  the popups.
- Holding right-click to give up a bounty now traces a frame round the row with motes closing in, and the row trembles
  as it fills, like holding a node to unlock it.

## 0.36.1

- Supports Companions!: Saints and Shades (ring 9, after Champion; kill the Sacred Pontiff or a Shade) for crystallised
  blood arms and armour, relic gold, old cloth and the holy robes. The companions, puppets and Shade altars are at
  Husbandry, the tesla machines at Redstone, the mage gear and spell books at Magician, the croissant dragon's
  things at Bakery, the netherite dagger and eternal fire at Netherite, and the respawn totem at Undying.
- Knight Lib, which Companions! needs, is placed at Husbandry.

## 0.36.0

- The sixteen eyes come only from their nodes: unlocking an eye node gives you its eye. They are no longer in any
  loot table, mob drop or trade, and their recipes are refused. Bosses drop what they always did.
- Each eye node's task is now a boss kill, the first of its bosses this pack has; without any, it keeps its old task.
  Rogue, Cursed, Cold, Old, Guardian and Nether: the Bosses' Rise bosses (Guardian falls back to an elder guardian).
  Wither: the Wither. Corrupted: the Ender Guardian. Undead: Maledictus. Lost: the Ancient Remnant. Exotic: the
  Leviathan. Black: the Netherite Monstrosity. Cryptic: Ignis. Magical: Scylla. Witch: the Harbinger. Evil: Azazel.
- Lost an eye? Kill its boss again while you own its node and carry none of that eye, and you get another.
- Anyone who already owns an eye node is given its eye once.
- A boss that an eye node asks for takes no damage from you until a node asking for it is reachable (not the Wither
  or the elder guardian, which anyone can find or summon again).
- Supports Cult of Azazel: The Divine Chariot (ring 10, after Champion) for Azazel's arms, armour and trophies, its
  samsonit stone and dungeon blocks at Nether, crimson honey at Apiary.
- tree.json: "bossDrops" is gone; nodes take "bosses" (kill tasks, best first) and "gives" (an item handed over
  on unlock).

## 0.35.1

- Supports Shield Expansion: the wooden shield at Woodcutter, iron at Iron Tools, golden at Golden Gear, diamond at
  Diamond Armour and netherite at Netherite Gear.

## 0.35.0

- Supports Quark, No Man's Land, The Beyond, Design n Decor, Create: Dreams n Desires, Create Stuff Additions, Create
  Ratatouille, Veggies Delight, Redomesticate, Nirvana and Create Auto-Navigation, all on existing nodes:
  - Quark and No Man's Land: woods at Woodcutter, stonework at Builder (and Polished Stone, Brickwork, Deepslate Work,
    Copperwork, Seastone, Fortress Stone, Crimson and Warped and The End by material), glass at Glassworks, lamps at
    Lighting, crates at Gardener, bookshelves at Enchanting, boats at Boatwright, dishes at Larder and gadgets at
    Mechanisms, Redstone, Ironworks and Cartographer. Their natural stones, plants and soils are free, like Biomes
    O' Plenty's.
  - The Beyond: all of it at The End.
  - Design n Decor: Stone Palettes, with its working cogwheels, millstones, crushing wheels, fans, flywheels and
    belts at the Create node for each.
  - Dreams n Desires and Create Stuff Additions: each machine, tool and armour set at its Create or metal node.
  - Ratatouille: its machines at Pressing and Mixing, the rest at Bakery. Veggies Delight: crops at Gardener, dishes
    at Larder. Redomesticate at Husbandry. Nirvana: hemp at Gardener, burlap at Canvas Works, the rest at Herbalist.
- Quark's furnaces and Redomesticate's pet beds lock only their recipe, like other everyday things.
- Biolith and Create Central Kitchen add no items and need nothing.

## 0.34.0

- Dying no longer freezes tasks and bounties, on any difficulty. It still costs 1 Focal Shard, and on Normal and Hard a
  quarter of running progress.
- Players now hold 3 bounties. The third is always a delivery and the other two never are. A delivered or expired
  delivery is replaced at dawn; one given up is replaced at once. The new slot is dealt as soon as a player logs in.
- Deliveries no longer ask for grass blocks, which need Silk Touch; Tier I can ask for dirt instead.
- tree.json: "bountySlots" is 3, "delivery.chance" is replaced by "delivery.slot" (false turns deliveries off),
  "delivery.raw" lists items no recipe makes that may still be asked for, and "rules.freezeTicks" is gone.

## 0.33.1

- A tree.json from a datapack or KubeJS now applies in single player as soon as the world opens. Opening a world
  used to put the built-in tree back right after the datapacks loaded, so the change only showed after /reload.

## 0.33.0

- L_Ender's Cataclysm now has a node for each of its eight bosses, each asking for its kill, on two roads from
  Warrior: the Ender Guardian (ring 9), the Netherite Monstrosity (10), Ignis (11) and the Harbinger (12); and the
  Ancient Remnant (9), the Leviathan (10), Maledictus (11) and Scylla (12). Each holds its own boss's drops and what
  is made from them; lesser bosses (the Kobolediator, the draugr, the Clawdian) share their master's node. Players
  who owned Ignis, the Leviathan or the Harbinger before need the new nodes for the Monstrosity, Scylla, the Ender
  Guardian and Maledictus.
- Born in Chaos: Born in Chaos (ring 5, after Hunter) for its creatures' drops and blocks, and Lords of Chaos (ring 8)
  for its bosses' trophies.
- Immersive Aircraft: Aviator (ring 6, after Boatwright) for the airship, gyrodyne, biplane and quadrocopter, and
  Air Fleet (ring 9) for the cargo airship, the warship and the upgrades.
- Map Atlases are unlocked at Cartographer; Quality Equipment's reforging station at Enchanting and its emerald
  ring and amulet at Trader.

## 0.32.1

- A node's Unlocks list marks recipe-only items (decoration and everyday things anyone may already use) with a gold
  frame and corner tab; hovering says the node only lets you craft them.

## 0.32.0

- Everyday things lock only their recipe: anyone can place, use, pick up and break beds, doors, trapdoors, gates,
  buttons, pressure plates, levers, bells, ladders, signs, stoves, the furnace and smoker, campfires, the crafting
  table, chests and barrels, torches and lanterns, flower pots, item frames, paintings, armour stands, composters
  and cauldrons, from every mod. The node that unlocks them now only buys the recipe. The list is
  `craftOnlyItems` in tree.json. Restart the server once after updating so ProgressiveStages reads the new locks.

## 0.31.0

- New bounty type, Delivery: hand in a number of one item you can craft (32 Iron Ingots, 2 Bookshelves). About one
  bounty in four is a delivery, at most one a day. Carry all of it and press Deliver on the bounty; only plain items
  count. Once delivered, its slot waits for dawn.
- What a delivery asks for follows what the item costs to make, worked out from every recipe in the pack, with rare
  materials (diamonds, ender pearls, ancient debris and others) worth more. Higher tiers ask for rarer things and pay
  1 to 5 shards. Values, budgets and items never asked for are set in tree.json under "delivery".
- A finished bounty is now replaced after 3 minutes instead of 2.
- Giving up a bounty now takes holding right-click for a second, and a new one is dealt straight away.

## 0.30.0

- A finished bounty is replaced 2 minutes later, or at dawn if that is sooner. One dealt late in the day gets an
  extra day before it expires.
- Right-click a running bounty on the Tasks tab to give it up, once per in-game day; its slot refills the same way.
- Every full day lived without dying pays 1 Focal Shard.
- Bounties lean toward the highest tiers you have reached, so they grow harder with the tree. Rewards are capped
  by tier at 2, 2, 3, 4, 4 and 5 shards; only the rare tier VI bounties pay 5.

## 0.29.4

- Fixed: the game crashed when opening the recipe book with recipes hidden by locks.
- Fixed: the Grimoire showed nothing.

## 0.29.1

- Dynamic Trees addons such as Dynamic Trees BWG: their branches and roots unlock with Woodcutter, seeds with
  Forager and mega seeds with Grove, and they count for the chop and plant bounties.

## 0.29.0

- Biomes O' Plenty and Oh The Biomes We've Gone: their woods unlock with Woodcutter, boats with Boatwright,
  bookshelves with Enchanting, sandstone with Brickwork and stone blocks with Builder; plants, leaves and
  natural blocks are free from the start.
- Tom's Simple Storage: new Storage Network (ring 6) and Crafting Terminals (ring 8) nodes on the Survivor road.

## 0.28.1

- `tree.json` is now loaded as datapack data, so a datapack or KubeJS can replace it
  (`data/foreverpathways/tree.json`) and `/reload` picks up a change. The server sends the tree it uses to
  every player. `/pathways debug` shows where the tree came from. (Issue #1)
- Removed an unused scrollbar sprite whose metadata logged an error on every texture reload. (Issue #3)

## 0.28.0

- Support for six more mods, 14 new nodes:
  - Hearth and Harvest: Orchard and Trellis (ring 3), Hearth (ring 4) and Cellar (ring 5), Homesteader road.
  - Vaskar's Remains: Relics of the Fallen (ring 4, Ranger). Bodies and graves can always be searched.
  - Vaskar's Bestiary: Beast Lore (ring 4, kill 3 trolls) and Titans (ring 8, kill one titan), Ranger.
  - Legionary: The Unbreakable (ring 6, Ranger, kill it).
  - L_Ender's Cataclysm: Ancient Remnant, The Leviathan, Ignis and The Harbinger (rings 9 to 12, Ranger),
    each a boss kill.
  - Hazen N Stuff: Hazen's Metals (ring 9), Runes and Orbs (ring 10) and Flamebearer's Arsenal (ring 11),
    Magician.
- Iron's Simple Blood is listed as supported; it adds no items, so no nodes.
- Grimoire: a page for every new node and a new appendix, A13. Foes and Feasts.

## 0.27.2

- New bounties are announced one short line each: "New Bounty Available: Hunt 4 pigs".

## 0.27.1

- Fixed: every new world used the difficulty of the first world you made. NeoForge keeps server
  configs in the shared config folder, so the first pick was written there for all worlds. Each world
  now keeps its own preset in its save, picked from its difficulty when it is first started.
- `force_preset` in `config/foreverpathways-server.toml` pins one preset for every world (leave it on
  `AUTO` to let each world choose). The old `preset` setting is no longer read.
- New `/pathways difficulty [easy|normal|hard]` (operators) shows or changes this world's preset.

## 0.27.0

- Nodes that give nothing in your install are gone: one written for a mod's items (or for using them) is
  removed when none of them exist, even if it also carried a perk or a slot. On vanilla that clears the
  magic schools, Archmage, Spellblades, Outfitter, Cartwright, Helvar and Explorer. Stat nodes (the
  Constitution road and the like) and the enchanting Libraries stay: they still give something.
- A node's Unlocks list now also shows items it only lets you use, marked with a blue corner tab;
  hovering says where their crafting is unlocked.
- Unlocking a node or breaking a seal is announced in chat to every player.
- The node panel no longer repeats how many shards you have under the cost.

## 0.26.0

- Difficulty presets, Easy, Normal and Hard, kept per world in `serverconfig/foreverpathways-server.toml`
  and shared by every player on a server. They set starting shards (5 / 3 / 2), whether death costs a quarter
  of task progress (no / yes / yes), the death freeze (3 / 5 / 10 minutes) and node costs (25% off / 5% off /
  full). A new world picks its preset from the world difficulty the first time it starts: Peaceful and Easy
  pick Easy, Hardcore picks Hard.
- `/pathways debug` shows the difficulty.

## 0.25.3

- Hardcore Torches: torches are now crafted unlit (Pathways switches `craftUnlit` on, once, if it is still
  at the default). The Grimoire explains torches, soul torches (refuelled with experience), campfires
  and stoves, and shows the torch recipe whichever way the server crafts it.

## 0.25.2

- Fixed: no stages loaded at all, so Core was never owned and nothing could be bought. Pathways wrote
  its stage files into folders named after nodes (warrior, ranger, scout...), overwriting
  ProgressiveStages' example stages of the same names; the broken examples made ProgressiveStages
  reject every stage. Pathways stages now live in `forever_*` folders and the examples are removed.
  Restart once after updating.
- Core is granted whenever it is missing, not only on a player's first join.
- New `/pathways debug [node]`: what Pathways and ProgressiveStages see for you, in chat.

## 0.25.1

- Wooden tools are unlocked by Woodcutter (ring 1). They had fallen to Curiosities on ring 9, so with
  Tough Beginnings there was no way to mine stone until the mid-game.

## 0.25.0

- Tough Beginnings replaces It's Harder Early. Core holds its flint, fibre and fire; Woodcutter its
  flint axe and blocks of wood. The Grimoire's Early Game chapter teaches it when it is installed,
  and the plain start when it is not.
- Nether Remastered support: Obsidian unlocks the seal and its parts, and the Nether node asks that
  you have used a Seal of the Underworld. New Grimoire appendix, A12.
- Nodes whose items all come from missing mods are left out of the tree; seals ask for the same share
  of what remains.
- Tier I bounties are lighter: walk, crouch, climb, jump, sprint and dig ask for less.
- Nodes are bought only with the panel button, which is now coloured when the node can be bought.
- The Grimoire tab opens with the book anywhere in your inventory. The panel scrollbar is fixed.
- Finished bounties show as completed, on a green row, with no counter.
- The death freeze lasts 10 minutes instead of a full in-game day (20 minutes).
- The Grimoire is in the creative menu, under Tools & Utilities.
- With Tough Beginnings and Supplementaries together, sticks place again and a Pile of Fiber can be lit.
- Fixed: modded nodes (Create, magic and the rest) were cut from the tree in full packs, and modded
  tasks dropped, because the tree was fitted before other mods had registered their items.

## 0.24.0

- New pixel-art interface: gold frames with diagonal corners, patterned backgrounds, cut-gem node plates,
  crisp pixel rings and links, striped seal bands and a proper padlock.
- Lighter unlock animation: a flash, stepped diamonds and sparks, capped and culled.
- Progress popups and the completion banner share the new frames.
- Long lines in the node panel wrap instead of running off the edge; tabs fit on small screens.

## 0.23.0 - first public release

- The ring: over 250 nodes on 16 rings, seven roads, nine Seals and the sixteen eyes.
- Focal Shards from one-off tasks, two daily bounties and vanilla advancements.
- Death costs a shard, a quarter of running progress and a day's freeze.
- Item and machine locks; doors, gates, buttons and levers always usable; decoration locks only its recipe.
- Bosses cannot be hurt until the node that asks for them is reachable.
- The Forever Grimoire (with Patchouli): five chapters and an appendix per supported mod.
- Progress popups and completion banners, switchable per player.
- Integrations with Create, Power Grid, Iron's Spellbooks, Supplementaries, Waystones, Envelope, Chalk,
  Sleep Tight, Icy's Better Horses, Hardcore Torches, Advanced Chimneys, It's Harder Early and others.
- Commands: `/pathways give|take|set|reset|bounties|rebuild`.

The full development history is in `docs/DEVELOPMENT_NOTES.md`.
