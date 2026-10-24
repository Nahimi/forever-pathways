"""Builds the Pathways GUI pixel art.

Usage:
    python3 tools/build_sprites.py [path/to/Tiles (Color)]

Writes:
  assets/foreverpathways/textures/gui/sprites/pathways/*.png (+ .mcmeta)
      Nine-slice frames on the GUI sprite atlas: window, inset, panel, banner, tabs, buttons, scrollbar,
      and the padlock. Every frame has chamfered (diagonal) corners.
  assets/foreverpathways/textures/gui/pathways/*.png
      Repeating textures drawn with one blit each: the map and panel backgrounds and the meander
      divider. These are recoloured from the patterned tile pack when its folder is given; without it
      the last committed versions are kept.

Everything is drawn pixel by pixel, so the art is reproducible and easy to tweak.
"""
import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/foreverpathways/textures/gui"
SPRITES = ASSETS / "sprites/pathways"
TEXTURES = ASSETS / "pathways"

# ---------------------------------------------------------------- palette (matches PathwaysScreen)
CLEAR = (0, 0, 0, 0)
OUT = (10, 8, 6, 255)          # outline
G0 = (36, 28, 20, 255)         # gold, darkest
G1 = (74, 58, 38, 255)
G2 = (138, 114, 80, 255)
G3 = (196, 165, 116, 255)      # GOLD
G4 = (232, 211, 160, 255)      # SEAL, highlight
S0 = (12, 10, 8, 255)          # recess
S1 = (23, 19, 15, 255)         # PANEL
S2 = (34, 27, 21, 255)
S3 = (46, 37, 28, 255)
WIN = (26, 21, 17, 255)        # window body
CREAM = (240, 230, 214, 255)


def chamfer_ring(w, h, x, y, chamfer):
    """Which concentric ring (0 outermost) a pixel sits on, for a box with cut corners. -1 if outside."""
    k = 0
    while True:
        c = max(0, chamfer - k)
        x0, y0, x1, y1 = k, k, w - 1 - k, h - 1 - k
        if x0 > x1 or y0 > y1:
            return k - 1
        inside = (x0 <= x <= x1 and y0 <= y <= y1
                  and (x - x0) + (y - y0) >= c and (x1 - x) + (y - y0) >= c
                  and (x - x0) + (y1 - y) >= c and (x1 - x) + (y1 - y) >= c)
        if not inside:
            return k - 1
        k += 1


def side(w, h, x, y):
    """'tl' for pixels nearer the top-left half (lit), 'br' for the bottom-right half (shaded)."""
    return "tl" if (x / max(1, w - 1)) + (y / max(1, h - 1)) < 1.0 else "br"


def frame(w, h, chamfer, rings, fill):
    """rings: list of (tl_colour, br_colour) from the outside in. Everything deeper is fill."""
    im = Image.new("RGBA", (w, h), CLEAR)
    px = im.load()
    for y in range(h):
        for x in range(w):
            r = chamfer_ring(w, h, x, y, chamfer)
            if r < 0:
                continue
            if r < len(rings):
                tl, br = rings[r]
                px[x, y] = tl if side(w, h, x, y) == "tl" else br
            else:
                px[x, y] = fill
    return im


def stud(im, cx, cy, light=G4, mid=G3, dark=G1):
    """A small diamond rivet."""
    px = im.load()
    for dx, dy, c in [(0, -1, light), (-1, 0, light), (0, 0, mid), (1, 0, dark), (0, 1, dark)]:
        x, y = cx + dx, cy + dy
        if 0 <= x < im.width and 0 <= y < im.height:
            px[x, y] = c


def save_sprite(name, im, border=None, scaling="nine_slice"):
    SPRITES.mkdir(parents=True, exist_ok=True)
    im.save(SPRITES / f"{name}.png")
    meta = SPRITES / f"{name}.png.mcmeta"
    if border is None:
        meta.unlink(missing_ok=True)
        return
    body = {"gui": {"scaling": {"type": scaling, "width": im.width, "height": im.height}}}
    if scaling == "nine_slice":
        body["gui"]["scaling"]["border"] = border
    meta.write_text(json.dumps(body, indent=2) + "\n")


def build_frames():
    # The window: a bevelled gold rim with cut corners, a stone band, a thin inner gold line,
    # and a stud on each diagonal.
    win = frame(32, 32, 6, [
        (OUT, OUT), (G4, G2), (G3, G1), (G2, G0), (OUT, OUT),
        (S3, S2), (S2, S2), (G1, G2), (S0, S0),
    ], WIN)
    for cx, cy in [(6, 6), (25, 6), (6, 25), (25, 25)]:
        stud(win, cx, cy, light=CREAM, mid=G4, dark=G2)
    save_sprite("window", win, border=10)

    # A recessed well (the map, the panel body): dark, shadowed on top and left.
    inset = frame(16, 16, 2, [(OUT, OUT), (S0, S3), (S0, S2)], S1)
    save_sprite("inset", inset, border=4)

    # A raised panel (the side panel, popups): gold rim, cut corners.
    panel = frame(24, 24, 3, [(OUT, OUT), (G3, G1), (G1, G0), (S2, S2)], S1)
    save_sprite("panel", panel, border=5)

    # The banner: the window's rim at a smaller scale, with a gem on each diagonal.
    banner = frame(32, 32, 5, [
        (OUT, OUT), (G4, G2), (G3, G1), (OUT, OUT), (S2, S2), (G1, G2), (S0, S0),
    ], S1)
    for cx, cy in [(5, 5), (26, 5), (5, 26), (26, 26)]:
        stud(banner, cx, cy, light=CREAM, mid=G4, dark=G2)
    save_sprite("banner", banner, border=8)

    # Tabs: cut top corners only, so they sit on the line below them.
    def tab(rim_tl, rim_br, fill):
        im = frame(14, 14, 2, [(OUT, OUT), (rim_tl, rim_br)], fill)
        px = im.load()
        for x in range(14):  # square bottom: rebuild the last two rows straight across
            for y in (12, 13):
                px[x, y] = fill if 1 <= x <= 12 else OUT
        return im
    save_sprite("tab", tab(S3, S2, S1), border=4)
    save_sprite("tab_selected", tab(G4, G2, S2), border=4)

    # Buttons: three states.
    save_sprite("button", frame(20, 16, 2, [(OUT, OUT), (G3, G1), (S3, S0)], S2), border=4)
    save_sprite("button_hover", frame(20, 16, 2, [(OUT, OUT), (G4, G2), (S3, S0)], S3), border=4)
    save_sprite("button_disabled", frame(20, 16, 2, [(OUT, OUT), (S3, S2), (S2, S0)], S1), border=4)

    # Scrollbar thumb.
    save_sprite("scroll", frame(4, 8, 1, [(G2, G1)], G1), border=2)

    # The padlock, drawn at its own size (11 by 13).
    lock = Image.new("RGBA", (11, 13), CLEAR)
    rows = [
        "...OOOOO...",
        "..OhhhhhO..",
        ".OhO...OgO.",
        ".OhO...OgO.",
        ".OhO...OgO.",
        "OOOOOOOOOOO",
        "OHHHHHHHHmO",
        "OHmmmmmmmdO",
        "OHmmmOmmmdO",
        "OHmmmOmmmdO",
        "OHmmmmmmmdO",
        "OmdddddddDO",
        "OOOOOOOOOOO",
    ]
    colours = {"O": OUT, "h": G4, "g": G2, "H": CREAM, "m": G3, "d": G2, "D": G1}
    px = lock.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colours:
                px[x, y] = colours[ch]
    save_sprite("padlock", lock)


def recolour(src, mapping):
    """Map the pack's three slate tones onto our own."""
    im = Image.open(src).convert("RGBA")
    out = Image.new("RGBA", im.size)
    sp, dp = im.load(), out.load()
    for y in range(im.height):
        for x in range(im.width):
            c = sp[x, y][:3]
            dp[x, y] = mapping.get(c, mapping["default"])
    return out


SLATE_DARK, SLATE_MID, SLATE_LIGHT = (38, 45, 54), (65, 76, 92), (106, 127, 151)


def build_textures(pack):
    TEXTURES.mkdir(parents=True, exist_ok=True)
    if pack is None:
        print("no tile pack given: keeping the committed background textures")
        return
    tile = lambda n: pack / f"tile_{n:04d}.png"
    # The map: a faint diagonal lattice, barely lighter than the ink.
    recolour(tile(16), {SLATE_MID: (20, 17, 14, 255), SLATE_DARK: (31, 25, 19, 255),
                        SLATE_LIGHT: (30, 24, 19, 255), "default": (20, 17, 14, 255)}).save(TEXTURES / "map_tile.png")
    # The panel: a single dot per tile.
    recolour(tile(45), {SLATE_MID: (23, 19, 15, 255), SLATE_DARK: (29, 24, 19, 255),
                        SLATE_LIGHT: (35, 29, 22, 255), "default": (23, 19, 15, 255)}).save(TEXTURES / "panel_tile.png")
    # The meander divider: rows 3 to 12 of the Greek key tile, in gold on the panel colour.
    key = recolour(tile(25), {SLATE_MID: (23, 19, 15, 255), SLATE_DARK: (110, 90, 62, 255),
                              SLATE_LIGHT: (196, 165, 116, 255), "default": (23, 19, 15, 255)})
    key.crop((0, 3, 16, 13)).save(TEXTURES / "divider.png")


def main():
    pack = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    build_frames()
    build_textures(pack)
    print("sprites written to", SPRITES)


if __name__ == "__main__":
    main()
