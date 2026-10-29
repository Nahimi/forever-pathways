"""Build the Forever Grimoire, the Patchouli guide, out of tree.json.

Usage:
    python tools/build_book.py [tree.json] [resources dir]

Everything a player needs is generated from the tree itself, so the book can never drift from the
mod: the walkthrough reads the tiers, the seal pages read their own levels and tasks, and every node
gets a reference entry with its cost, its task and what it unlocks. Only the prose chapters in
CHAPTERS below are written by hand.

Patchouli 1.21.1 wants book.json under data/ and all of its content under assets/, which is what
use_resource_pack means; it throws if that flag is false.
"""
import json
import re
import sys
import shutil
import sys
from pathlib import Path

BOOK = "grimoire"
NS = "foreverpathways"

# Page width is small, so prose is written in short paragraphs. $(br2) is a blank line, $(l)...$(/l)
# a link, $(#rrggbb) a colour, $(item) and $(thing) Patchouli's own highlight macros.
GOLD = "$(#6B4A1F)"
RED = "$(#8A3B2F)"


def ital(text):
    return "$(o)" + text + "$(/o)"


# --------------------------------------------------------------------------- hand-written chapters
CHAPTERS = [
    ("c1", "I. Early Game", "minecraft:flint", 0, [
        # Shown only without Tough Beginnings: the plain start.
        {"type": "patchouli:text", "title": "I. Early Game", "flag": "!mod:tough_beginnings",
         "text": "Trees are locked at first.$(br2)"
                 "$(li)1. Live off the soft ground, wild plants and raw food.$(br)"
                 "$(li)2. Buy $(l:paths/woodcutter)Woodcutter$() first: logs, planks, wooden tools and the crafting "
                 "table.$(br)"
                 "$(li)3. Then $(l:paths/quarry)Quarry$() for stone and the first tools."},
        # Tough Beginnings: flint, fibre and fire before any wooden tool.
        {"type": "patchouli:text", "title": "I. Early Game", "flag": "mod:tough_beginnings",
         "text": "Bare hands will not break logs, planks or stone.$(br2)"
                 "$(li)1. Dig gravel: sometimes $(thing)raw flint$().$(br)"
                 "$(li)2. Right-click raw flint on stone to knap it: $(thing)flint$(), or "
                 "$(thing)flint shards$() if it breaks.$(br)"
                 "$(li)3. Flint + stick: $(thing)flint knife$().$(br)"
                 "$(li)4. Use the knife on grass: $(thing)plant fibre$().$(br)"
                 "$(li)5. Two fibre: $(thing)fibre rope$().$(br)"
                 "$(li)6. Flint + rope + stick: $(thing)flint axe$()."},
        {"type": "patchouli:crafting", "recipe": "tough_beginnings:flint_knife_recipe",
         "title": "Flint Knife", "flag": "mod:tough_beginnings",
         "text": "Use it on grass for fibre."},
        {"type": "patchouli:crafting", "recipe": "tough_beginnings:fiber_to_string_recipe",
         "title": "Fibre Rope", "flag": "mod:tough_beginnings",
         "text": "Two plant fibre."},
        {"type": "patchouli:crafting", "recipe": "tough_beginnings:flint_axe_recipe",
         "title": "Flint Axe", "flag": "mod:tough_beginnings",
         "text": "Now you can cut wood, once you own $(l:paths/woodcutter)Woodcutter$()."},
        {"type": "patchouli:text", "title": "Wooden Tools", "flag": "mod:tough_beginnings",
         "text": "Wooden tools need $(thing)tough blocks of wood$(), not planks.$(br2)"
                 "$(li)Knife in your main hand, a log or plank in your off-hand, right-click: "
                 "$(thing)block of wood$().$(br)"
                 "$(li)Place a $(thing)pile of fibre$() and right-click it with a stick to light it.$(br)"
                 "$(li)Right-click blocks of wood on the fire, or cook them, to toughen them."},
        {"type": "patchouli:crafting", "recipe": "tough_beginnings:pile_of_fiber_recipe",
         "title": "Pile of Fibre", "flag": "mod:tough_beginnings",
         "text": "Seven fibre. Light it with a stick."},
        {"type": "patchouli:smelting", "recipe": "tough_beginnings:woodblock",
         "title": "Tough Block of Wood", "flag": "mod:tough_beginnings",
         "text": "Or right-click a block of wood on a lit pile of fibre."},
        {"type": "patchouli:crafting", "recipe": "minecraft:wooden_pickaxe",
         "title": "Wooden Pickaxe", "flag": "mod:tough_beginnings",
         "text": "Tough blocks of wood and sticks. Every wooden tool works the same way, once you own "
                 "$(l:paths/woodcutter)Woodcutter$()."},
        {"type": "patchouli:crafting", "recipe": "tough_beginnings:gravel_recipe",
         "title": "Gravel", "flag": "mod:tough_beginnings",
         "text": "Failed flint is not wasted: shards make gravel to dig again."},
        {"type": "patchouli:text",
         "title": "Light Burns Out",
         "flag": "mod:hardcore_torches",
         "text": "Torches and lanterns burn down while lit, and then go out.$(br2)$(li)A burning torch dims to $(thing)smouldering$(), then $(thing)burnt$(). A burnt one can be relit.$(br)$(li)A torch lasts about an hour, a lantern about four.$(br)$(li)Rain makes them smoulder, three times slower. Water and swimming put them out; so does breaking one.$(br)$(li)Carry spares."},
        {"type": "patchouli:crafting",
         "recipe": "hardcore_torches:unlit_torch",
         "recipe2": "hardcore_torches:lit_torch",
         "title": "Torch",
         "flag": "mod:hardcore_torches",
         "text": "Coal and a stick. Torches may only be crafted lit or unlit, depending on your server's settings. An unlit one is lit from a lit torch, a campfire, flint and steel or a fire starter."},
        {"type": "patchouli:crafting",
         "recipe": "hardcore_torches:fire_starter",
         "title": "Fire Starter",
         "flag": "mod:hardcore_torches",
         "text": "String and three sticks. Lights torches and campfires with no flint needed, but only one try in three works. It cannot light lanterns."},
        {"type": "patchouli:text",
         "title": "Soul Torches",
         "flag": "mod:hardcore_torches",
         "text": "Soul torches and soul lanterns burn down too, as long as their plain cousins.$(br2)$(li)They are not refilled with fuel but with $(thing)experience$(): right-click with an empty hand.$(br)$(li)Each click spends 1 experience point and adds about four minutes.$(br)$(li)An experience bottle is worth about seven points."},
        {"type": "patchouli:text",
         "title": "Lanterns and Oil",
         "flag": "mod:hardcore_torches",
         "text": "Lanterns burn fuel too, and are refilled with an $(thing)oil can$(): coal or animal fat. A fire starter will not light them; use a torch, campfire or flint and steel.$(br2)$(li)Lanterns and the oil can: $(l:paths/ironworks)Ironworks$(), ring 4.$(br)$(li)Animal fat: $(l:paths/husbandry)Husbandry$(), ring 2.$(br)$(li)The bandolier carries torches: Ironworks, ring 4."},
        {"type": "patchouli:text",
         "title": "Campfires and Stoves",
         "flag": "mod:hardcore_torches",
         "text": "A campfire is crafted $(thing)unlit$(), and has to be fed fuel to burn: it holds about twenty minutes.$(br2)$(li)Light it with a fire starter, flint and steel or a lit torch.$(br)$(li)With Farmer's Delight, the stove needs fuel in the same way."},
    ]),
    ("c2", "II. The Ring", "minecraft:target", 1, [
        "Most things are locked until you unlock them. Open the ring with "
        "$(k:key.foreverpathways.open) or the tab above your inventory.",
        {"type": "patchouli:text", "title": "Unlocking",
         "text": "$(li)1. Click a node.$(br)"
                 "$(li)2. Finish its task.$(br)"
                 "$(li)3. Pay its Focal Shards.$(br)"
                 "$(li)4. Hold the unlock button.$(br2)"
                 "You start with 2 to 5 shards, by difficulty, and four roads: $(l:paths/quarry)Quarry$(), "
                 "$(l:paths/woodcutter)Woodcutter$(), $(l:paths/ranger)Ranger$(), "
                 "$(l:paths/homesteader)Homesteader$()."},
        {"type": "patchouli:image", "title": "The Ring",
         "images": ["foreverpathways:textures/gui/book/rings.png"], "border": False,
         "text": "Each dot is a node, coloured by road. Red rings are sealed."},
        {"type": "patchouli:text", "title": "Rings and Seals",
         "text": "$(li)15 rings out from the Core; cost climbs with the ring.$(br)"
                 "$(li)A node needs the node before it, and its ring unsealed.$(br)"
                 "$(li)A seal needs a count of nodes owned, shards, and its own task.$(br)"
                 "$(li)Any node counts towards a seal, whatever road it is on."},
        {"type": "patchouli:text", "title": "Locks",
         "text": "$(li)A locked item cannot be used, placed, eaten, swung or worn.$(br)"
                 "$(li)You can still carry it.$(br)"
                 "$(li)Ordinary blocks break and place freely.$(br)"
                 "$(li)Locked $(thing)machines$() will not open, place or break.$(br)"
                 "$(li)Everyday things (beds, doors, chests, stoves, torches) lock only their recipe."},
        {"type": "patchouli:text", "title": "Death",
         "text": "$(#8A3B2F)1 Focal Shard, and on Normal and Hard a quarter of all progress on running "
                 "tasks and bounties.$()$(br2)"
                 "Everything keeps counting. Unlocked nodes and finished tasks are safe."},
    ]),
    ("c3", "III. Gaining Shards", "minecraft:amethyst_shard", 2, [
        "Four sources, and no others. Shards cannot be mined, crafted or traded for.",
        {"type": "patchouli:text", "title": "Tasks",
         "text": "One per node. Pays once, for good.$(br2)"
                 "A task counts only once its node is reachable: the node before it owned, its ring "
                 "unsealed. Earlier work does not count."},
        {"type": "patchouli:text", "title": "Bounties",
         "text": "Three at a time, lasting a few days. The third is always a delivery.$(br2)"
                 "$(li)Finish one and the next comes 3 minutes later, or at dawn if that is sooner.$(br)"
                 "$(li)Hold right-click on one in the Tasks tab to give it up, once a day; a new one comes at once.$(br)"
                 "$(li)They grow harder as you unlock nodes, and pay up to 4 shards; rare ones pay 5."},
        {"type": "patchouli:text", "title": "Deliveries",
         "text": "Some bounties ask you to hand in crafted items: 32 Iron Ingots, 2 Bookshelves.$(br2)"
                 "$(li)Carry them all, then press Deliver.$(br)"
                 "$(li)Plain items only: not damaged, named or enchanted.$(br)"
                 "$(li)The third bounty is always one. Delivered or expired, it waits for dawn; given up, a new one comes at once."},
        {"type": "patchouli:text", "title": "Staying Alive",
         "text": "Every full day you live without dying pays 1 Focal Shard.$(br2)"
                 "Dying starts the count again."},
        {"type": "patchouli:text", "title": "Achievements",
         "text": "A few vanilla advancements pay once: first iron smelted, first iron pick, and "
                 "others.$(br2)"
                 "First come, first paid."},
        {"type": "patchouli:text", "title": "Keeping Track",
         "text": "$(li)A small popup at the right edge as a task or bounty moves on.$(br)"
                 "$(li)A banner and a chime when one is complete.$(br2)"
                 "Turn any of these off at the bottom of the Tasks tab on the ring."},
    ]),
    ("c4", "IV. The Sixteen Eyes", "minecraft:ender_eye", 3, [
        "The End portal needs sixteen $(thing)different$() eyes. None can be crafted, mined or found.$(br2)Each is a node on the last ring, behind the final seal. Unlocking one gives you its eye.",
        {"type": "patchouli:text", "title": "Where They Come From",
         "text": "$(li)Each eye's task is a boss kill: the six bosses of Bosses' Rise, the eight of Cataclysm, Azazel and the Wither.$(br)$(li)Without a boss's mod, its node asks for other work.$(br2)Every eye and its boss is listed under $(l:eyes/about)The Sixteen Eyes$()."},
        {"type": "patchouli:text", "title": "You Cannot Waste a Boss",
         "text": "A boss takes no damage from you until a node that asks for it is reachable.$(br2)Kill it again after unlocking its eye node, carrying none of that eye, and you get another."},
    ]),
    ("c5", "V. Your Past", "minecraft:ender_pearl", 4, [
        "You woke with no memory and a book whose letters glow. I knew you once.$(br2)"
        "What you knew is held in the ring. Take it back a piece at a time.$(br2)"
        "The Monoliths were here before either of us. Some of their stones still remember your name.",
    ]),
]
BEYOND = [
    ("a1", "A1. Beds & Sleep", "sleep_tight:hammock_red", 1, "mod:sleep_tight", [
        "$(li)Sleeping in the same bed makes it a $(thing)home bed$(), which gives more back each "
        "night.$(br)"
        "$(li)Hammocks for a camp: $(l:paths/furnishings)Furnishings$(), ring 3.$(br)"
        "$(li)Night bag for the road: $(l:paths/dreaming)Dreaming$(), ring 9.",
        {"type": "patchouli:text", "title": "Infested Beds",
         "text": "$(#8A3B2F)Mansion beds are often infested.$() Sleep in one and bedbugs wake "
                 "you.$(br2)"
                 "$(li)Break it and make another; you lose what that bed had become.$(br)"
                 "$(li)Or throw a $(thing)splash potion of Harming$() at it. Needs "
                 "$(l:paths/brewing)Brewing$(), ring 9."},
    ]),
    ("a2", "A2. Thirst & Cold", "legendarysurvivaloverhaul:canteen", 2,
     "mod:legendarysurvivaloverhaul", [
        "$(li)You get thirsty. Drink from a $(thing)canteen$(); water plants refill it.$(br)"
        "$(li)Juices quench more than water.$(br)"
        "$(li)Wounds want $(thing)bandages$(), then $(thing)plasters$().$(br)"
        "$(li)Heat and cold both kill. Seasons turn.",
        {"type": "patchouli:text", "title": "Climate Gear",
         "text": "$(li)$(l:paths/climate_1)Climate Gear I$(), ring 4: coats, heater, cooler.$(br)"
                 "$(li)$(l:paths/climate_2)Climate Gear II$(), ring 6: better coats, resistance "
                 "rings.$(br)"
                 "$(li)$(l:paths/climate_3)Climate Gear III$(): the last grade."},
    ]),
    ("a3", "A3. Butchery", "butchery:copper_cleaver", 3, "mod:butchery", [
        "Meat is not one item. An animal gives a $(thing)carcass$(), a carcass gives cuts, cuts cook "
        "into food. You need tools and a butcher's table.",
        {"type": "patchouli:text", "title": "Tools",
         "text": "$(li)$(thing)Cleaver$(): carcasses and cuts.$(br)"
                 "$(li)$(thing)Hacksaw$(): bone.$(br)"
                 "$(li)$(thing)Skinning knife$(): hide and fur.$(br)"
                 "$(li)$(thing)Hammer$(): the rest.$(br2)"
                 "Bone and copper: $(l:paths/husbandry)Husbandry$(), ring 2. Iron and gold: "
                 "$(l:paths/butcher_tools)Butcher's Steel$(), ring 5."},
        {"type": "patchouli:text", "title": "Further Out",
         "text": "$(li)$(l:paths/abattoir)Abattoir$(), ring 5: cuts, blood work, the grinder.$(br)"
                 "$(li)$(l:paths/charcuterie)Charcuterie$(), ring 6: sausage, ham, salami.$(br)"
                 "$(li)$(l:paths/taxidermy)Taxidermy$(), ring 6: heads, skeletons, furs.$(br)"
                 "$(li)$(l:paths/butchers_shop)Butcher's Shop$(), ring 6: displays and counters."},
    ]),
    ("a4", "A4. Machines", "create:cogwheel", 4, "mod:create", [
        "Create turns rotation into work; shafts and cogs carry it.$(br2)"
        "$(li)$(l:paths/gearing)Gearing$(), ring 3: cogs, gearboxes.$(br)"
        "$(li)$(l:paths/waterworks)Waterworks$(), ring 4: water wheels.$(br)"
        "$(li)$(l:paths/mechanisms)Mechanisms$(), ring 7, then belts, crushing, fluids.$(br)"
        "$(li)$(l:paths/brass)Brass$(), ring 8: the second half of the mod.",
    ]),
    ("a5", "A5. Electricity", "powergrid:copper_cord", 5, "mod:powergrid", [
        "Separate from Create, and it starts earlier.$(br2)"
        "$(li)$(l:paths/wiring)Wiring$(), ring 3: cord, wire, potato battery.$(br)"
        "$(li)$(l:paths/lighting_rig)Lighting Rig$(), ring 4: bulbs, meters.$(br)"
        "$(li)$(l:paths/power_grid)Power Grid$(), ring 10: generators, motors.$(br)"
        "$(li)$(l:paths/high_voltage)High Voltage$(), ring 14: the end of it.",
    ]),
    ("a6", "A6. Magic", "irons_spellbooks:wimpy_spell_book", 6, "mod:irons_spellbooks", [
        "Late, and dearer: every magic node costs half again a machine on the same ring.$(br2)"
        "$(li)$(l:paths/magician)Magician$(), ring 6: books, scrolls, ink.$(br)"
        "$(li)$(l:paths/study)Study$(), $(l:paths/alchemy)Alchemy$(), ring 7.$(br)"
        "$(li)Eight schools, ring 10. $(thing)You may learn three.$(br)"
        "$(li)Robes 11, relics 12, Eldritch 13, Grand Magus 14.",
        {"type": "patchouli:text", "title": "Enchanting",
         "text": "Capped by your nodes.$(br2)"
                 "$(li)$(l:paths/enchant)Enchanting$(), ring 7: level 5.$(br)"
                 "$(li)$(l:paths/library)Library$(), ring 9: level 15.$(br)"
                 "$(li)$(l:paths/grand_library)Grand Library$(), ring 10: level 30."},
    ]),
    ("a7", "A7. Getting About", "waystones:waystone", 7, "mod:waystones", [
        "$(li)$(thing)Horses$() take shoes, a cart and an upgraded saddle: "
        "$(l:paths/rider)Rider$(), $(l:paths/cartwright)Cartwright$(), $(l:paths/tack)Tack$().$(br)"
        "$(li)$(thing)Trains$(): ring 10. $(thing)Airships$(): "
        "$(l:paths/aeronautics)Aeronautics$(), ring 12.$(br2)"
        "Your legs start at 85% and reach 110% only at ring 14. A horse is worth having early.",
        {"type": "patchouli:text", "title": "The Monoliths",
         "text": "Great stones stand across the world, each holding a $(thing)waystone$().$(br2)"
                 "$(li)Right-click one to wake it.$(br)"
                 "$(li)From any woken waystone, travel to the others you have woken.$(br2)"
                 "They answer you from $(l:paths/explorer)Explorer$(), ring 5."},
        {"type": "patchouli:text", "title": "Your Own Waystones",
         "text": "$(li)$(l:paths/explorer)Explorer$(), ring 5: use the Monoliths' waystones. You cannot "
                 "move or break them.$(br)"
                 "$(li)$(l:paths/attunement)Attunement$(), ring 11: craft and place your own, with warp "
                 "stones and scrolls.$(br)"
                 "$(li)$(l:paths/warp_network)Warp Network$(), ring 13: sharestones, portstones and "
                 "warp plates."},
        {"type": "patchouli:crafting", "recipe": "waystones:waystone", "recipe2": "waystones:warp_stone",
         "title": "Waystone and Warp Stone"},
        {"type": "patchouli:text", "title": "Immersive Aircraft", "flag": "mod:immersive_aircraft",
         "text": "Two nodes on the Ranger road, after Boatwright:$(br2)$(li)$(l:paths/aviator)Aviator$(), ring 6: the airship, gyrodyne, biplane and quadrocopter, and their parts.$(br)$(li)$(l:paths/air_fleet)Air Fleet$(), ring 9: the cargo airship, the warship, better engines and the weapons."},
    ]),
    ("a8", "A8. Smoke", "adchimneys:small_brick_chimney", 8, "mod:adchimneys", [
        "$(li)A $(thing)chimney$() vents smoke off the roof.$(br)"
        "$(li)$(thing)Pipes$() and $(thing)ducts$() carry it.$(br)"
        "$(li)$(thing)Vents$() release it where you want.$(br)"
        "$(li)$(thing)Pumps$() push it, and take a redstone signal.",
        {"type": "patchouli:text", "title": "Where They Unlock",
         "text": "$(li)$(l:paths/chimneys)Chimneys$(), ring 4: stone, cobble, brick.$(br)"
                 "$(li)$(l:paths/flues)Flues$(), ring 5: glass and metal, pipes, ducts, vents.$(br)"
                 "$(li)$(l:paths/draught)Draught$(), ring 7: the pumps."},
    ]),
    ("a9", "A9. Post & Pigeons", "envelope:mailbox", 9, "mod:envelope", [
        "$(li)Write with a $(thing)letter and quill$(). Pack items in a $(thing)paper box$().$(br)"
        "$(li)Put mail in a $(thing)mailbox$() with food for the courier.$(br)"
        "$(li)A $(thing)pigeon$() flies by day for seeds. A $(thing)bat$() flies by night for a spider eye.$(br)"
        "$(li)Name your mailbox to get an address.",
        {"type": "patchouli:crafting", "recipe": "envelope:letter_and_quill", "recipe2": "envelope:mailbox",
         "title": "Letters and the Mailbox"},
        {"type": "patchouli:crafting", "recipe": "envelope:oak_pigeonhole", "recipe2": "envelope:paper_box",
         "title": "Pigeonhole and Paper Box"},
        {"type": "patchouli:text", "title": "Services",
         "text": "Some addresses are not people.$(br2)"
                 "$(li)The $(thing)Mail Service$() trades by post: a diamond buys a parcel of lost mail.$(br)"
                 "$(li)The $(thing)Automated Supply Service$() crafts what you send it the ingredients for.$(br2)"
                 "Anything it sends back still obeys the ring: a locked item stays locked."},
        {"type": "patchouli:text", "title": "Where They Unlock",
         "text": "$(li)$(l:paths/post)Post$(), ring 3: letters, mailbox, pigeonholes, paper boxes.$(br)"
                 "$(li)$(l:paths/parcels)Parcels$(), ring 4: packages, seal stamps, payback tags.$(br)"
                 "$(li)Create's $(l:paths/packaging)Packaging$(), ring 9, builds on Parcels."},
    ]),
    ("a10", "A10. Chalk", "chalk:chalk", 10, "mod:chalk", [
        "$(li)Use $(thing)chalk$() on a block to draw a mark. Mark the way out of a cave.$(br)"
        "$(li)Dye it for colour. Special symbols are learnt as you go.$(br)"
        "$(li)A $(thing)chalk box$() holds several. Add a glow ink sac and the marks glow.$(br2)"
        "All at $(l:paths/chalk)Chalk$(), ring 2, from calcite.",
        {"type": "patchouli:crafting", "recipe": "chalk:chalk", "recipe2": "chalk:chalk_box",
         "title": "Chalk and the Box"},
    ]),
    ("a11", "A11. Villages", "minecraft:emerald", 11, "mod:thief", [
        "Villagers watch you.$(br2)"
        "$(li)Take from their chests, break their things or sleep in their beds and you are $(thing)seen$().$(br)"
        "$(li)Crimes lower your $(thing)reputation$(). Low enough and they refuse to trade; lower and the "
        "guards attack.",
        {"type": "patchouli:text", "title": "Staying Welcome",
         "text": "$(li)Gifts win them back.$(br)"
                 "$(li)Small crimes fade with time; heavy ones linger.$(br2)"
                 "Trading is a task at $(l:paths/trader)Trader$(), so stay welcome."},
    ]),
    ("a13", "A13. Foes and Feasts", "minecraft:skeleton_skull", 13,
     "|mod:hearthandharvest,mod:remains,mod:bestiary,mod:legionary,mod:cataclysm,mod:hazennstuff,mod:born_in_chaos_v1,mod:netherman", [
        {"type": "patchouli:text", "title": "Hearth and Harvest", "flag": "mod:hearthandharvest",
         "text": "Three nodes on the Homesteader road:$(br2)"
                 "$(li)$(l:paths/hh_orchard)Orchard and Trellis$(), ring 3: crops, trellises and farm tools.$(br)"
                 "$(li)$(l:paths/hh_hearth)Hearth$(), ring 4: pies, cakes, sweets, cheese and its kitchen furniture.$(br)"
                 "$(li)$(l:paths/hh_cellar)Cellar$(), ring 5: casks, kegs, jams, pickles, juices and wine."},
        {"type": "patchouli:text", "title": "Vaskar's Remains", "flag": "mod:remains",
         "text": "The dead you find in the world can always be searched.$(br2)"
                 "What they carry, their old weapons, staves, crowns and keepsakes, is yours to use once you "
                 "own $(l:paths/relics)Relics of the Fallen$(), ring 4 on the Ranger road."},
        {"type": "patchouli:text", "title": "Vaskar's Bestiary", "flag": "mod:bestiary",
         "text": "$(li)$(l:paths/beast_lore)Beast Lore$(), ring 4: the hides, glands, scales and lair blocks "
                 "of its creatures. Kill three trolls first.$(br)"
                 "$(li)$(l:paths/titans)Titans$(), ring 8: the trophies of the Deep Mother, Ember Heart, Ice "
                 "Colossus, Sand Maw and Trap Crown. Kill any one of them."},
        {"type": "patchouli:text", "title": "Legionary", "flag": "mod:legionary",
         "text": "Kill the Unbreakable to open $(l:paths/unbreakable)The Unbreakable$(), ring 6 on the Ranger "
                 "road, for its cudgel, gold shards and the golden chains and lanterns."},
        {"type": "patchouli:text", "title": "L_Ender's Cataclysm", "flag": "mod:cataclysm",
         "text": "Eight boss nodes on the Ranger road, one for each boss, each asking for its kill. The first road:$(br2)$(li)$(l:paths/cata_end)The Ender Guardian$(), ring 9.$(br)$(li)$(l:paths/cata_forge)The Netherite Monstrosity$(), ring 10.$(br)$(li)$(l:paths/cata_fire)Ignis$(), ring 11.$(br)$(li)$(l:paths/cata_void)The Harbinger$(), ring 12."},
        {"type": "patchouli:text", "title": "Cataclysm, Continued", "flag": "mod:cataclysm",
         "text": "The second road:$(br2)$(li)$(l:paths/cata_desert)Ancient Remnant$(), ring 9.$(br)$(li)$(l:paths/cata_deep)The Leviathan$(), ring 10.$(br)$(li)$(l:paths/cata_cursed)Maledictus$(), ring 11.$(br)$(li)$(l:paths/cata_storm)Scylla$(), ring 12.$(br2)Each boss's lesser kin, such as the Kobolediator or the draugr, share their master's node."},
        {"type": "patchouli:text", "title": "Born in Chaos", "flag": "mod:born_in_chaos_v1",
         "text": "Two nodes on the Ranger road:$(br2)$(li)$(l:paths/chaos)Born in Chaos$(), ring 5: dark metal, charms, elixirs and the blocks of its haunts. Kill 20 of its creatures first.$(br)$(li)$(l:paths/chaos_lords)Lords of Chaos$(), ring 8: the bosses' trophies. Kill any one of them."},
        {"type": "patchouli:text", "title": "Cult of Azazel", "flag": "mod:netherman",
         "text": "Kill the Divine Chariot Azazel to open $(l:paths/azazel)The Divine Chariot$(), ring 10 on the Ranger road, for its scythe, spear, shield and armour. Its samsonit halls and keys are at $(l:paths/nether)Nether$(). Azazel's kill is also the Evil Eye's task."},
        {"type": "patchouli:text", "title": "Hazen N Stuff", "flag": "mod:hazennstuff",
         "text": "Three nodes on the Magician road:$(br2)"
                 "$(li)$(l:paths/hazen_metals)Hazen's Metals$(), ring 9: ores, ingots and its woods.$(br)"
                 "$(li)$(l:paths/hazen_runes)Runes and Orbs$(), ring 10.$(br)"
                 "$(li)$(l:paths/hazen_arsenal)Flamebearer's Arsenal$(), ring 11: every weapon and armour set. "
                 "Kill one of the Flamebearer's officers first."},
    ]),
    ("a12", "A12. The Underworld Seal", "nether_remastered:seal_of_the_underworld_item", 12,
     "mod:nether_remastered", [
        "The Nether is shut. Its portals will not take you until you have used a "
        "$(thing)Seal of the Underworld$().$(br2)"
        "$(li)Four $(thing)Mysterious Shards$(), four $(thing)Bottled Nether Essence$() and a "
        "$(thing)Nether Crystal$() make one.$(br)"
        "$(li)Right-click it to use it. It is used up.",
        {"type": "patchouli:crafting", "recipe": "nether_remastered:sotu_recipe",
         "title": "Seal of the Underworld", "text": "One shard in each corner, the crystal at its heart."},
        {"type": "patchouli:text", "title": "The Shards",
         "text": "Each corner is found somewhere else, about one time in ten.$(br2)"
                 "$(li)Top left: abandoned mineshaft chests.$(br)"
                 "$(li)Top right: witches.$(br)"
                 "$(li)Bottom left: evokers.$(br)"
                 "$(li)Bottom right: pillager outpost chests."},
        {"type": "patchouli:text", "title": "Crystal and Essence",
         "text": "$(li)The $(thing)Nether Crystal$() is brushed from desert pyramids and trail ruins.$(br)"
                 "$(li)Kill the undead with an $(thing)obsidian-infused bottle$() in your off-hand to "
                 "bottle their $(thing)essence$().$(br)"
                 "$(li)Now and then a whole seal turns up in ruins or a desert well."},
        {"type": "patchouli:crafting", "recipe": "nether_remastered:obsidian_infused_bottle",
         "title": "Obsidian-Infused Bottle", "text": "Crying obsidian and glass. Makes three."},
        {"type": "patchouli:text", "title": "On the Ring",
         "text": "The shards, crystal, bottles and the seal are unlocked at "
                 "$(l:paths/obsidian)Obsidian$(), ring 6.$(br2)"
                 "$(l:paths/nether)Nether$(), ring 6, cannot be bought until you have used a seal."},
    ]),
]
MODIDS = {"icys-better-horses": "icys_better_horses"}
# Category icons are always vanilla, so a road never loses its picture.
PATH_ICONS = {
    "survivor": "minecraft:iron_pickaxe",
    "ranger": "minecraft:bow",
    "builder": "minecraft:bricks",
    "homesteader": "minecraft:wheat",
    "technician": "minecraft:redstone",
    "magician": "minecraft:enchanted_book",
    "constitution": "minecraft:golden_apple",
}


def mod_flag(*ids):
    """The Patchouli flag that hides a page unless the mod owning these items is installed."""
    mods = []
    for item in ids:
        if not item or ":" not in item:
            continue
        ns = item.split(":", 1)[0]
        if ns == "minecraft":
            continue
        mod = MODIDS.get(ns, ns)
        if mod not in mods:
            mods.append(mod)
    return "&".join("mod:" + m for m in mods) if mods else None


ORDINALS = ["nought", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth",
            "ninth", "tenth", "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth",
            "sixteenth"]


def ordinal(n):
    return ORDINALS[n] if 0 <= n < len(ORDINALS) else str(n) + "th"


def slug(node_id):
    return node_id.split(":", 1)[1]


def kind_text(task):
    return task["title"]


def embolden(data):
    """Every page of prose is set in bold: it reads far better at this size on a pale page."""
    for page in data.get("pages", []):
        text = page.get("text")
        if not text:
            continue
        page["text"] = "$(bold)" + text.replace("$()", "$(n)")
    return data


def pretty_item(item):
    return item.split(":")[-1].replace("_", " ").strip().capitalize()


def recipe_index():
    """item id -> crafting recipe id, built from the mods' own data by tools/index_recipes.py."""
    path = Path("tools/recipe_index.json")
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def claimed_items(tree):
    """item id -> node id, by the same rules the mod locks with."""
    sys.path.insert(0, "tools")
    try:
        import check_tree
        items = json.loads(Path("tools/items.json").read_text(encoding="utf-8"))
        return check_tree.resolve(tree, items)[1]
    except Exception:
        return {}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def lock_summary(node, limit=42):
    """A readable sentence for what a node's lock specs actually cover."""
    pretty = []
    for spec in node.get("locks", []):
        if spec.startswith("rest:"):
            pretty.append("everything else of its kind")
        elif spec.startswith("mod:"):
            pretty.append("everything of its kind")
        elif spec.startswith("re:"):
            continue
        else:
            pretty.append(spec.split(":")[-1].replace("_", " "))
    out = []
    for item in pretty:
        if item not in out:
            out.append(item)
    if not out:
        return None
    if len(out) > limit:
        out = out[:limit] + ["and more"]
    return ", ".join(out)


def main():
    tree_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
        "src/main/resources/data/foreverpathways/tree.json")
    res = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("src/main/resources")
    tree = json.loads(tree_path.read_text(encoding="utf-8"))
    recipes = recipe_index()
    owned_items = {}
    for item, node_id in claimed_items(tree).items():
        owned_items.setdefault(node_id, []).append(item)
    nodes = tree["nodes"]
    by_id = {n["id"]: n for n in nodes}
    tiers = tree["tiers"]
    paths = {p["id"]: p for p in tree["paths"]}
    seals = sorted([n for n in nodes if n.get("seal")], key=lambda n: n["seal"]["opensRing"])
    outer = max(n["ring"] for n in nodes)

    data_root = res / "data" / NS / "patchouli_books" / BOOK
    content = res / "assets" / NS / "patchouli_books" / BOOK / "en_us"
    for folder in (content / "categories", content / "entries"):
        if folder.exists():
            shutil.rmtree(folder)

    # ------------------------------------------------------------------ book.json
    write(data_root / "book.json", {
        "name": "The Forever Grimoire",
        "landing_text": "You do not know me. I knew you, once.$(br2)Read, and I will tell you what "
                        "this world has taken and how to take it back.",
        "use_resource_pack": True,
        "i18n": False,
        "version": "1",
        "subtitle": "Forever Pathways",
        "model": "patchouli:book_brown",
        "index_icon": "minecraft:amethyst_shard",
        "text_color": "241C14",
        "header_color": "6B4A1F",
        "nameplate_color": "C4A574",
        "link_color": "8A5E22",
        "link_hover_color": "B07A2E",
        "progress_bar_color": "C4A574",
        "progress_bar_background": "3A3128",
        "show_progress": True,
        "pause_game": False,
        "macros": {
            "$(n)": "$(reset)$(bold)",
            "$(thing)": "$(bold)$(#6B4A1F)",
            "$(seal)": "$(bold)$(#8A3B2F)",
        },
    })

    cats = []

    def category(cid, name, desc, icon, sortnum, parent=None):
        body = {"name": name, "description": desc, "icon": icon, "sortnum": sortnum}
        if parent:
            body["parent"] = f"{NS}:{parent}"
        write(content / "categories" / f"{cid}.json", body)
        cats.append(cid)

    # ------------------------------------------------------------------ categories
    category("start", "Chapters",
             "Read these first: the hard start, the ring, shards, the eyes.",
             "minecraft:oak_sapling", 0)
    category("beyond", "Appendices",
             "One per mod that changes how the world works.",
             "minecraft:writable_book", 1)
    category("rings", "Rings and Seals",
             "The map of the ring, and what each seal asks before it breaks.",
             "minecraft:gold_block", 2)
    category("paths", "Nodes",
             "Every node: cost, task, what must come first, and what it unlocks.",
             "minecraft:crafting_table", 3)
    for i, (pid, path) in enumerate(paths.items()):
        first = min((n["ring"] for n in nodes if n["path"] == pid and n["ring"] < outer), default=1)
        category(f"paths/{pid}", path["title"],
                 f"The {path['title']} road, from ring {first} outwards.",
                 PATH_ICONS.get(pid, "minecraft:stone"),
                 10 + i, parent="paths")
    category("eyes", "The Sixteen Eyes",
             "Every eye, and what drops it.",
             "minecraft:ender_eye", 4)

    entries = 0

    def entry(eid, name, cat, icon, pages, sortnum=0, priority=False, flag=None):
        nonlocal entries
        body = {"name": name, "category": f"{NS}:{cat}", "icon": icon,
                "sortnum": sortnum, "pages": pages}
        if priority:
            body["priority"] = True
        if flag:
            body["flag"] = flag
        embolden(body)
        write(content / "entries" / f"{eid}.json", body)
        entries += 1

    # ------------------------------------------------------------------ Start Here
    for cid, title, icon, sortnum, paragraphs in CHAPTERS:
        pages = []
        for i, piece in enumerate(paragraphs):
            # A plain string is a page of prose; a dict is a page of its own kind, such as a recipe.
            page = {"type": "patchouli:text", "text": piece} if isinstance(piece, str) else dict(piece)
            if i == 0 and "title" not in page:
                page["title"] = title
            pages.append(page)
        entry(cid, title, "start", icon, pages, sortnum, priority=(sortnum <= 1))

    # ------------------------------------------------------------------ Not Vanilla
    for cid, title, icon, sortnum, flag, paragraphs in BEYOND:
        pages = []
        for i, piece in enumerate(paragraphs):
            page = {"type": "patchouli:text", "text": piece} if isinstance(piece, str) else dict(piece)
            if i == 0 and "title" not in page:
                page["title"] = title
            pages.append(page)
        entry(cid, title, "beyond", icon, pages, sortnum, flag=flag)

    # ------------------------------------------------------------------ Rings and Seals
    entry("about_rings", "Rings", "rings", "minecraft:target", pages=[
        {"type": "patchouli:image", "title": "The Ring",
         "images": ["foreverpathways:textures/gui/book/rings.png"], "border": False,
         "text": "The Core at the centre. Each dot is a node, coloured by its road. Red rings are "
                 "still sealed; each padlock is a seal."},
        {"type": "patchouli:text", "title": "Rings",
         "text": f"$(thing){outer} rings$() out from the Core, each with a name: "
                 f"{tiers[1]}, {tiers[2]}, {tiers[3]}, out to {tiers[outer]}.$(br2)"
                 "A node needs the node before it, whatever else it needs, and its ring unsealed."},
        {"type": "patchouli:text",
         "text": "Cost climbs with the ring: a few shards on ring 2, about a hundred on ring "
                 "14.$(br2)"
                 "Seals count how many nodes you own, not which. Breadth gets you out, not one long "
                 "road.$(br2)Costs in this book are Hard costs. On Normal nodes cost 5% less, on Easy a "
                 "quarter less."},
    ], sortnum=0, priority=True)
    for i, seal in enumerate(seals):
        band = seal["seal"]
        a, b = band["opensRing"], band.get("throughRing", band["opensRing"])
        covered = f"ring {a}" if a == b else f"rings {a} to {b}"
        names = " and ".join(tiers[r] for r in range(a, b + 1))
        inside = [n for n in nodes if a <= n["ring"] <= b and not n.get("seal")]
        pages = [{
            "type": "patchouli:text", "title": seal["title"],
            "text": f"Bars {covered}: {names}.$(br2)"
                    f"$(li)$(thing){band['level']} nodes$() owned$(br)"
                    f"$(li)$(thing){seal['cost']} Focal Shards$()$(br)"
                    f"$(li)its task, below$(br2)"
                    f"{len(inside)} nodes wait behind it.",
        }]
        if seal.get("task"):
            pages.append({"type": "patchouli:text", "title": "Task",
                          "text": f"$(thing){kind_text(seal['task'])}$()$(br2)"
                                  "Counts only once everything else about the seal is ready."})
        # A seal must never vanish from the guide, so its icon falls back to something vanilla.
        icon = seal["item"] or "minecraft:gold_ingot"
        if not icon.startswith("minecraft:"):
            icon = "minecraft:gold_block"
        entry(f"rings/{slug(seal['id'])}", seal["title"], "rings", icon, pages, 10 + i)

    # ------------------------------------------------------------------ The eyes
    # Each eye node gives its eye on unlock; its task is the first of its bosses this pack has, else its own task.
    eye_nodes = [n for n in nodes if n["ring"] == outer and not n.get("seal")]
    cost = eye_nodes[0]["cost"] if eye_nodes else 0
    entry("eyes/about", "The Sixteen Eyes", "eyes", "minecraft:ender_eye", [
        {"type": "patchouli:text", "title": "The Sixteen Eyes",
         "text": "The End portal needs sixteen different eyes. None can be mined, crafted or found: each node on the last ring gives you its eye the moment you unlock it.$(br2)Every eye is a node on the last ring, $(thing){cost} Focal Shards$() each, all behind the final seal.".replace("{cost}", str(cost))},
        {"type": "patchouli:text", "title": "Boss by Boss",
         "text": "Each eye's task is a boss kill: Bosses' Rise, Cataclysm, Cult of Azazel or the Wither. Without that boss's mod, the node asks for other work instead.$(br2)A boss takes no damage from you until a node that asks for it is reachable, so you cannot waste a kill."},
        {"type": "patchouli:text", "title": "Lost Eyes",
         "text": "Lost an eye? Kill its boss again while you own its node and carry none of that eye, and you are given another."},
    ], 0, priority=True)

    def eye_task(node):
        bosses = node.get("bosses") or []
        lines = [b["title"] + "." for b in bosses]
        if node.get("task") and not any(b["target"].startswith("minecraft:") for b in bosses):
            lines.append(("Without those bosses: " if len(bosses) > 1 else "Without that boss: ")
                         + node["task"]["title"] + ".")
        return "$(br)".join(lines)

    for i, node in enumerate(sorted(eye_nodes, key=lambda n: n["title"])):
        entry(f"eyes/{slug(node['id'])}", node["title"], "eyes", node["item"],
              [{"type": "patchouli:spotlight", "item": node["item"], "title": node["title"],
                "text": f"{node['opens']}$(br2)$(thing){node['cost']} Focal Shards$().$(br2){eye_task(node)}"}],
              sortnum=20 + i, flag=mod_flag(node["item"]))

    # ------------------------------------------------------------------ every other node
    for node in nodes:
        if node.get("seal") or node["ring"] == outer or node["requires"] is None:
            continue
        lines = [f"$(thing){tiers[node['ring']]}$(), ring {node['ring']}.",
                 f"$(thing){node['cost']} Focal Shards$()."]
        needs = [by_id[i]["title"] for i in (node["requires"], node.get("also")) if i in by_id]
        if needs:
            lines.append("After " + " and ".join(needs) + ".")
        pages = [{"type": "patchouli:spotlight", "item": node["item"] or "minecraft:barrier",
                  "title": node["title"],
                  "text": node["opens"] + "$(br2)" + "$(br)".join(lines)}]
        if node.get("task"):
            task_page = {"type": "patchouli:text", "title": "Task",
                         "text": f"$(thing){kind_text(node['task'])}$()$(br2)"
                                 f"Pays {node['task']['reward']} Focal Shard"
                                 f"{'s' if node['task']['reward'] != 1 else ''} once. Counts only "
                                 "once this node is reachable."}
            # A mod's advancement is only a task with that mod installed (Availability.java drops it).
            if node["task"]["kind"] == "advancement" and mod_flag(node["task"]["target"]):
                task_page["flag"] = mod_flag(node["task"]["target"])
            pages.append(task_page)
        extra = []
        if node.get("craftOnly"):
            extra.append("$(thing)Craft-locked only.$() Anyone can place, use and break these; the "
                         "node buys the recipe.")
        if node.get("perks"):
            names = {"movement": "movement speed", "breaking": "breaking speed",
                     "attack": "attack damage", "boat": "boat speed", "health": "maximum health"}
            extra.append("$(br)".join(
                f"$(thing)+{int(v * 100)}% {names.get(k, k)}$()" for k, v in node["perks"].items()))
        if node.get("enchantPower"):
            extra.append(f"Enchanting table up to $(thing)level {node['enchantPower']}$().")
        if node.get("slot"):
            limit = tree["slots"].get(node["slot"])
            if limit:
                extra.append(f"You may own $(thing){limit}$() of these in all. Choose carefully.")
        if extra:
            pages.append({"type": "patchouli:text", "title": "Notes",
                          "text": "$(br2)".join(extra)})
        # Real crafting grids for what this node opens, up to four, the icon item first.
        mine = sorted(owned_items.get(node["id"], []))
        if node.get("item") in mine:
            mine.remove(node["item"])
            mine.insert(0, node["item"])
        shown = 0
        for item in mine:
            recipe = recipes.get(item)
            if not recipe or shown >= 1:
                continue
            page = {"type": "patchouli:crafting", "recipe": recipe,
                    "text": pretty_item(item)}
            flag = mod_flag(item)
            if flag:
                page["flag"] = flag
            pages.append(page)
            shown += 1
        unlocks = lock_summary(node)
        if unlocks:
            pages.append({"type": "patchouli:text", "title": "Unlocks",
                          "text": unlocks + "."})
        entry(f"paths/{slug(node['id'])}", node["title"], f"paths/{node['path']}",
              node["item"] or "minecraft:barrier", pages,
              sortnum=node["ring"] * 100, flag=mod_flag(node["item"]))

    print(f"grimoire: {len(cats)} categories, {entries} entries")


if __name__ == "__main__":
    main()
