# Changelog

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
