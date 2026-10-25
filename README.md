**Forever Pathways adds a progression system to Minecraft. The entire tree contains nodes which the players unlock with 'Focal Shards'; which can be earned by completing daily bounties (small tasks) or larger tasks. Almost every item in the game is locked behind some sort of node, which prevents players from speed-running the game.**

The mod began as part of a passion project: A large Minecraft server for my friends. Our previous servers would always die after a few weeks because of a lack of progression and rewards! Or someone would always try to speed-run and ruin the experience for everyone else. Well… NO MORE! After spending so much time and effort on the mod, I figured other people might wish to use it as well :)

This mod was **NEVER** intended for vanilla Minecraft on its own. While developing it I had countless mods installed, and the tree is built around them. Create, Iron's Spellbooks, Supplementaries, Waystones, Farmer's Delight and plenty more each have nodes of their own. It CAN be finished with nothing but vanilla and the ProgressiveStages mod (a dependency), but it will be a much smaller game. See [Supported mods](#supported-mods).

***

<p align="center">
  <a href="../../releases/latest">
    <img src="docs/download-button.svg" alt="Download Forever Pathways" width="420">
  </a>
  <br>
  <sub>Minecraft 1.21.1 &middot; NeoForge 21.1.256+ &middot; needs ProgressiveStages 3</sub>
</p>

## Contents

*   [The Beginning](#the-beggining)
*   [The Ring](#the-ring)
*   [Sixteen Ages](#sixteen-ages)
*   [Earning Focal Shards](#earning-focal-shards)
*   [Staying Alive](#staying-alive)
*   [What is locked, and what is not](#what-is-locked-and-what-is-not)
*   [The Forever Grimoire](#the-forever-grimoire)
*   [Supported Mods](#supported-mods)
*   [Installing](#installing)
*   [Commands and config](#commands-and-config)
*   [Questions](#questions)
*   [Reporting a Problem](#reporting-a-problem)
*   [Building from Source](#building-from-source)
*   [Credits and Licence](#credits-and-licence)

***

## The Beggining

The start of the game is now quite challenging, especially if you install all the mods that Pathways supports. A guide book--The Grimoire--is granted to all players at spawn if you have the dependency installed.

|              |                                                                                                                                                       |
| ------------ |------------------------------------------------------------------------------------------------------------------------------------------------------ |
| <strong>Nodes</strong> |A node unlocks a set of items or tools. They are unlocked with Focal Shards.                                                                           |
| <strong>Focal Shards</strong> |The only currency in the mod. They are earned by completing bounties or advancements.                                                                  |
| <strong>Seals</strong> |Each age waits behind a Seal that needs a Pathway Level (a number of unlocked nodes) and one larger task.                                              |
| <strong>The eyes</strong> |Sixteen different eyes are needed to open the End. Each is a node on the outer edges of the ring (end-game). Please note the eyes require **End Remastered**. |

Progression is deliberately slow and grindy. The whole tree costs around ~8,500 shards WITH all the supported mods installed. The Vanilla tree is far smaller and cheaper.

***

## The ring

Press **O**, or use the Pathways tab if you run Legendary Tabs to open the Pathways UI. The Core sits in the middle and the ages spread outwards in rings. Notice that all other rings apart from the centre one (The Core) have a lock on them; these are the Seals.

Seven paths are provided:

| Road         |What it holds                                                              |
| ------------ |-------------------------------------------------------------------------- |
| <strong>Survivor</strong> |Stone, copper, iron, gold, diamond and the deep: tools, ore and metals     |
| <strong>Ranger</strong> |Travel, combat, bows, boats, horses, waystones and exploration             |
| <strong>Builder</strong> |Building blocks, glass, lighting, furniture and decoration                 |
| <strong>Homesteader</strong> |Farming, cooking, animals, butchery, brewing herbs and medicine            |
| <strong>Technician</strong> |Create, electricity, radio, post and logistics (Create and more!)          |
| <strong>Magician</strong> |Enchanting, alchemy, spell schools, relics and travel by magic (Waystones) |
| <strong>Constitution</strong> |Health, hardiness and endurance, earned by surviving                       |

Click a node to see what it unlocks, what it needs and what its task is. **Hold** the button to buy it.

***

## Sixteen ages

The ring has up to sixteen levels, from the Core (ring 0) to the **Eye Ring** (ring 15). Rings are grouped into ages, and Seals must be broken to unlock an age. Seals require a Pathway level (number of owned nodes) and for the player to complete that Seal's task.

Early rings are cheap and relatively quick to unlock. The middle of the tree is where players would begin to diversify and choose their role. Some players might choose to focus on the Technician pathway and build machines and contraptions whereas other players might choose the Magician pathway, for alchemy, spells or enchanting. A player can buy most of the entire ring if they wanted to, but this mod rewards team play!

***

## Earning Focal Shards

There are three sources, and no others.

### Tasks

Most nodes carry a one-off task: smelt 48 copper ingots, walk 6,000 blocks, kill 40 skeletons, breed 150 animals. A task only starts counting once its node is reachable. Finishing it pays its shards ONCE and lets you buy the node.

### Bounties

Players get 2 personal bounties per day every morning. These are small tasks designed to provide players with an opportunity to earn some shards.

### Discoveries

A handful of vanilla advancements pay a one-off reward.

While you play, a small popup shows each step of progress on a task or bounty, and a banner and a sound announce a completion. Each player can switch these off at the bottom of the Tasks tab.

***

## Staying alive

This mod makes death quite punishing. And in the future, I will provide extensive config for it.

When you die, you lose 1 Focal Shard, every running task and bounty loses a quarter of its progress, and they freeze for 10 minutes (meaning you cannot complete them). Unlocked nodes are NEVER taken away though.

Players also begin with slower stats. These are improved via unlocking nodes.

| Stat           |Starts at |Can reach |
| -------------- |--------- |--------- |
| Movement speed |85%       |110%      |
| Breaking speed |25%       |100%      |
| Attack damage  |50%       |110%      |
| Boat speed     |80%       |100%      |
| Horse speed    |80%       |100%      |
| Maximum health |100%      |200%      |

## The Forever Grimoire

With [Patchouli](https://www.curseforge.com/minecraft/mc-mods/patchouli) installed, every player spawns with the Grimoire. It can be crafted from paper and an amethyst shard if lost, and with Legendary Tabs it opens as a tab beside your inventory.

| Chapter              |Covers                                                          |
| -------------------- |--------------------------------------------------------------- |
| <strong>I. Early Game</strong> |Surviving the first hours, flint tools and light that burns out |
| <strong>II. The Ring</strong> |Unlocking, shards and what death costs                          |
| <strong>III. Gaining Shards</strong> |Tasks, bounties and achievements                                |
| <strong>IV. The Sixteen Eyes</strong> |How to get every eye                                            |
| <strong>V. Your Past</strong> |A little flavour                                                |
| <strong>Appendices</strong> |One for each supported mod, with recipes                        |

Every node also has a page, with the real crafting recipes for what it unlocks.

***

## Supported mods

Every supported mod is optional. Where its items exist, they are placed in the tree. Where a mod is missing, the nodes that would have held its items are left out of the tree, and the seals ask for fewer nodes to match.

| Area                          |Mods                                                                                                 |
| ----------------------------- |---------------------------------------------------------------------------------------------------- |
| Technology                    |• Create<br>• Create Aeronautics<br>• Power Grid<br>• Simple Radio                                   |
| Magic                         |• Iron's Spellbooks<br>• Waystones                                                                   |
| Building and decoration       |• Supplementaries<br>• Handcrafted<br>• Advanced Chimneys<br>• Terrain Slabs<br>• Dynamic Trees      |
| Food and animals              |• Farmer's Delight<br>• Butchery                                                                     |
| Survival                      |• Tough Beginnings<br>• Nether Remastered<br>• Legendary Survival Overhaul<br>• Hardcore Torches<br>• Sleep Tight<br>• Icy's Better Horses |
| Post, exploration and storage |• Envelope<br>• Chalk<br>• Backpacked                                                                |
| Villages                      |• Thief                                                                                              |
| Bosses                        |• Bosses' Rise<br>• End Remastered<br>• Mutant Monsters                                              |
| Guide                         |• Patchouli<br>• Legendary Tabs                                                                      |

The full list is in [`neoforge.mods.toml`](src/main/templates/META-INF/neoforge.mods.toml). If you run a mod this tree does not know, its items fall under the **Curiosities** node in the middle of the tree rather than staying locked forever.

***

## Installing

|             |                           |
| ----------- |-------------------------- |
| Minecraft   |1.21.1                     |
| Loader      |NeoForge 21.1.256 or later |
| <strong>Required</strong> |<a href="https://www.curseforge.com/minecraft/mc-mods/progressivestages" target="_blank" rel="nofollow">ProgressiveStages</a> 3 |
| Recommended |Patchouli, Legendary Tabs  |

1.  Install NeoForge for 1.21.1.
2.  Put `foreverpathways-<version>.jar` and ProgressiveStages in your `mods` folder.
3.  Add the other mods you want. The more of them, the bigger the tree.
4.  Do the same on the server. **The mod must be installed on the server and on every client.**
5.  Start the game once, then restart it. The first launch writes the stage files and adjusts config; the restart applies them.

> [!NOTE]
> On first run the mod also adjusts two other mods' configs, only where a value is still at that mod's default, and logs exactly what it changed: ProgressiveStages' block interaction switches, and Hardcore Torches' burn times and lantern lighting.

***

## Commands and config

All commands except `debug` need permission level 2.

| Command                                       |Does                                              |
| --------------------------------------------- |------------------------------------------------- |
| `/pathways give <players> <amount>`           |Grant Focal Shards                                |
| `/pathways take <players> <amount>`           |Remove Focal Shards                               |
| `/pathways set <players> <amount>`            |Set Focal Shards                                  |
| `/pathways reset <players>`                   |Wipe a player's ring, tasks and bounties          |
| `/pathways bounties <players>`                |Deal fresh bounties                               |
| `/pathways rebuild`                           |Rewrite the ProgressiveStages files from the tree |
| `/pathways debug [node]`                      |Show in chat what Pathways and ProgressiveStages see for you (and one node). Anyone may use it |

Each player's on-screen notices (progress popups, completion banner, sound) are saved in `config/foreverpathways-client.toml`. The whole tree is `src/main/resources/data/foreverpathways/tree.json`.

***

## Questions

**Can I use it with only vanilla?** Yes, it can be completed that way but it was not designed for that, and a lot of the tree is missing without other mods.

**Does it work on Fabric or Forge?** NeoForge 1.21.1 only.

**Does it work in single player?** Yes, but it will be tough…

**Can I change the tree?** The whole tree is one JSON file, `tree.json`. So yes. You can adjust it if you know a bit of coding. Do not republish the modified tree mod.

***

## Reporting a problem

Please [open an issue](../../issues/new/choose) and pick **Bug report**.

For ideas, such as a mod you would like added, use **Suggestion**.

***

## Credits and licence

Forever Pathways is made by Nahimi. Copyright (c) 2026 Nahimi, [All Rights Reserved](LICENSE).
