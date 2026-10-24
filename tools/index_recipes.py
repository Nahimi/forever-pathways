"""Build tools/recipe_index.json: every craftable item in the pack, to its recipe id.

Usage:
    python tools/index_recipes.py <folder with extracted mod jars> [more folders ...]

The Grimoire shows real crafting grids rather than describing them, and Patchouli needs a recipe id
to do that. Rather than guess ids, this reads the mods' own data folders. Run it again when a mod is
added, then rebuild the book.
"""
import glob
import json
import sys
from pathlib import Path

CRAFTING = {"minecraft:crafting_shaped", "minecraft:crafting_shapeless"}

# Vanilla ships inside the game jar rather than a mod folder, so its recipes cannot be read here.
# These are the ones whose recipe id is simply the item id, which covers everything the guide wants to
# show. Anything less certain is left out: a wrong id would show an error page in the book.
VANILLA = """crafting_table furnace chest barrel torch soul_torch lantern iron_bars bucket shears
shield white_bed campfire ladder oak_boat minecart rail piston sticky_piston hopper dispenser dropper
observer repeater comparator redstone_torch lever note_block jukebox anvil cauldron brewing_stand
enchanting_table beacon smithing_table grindstone loom stonecutter composter bookshelf item_frame
flower_pot lead bow arrow fishing_rod crossbow stone_sword stone_pickaxe stone_axe stone_shovel
stone_hoe iron_sword iron_pickaxe iron_axe iron_shovel iron_hoe iron_helmet iron_chestplate
iron_leggings iron_boots diamond_sword diamond_pickaxe diamond_axe diamond_shovel diamond_hoe
diamond_helmet diamond_chestplate diamond_leggings diamond_boots golden_apple clock compass
spyglass lodestone target daylight_detector tripwire_hook trapped_chest smoker blast_furnace
cartography_table fletching_table scaffolding""".split()


def main():
    roots = sys.argv[1:] or ["."]
    index = {}
    for root in roots:
        for file in glob.glob(root + "/**/data/*/recipe*/**/*.json", recursive=True):
            parts = file.split("/data/")
            if len(parts) < 2:
                continue
            rest = parts[-1]
            namespace, after = rest.split("/", 1)
            if not after.startswith("recipe"):
                continue
            path = after.split("/", 1)[1][:-len(".json")]
            try:
                recipe = json.loads(Path(file).read_text(encoding="utf-8"))
            except (OSError, ValueError):
                continue
            if recipe.get("type") not in CRAFTING:
                continue
            result = recipe.get("result")
            item = result.get("id") if isinstance(result, dict) else result
            if not isinstance(item, str) or item in index:
                continue
            index[item] = f"{namespace}:{path}"
    for name in VANILLA:
        index.setdefault("minecraft:" + name, "minecraft:" + name)
    Path("tools/recipe_index.json").write_text(json.dumps(index, indent=0, sort_keys=True) + "\n",
                                               encoding="utf-8")
    print(f"recipe index: {len(index)} items")


if __name__ == "__main__":
    main()
