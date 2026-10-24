"""Rebuild preview/data.js from tree.json and an item list.

Usage:
    python tools/build_preview.py [tree.json] [items.json]

Defaults to the tree inside the mod and preview/items.json. After /pathways rebuild
on the server you can point it at config/foreverpathways/items.json for a fresh
item list. Then open preview/index.html in a browser.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_tree import load, resolve  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent


def main():
    tree, items = load(sys.argv[1:])
    claims, owner, ignored, warnings = resolve(tree, items)
    slim = dict(tree)
    slim["nodes"] = [{k: v for k, v in n.items() if k != "locks"} for n in tree["nodes"]]
    out = ROOT / "preview" / "data.js"
    out.write_text(
        "window.PATHWAY_TREE = " + json.dumps(slim, separators=(",", ":")) + ";\n"
        + "window.PATHWAY_ITEMS = " + json.dumps(claims, separators=(",", ":")) + ";\n",
        encoding="utf-8",
    )
    print(f"wrote {out} ({len(tree['nodes'])} nodes, {len(owner)} locked items)")
    for w in warnings:
        print("warn ", w)


if __name__ == "__main__":
    main()
