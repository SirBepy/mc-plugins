"""Generates the Classes plugin's pack art: the talent tree menu's painted background (config
tree-background, classes TreeLook) and the held-sneak legend's ability icons (config ability-icons,
classes AbilityIcons).

The tree menu is a 6-row chest. Its title starts with SHIFT_TO_EDGE (U+F801), one background glyph
per scroll position (U+E000 to U+E002) and SHIFT_BACK (U+F802), all in the classes:tree font. A
title starts drawing at x 8 and y 6 of the 176 px wide chest, so -8 puts the pen on the left edge,
and ascent 13 puts the glyph's top row on the chest's top row. Each background is 176 x 125: the
title strip and the 9 x 6 slot grid (x 7..168, y 17..124). The player inventory below is left to
vanilla's own chest texture, and the title strip is transparent so vanilla's shows through. The
font measures a glyph's width up to its last non-transparent column, so column 175 repeats vanilla's
black outline there to keep the advance at 177 (176 + 1); SHIFT_BACK's -169 then returns the pen to
the title's start.

The layout matches classes TreeMenu: canvas rows 0 (abilities and resource) and 1 (branch headers),
tiers on rows 2, 5, 8, 11 and 14, each branch owning 3 columns with 2, 3, 3, 2 and 1 nodes per tier
(2 nodes sit in the outer columns, 1 in the middle), connectors in each branch's middle column on
the 2 rows under a tier, and the controls on the chest's bottom row (slots 45, 47, 48, 49, 51, 52,
53). Every class has the same shape, so one image per scroll position serves all seven.

The ability icons need no art: classes:icons maps each ability's code point straight to a vanilla
item or block texture, read from the icon(...) lines in AbilityIcons.java.

Writes into claudesmp-items/assets/classes/:
    font/tree.json, font/icons.json
    textures/gui/tree_0.png .. tree_2.png

    python server/resourcepacks/class_tree_art.py
"""

import json
import re
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[1]
ASSETS = ROOT / "claudesmp-items" / "assets" / "classes"
ICONS_JAVA = REPO / "classes" / "src" / "main" / "java" / "dev" / "sirbepy" / "mc" / "classes" / "talent" / "AbilityIcons.java"

WIDTH, HEIGHT = 176, 125
GRID_X, GRID_Y, CELL = 7, 17, 18
VIEW_ROWS = 5
TIER_ROW = [None, 2, 5, 8, 11, 14]
# Node columns within a branch per tier (TreeMenu.layout's offsets for 2, 3 and 1 nodes).
TIER_COLUMNS = {1: (0, 2), 2: (0, 1, 2), 3: (0, 1, 2), 4: (0, 2), 5: (1,)}
ABILITY_COLUMNS = (1, 3, 5)
RESOURCE_COLUMN = 7
CONTROL_COLUMNS = (0, 2, 3, 4, 6, 7, 8)
# The empty control slot between POINTS (49) and BINDS (51) shows which scroll position this is.
PIPS_COLUMN = 5

SHIFT_TO_EDGE, SHIFT_BACK = "", ""
BACKGROUND_CHARS = ["", "", ""]

# Dark walnut panel, gold trim.
PANEL = (44, 36, 30)
PANEL_EDGE = (24, 19, 16)
DIVIDER = (31, 25, 21)
GOLD = (201, 156, 72)
GOLD_SHADE = (104, 78, 38)
BRIGHT_GOLD = (246, 204, 98)
SILVER = (172, 174, 184)
SLOT_IN = (66, 55, 46)
SLOT_DARK = (22, 18, 15)
SLOT_LIGHT = (104, 88, 70)
PIP_OFF = (92, 76, 58)
VANILLA_OUTLINE = (0, 0, 0)

ICON_LINE = re.compile(r'icon\("([^"]+)", 0x([0-9A-F]{4}), "([a-z_/0-9]+)"\);')


def ability_icons():
    """(name, character, texture) for every icon(...) line in AbilityIcons.java, in order."""
    text = ICONS_JAVA.read_text(encoding="utf-8")
    return [(name, chr(int(code, 16)), texture) for name, code, texture in ICON_LINE.findall(text)]


def cell_kind(row, col):
    """What canvas cell (row, col) holds: node, keystone, ability, resource, branch, link or None."""
    branch, offset = divmod(col, 3)
    if row == 0:
        return "ability" if col in ABILITY_COLUMNS else "resource" if col == RESOURCE_COLUMN else None
    if row == 1:
        return "branch" if offset == 1 else None
    for tier in range(1, 6):
        if row == TIER_ROW[tier] and offset in TIER_COLUMNS[tier]:
            return "keystone" if tier == 5 else "node"
    if offset != 1:
        return None
    for tier in range(1, 5):
        if row in (TIER_ROW[tier] + 1, TIER_ROW[tier] + 2):
            return "link"
    # Tier rows whose middle column has no node: the line runs through and splits to both sides.
    if row in (TIER_ROW[1], TIER_ROW[4]):
        return "fork"
    return None


def fill(img, x0, y0, x1, y1, color):
    """Fills the inclusive rectangle."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), color + (255,))


def grain(x, y):
    """A fixed -3..3 brightness offset per texel, so the panel reads as wood, not flat paint."""
    h = (x * 73856093) ^ (y * 19349663)
    return (h >> 3) % 7 - 3


def slot(img, x, y, rim, inner=SLOT_IN):
    """An 18 x 18 framed slot at (x, y): coloured rim, then a dark-top-left bevel like vanilla's."""
    fill(img, x, y, x + 17, y + 17, rim)
    fill(img, x + 1, y + 1, x + 16, y + 16, SLOT_DARK)
    fill(img, x + 2, y + 2, x + 16, y + 16, SLOT_LIGHT)
    fill(img, x + 2, y + 2, x + 15, y + 15, inner)


def vertical(img, x, y0, y1):
    fill(img, x + 8, y0, x + 9, y1, GOLD)
    fill(img, x + 10, y0, x + 10, y1, GOLD_SHADE)


def horizontal(img, x0, x1, y):
    fill(img, x0, y + 8, x1, y + 9, GOLD)
    fill(img, x0, y + 10, x1, y + 10, GOLD_SHADE)


def background(scroll):
    """The 176 x 125 background for scroll position 0, 1 or 2 (canvas rows 5 * scroll and on)."""
    img = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    x_end, y_end = GRID_X + 9 * CELL - 1, GRID_Y + 6 * CELL - 1
    for y in range(GRID_Y, y_end + 1):
        for x in range(GRID_X, x_end + 1):
            g = grain(x, y)
            img.putpixel((x, y), tuple(c + g for c in PANEL) + (255,))
        img.putpixel((WIDTH - 1, y), VANILLA_OUTLINE + (255,))
    # Vanilla's slot grid starts with a dark top-left edge, so the panel keeps that edge.
    fill(img, GRID_X, GRID_Y, x_end, GRID_Y, PANEL_EDGE)
    fill(img, GRID_X, GRID_Y, GRID_X, y_end, PANEL_EDGE)

    top = scroll * VIEW_ROWS
    for view_row in range(VIEW_ROWS):
        row = top + view_row
        y = GRID_Y + view_row * CELL
        # Faint lines between the branches, below the header row.
        if row >= 1:
            for col in (3, 6):
                fill(img, GRID_X + col * CELL, y, GRID_X + col * CELL, y + CELL - 1, DIVIDER)
        for col in range(9):
            x = GRID_X + col * CELL
            kind = cell_kind(row, col)
            if kind == "link":
                vertical(img, x, y, y + CELL - 1)
            elif kind == "fork":
                vertical(img, x, y, y + CELL - 1 if row == TIER_ROW[4] else y + 9)
                horizontal(img, x, x + CELL - 1, y)
    # Slots last, so the lines run up to their rims.
    for view_row in range(VIEW_ROWS):
        row = top + view_row
        y = GRID_Y + view_row * CELL
        for col in range(9):
            x = GRID_X + col * CELL
            kind = cell_kind(row, col)
            if kind == "node":
                slot(img, x, y, GOLD)
            elif kind == "keystone":
                slot(img, x, y, BRIGHT_GOLD, inner=(84, 66, 40))
            elif kind in ("ability", "resource"):
                slot(img, x, y, SILVER)
            elif kind == "branch":
                slot(img, x, y, GOLD_SHADE)

    # The controls row under a gold rule.
    y = GRID_Y + VIEW_ROWS * CELL
    fill(img, GRID_X, y, x_end, y_end, PANEL_EDGE)
    fill(img, GRID_X, y, x_end, y, GOLD)
    for col in CONTROL_COLUMNS:
        slot(img, GRID_X + col * CELL, y, SLOT_DARK)
    px = GRID_X + PIPS_COLUMN * CELL
    for i in range(3):
        color = BRIGHT_GOLD if i == scroll else PIP_OFF
        fill(img, px + 3 + i * 5, y + 7, px + 5 + i * 5, y + 9, color)
    return img


def tree_font():
    providers = [{"type": "space", "advances": {SHIFT_TO_EDGE: -8, SHIFT_BACK: -(WIDTH + 1 - 8)}}]
    for scroll, char in enumerate(BACKGROUND_CHARS):
        providers.append({"type": "bitmap", "file": f"classes:gui/tree_{scroll}.png",
                          "height": HEIGHT, "ascent": 13, "chars": [char]})
    return {"providers": providers}


def icons_font():
    # Height 9 with ascent 8: one texel above the caps, so a 16 px texture reads at text size.
    return {"providers": [{"type": "bitmap", "file": f"minecraft:{texture}.png", "height": 9, "ascent": 8,
                           "chars": [char]} for _, char, texture in ability_icons()]}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=True) + "\n", encoding="utf-8", newline="\n")


def main():
    gui = ASSETS / "textures" / "gui"
    gui.mkdir(parents=True, exist_ok=True)
    for scroll in range(len(BACKGROUND_CHARS)):
        background(scroll).save(gui / f"tree_{scroll}.png")
    write_json(ASSETS / "font" / "tree.json", tree_font())
    write_json(ASSETS / "font" / "icons.json", icons_font())
    print(f"wrote {len(BACKGROUND_CHARS)} tree backgrounds and {len(ability_icons())} ability icons")


if __name__ == "__main__":
    main()
