"""Checks the Classes plugin's pack art (class_tree_art.py) against what the plugin sends: the tree
font's glyphs and shifts are the ones TreeLook.java puts in the menu title, the backgrounds keep the
177 px advance SHIFT_BACK assumes and paint a slot exactly where TreeMenu puts something, and the
icon font has one glyph per AbilityIcons.java entry. The built join pack carries all of it.

Run from server/resourcepacks/: python -m unittest test_class_tree_art
"""

import json
import re
import tempfile
import unittest
import zipfile
from pathlib import Path

import build_server_pack
import class_tree_art as art

TREE_LOOK = art.ICONS_JAVA.with_name("TreeLook.java")


class ClassTreeArtTest(unittest.TestCase):
    def test_tree_font_uses_tree_looks_glyphs(self):
        java = TREE_LOOK.read_text(encoding="utf-8")
        self.assertIn(f'SHIFT_TO_EDGE = "{art.SHIFT_TO_EDGE}"', java)
        self.assertIn(f'SHIFT_BACK = "{art.SHIFT_BACK}"', java)
        self.assertIn("BACKGROUND = {" + ", ".join(f'"{c}"' for c in art.BACKGROUND_CHARS) + "}", java)
        self.assertIn('Key.key("classes", "tree")', java)
        font = art.tree_font()
        space = font["providers"][0]["advances"]
        # Title origin -> left edge (-8), background (+177), back to the origin.
        self.assertEqual(0, space[art.SHIFT_TO_EDGE] + art.WIDTH + 1 + space[art.SHIFT_BACK])
        bitmaps = font["providers"][1:]
        self.assertEqual([[c] for c in art.BACKGROUND_CHARS], [b["chars"] for b in bitmaps])
        self.assertTrue(all(b["height"] == art.HEIGHT and b["ascent"] == 13 for b in bitmaps))

    def test_backgrounds_keep_the_full_advance_and_a_clear_title_strip(self):
        for scroll in range(3):
            img = art.background(scroll)
            self.assertEqual((art.WIDTH, art.HEIGHT), img.size)
            last = max(x for x in range(art.WIDTH) for y in range(art.HEIGHT) if img.getpixel((x, y))[3])
            self.assertEqual(art.WIDTH - 1, last, "the font would trim the glyph and shorten its advance")
            self.assertTrue(all(img.getpixel((x, y))[3] == 0 for x in range(art.WIDTH) for y in range(art.GRID_Y)))

    def test_slots_are_painted_where_the_menu_puts_items(self):
        # TreeMenu: 33 nodes, 3 abilities + resource, 3 branch headers, 7 controls per view.
        kinds = [art.cell_kind(r, c) for r in range(15) for c in range(9)]
        self.assertEqual(30, kinds.count("node"))
        self.assertEqual(3, kinds.count("keystone"))
        self.assertEqual(3, kinds.count("ability"))
        self.assertEqual(3, kinds.count("branch"))
        for scroll in range(3):
            img = art.background(scroll)
            for view_row in range(art.VIEW_ROWS):
                for col in range(9):
                    kind = art.cell_kind(scroll * art.VIEW_ROWS + view_row, col)
                    rim = img.getpixel((art.GRID_X + col * art.CELL + 17, art.GRID_Y + view_row * art.CELL + 17))[:3]
                    framed = kind in ("node", "keystone", "ability", "resource", "branch")
                    self.assertEqual(framed, rim in (art.GOLD, art.BRIGHT_GOLD, art.SILVER, art.GOLD_SHADE),
                                     f"scroll {scroll} row {view_row} col {col} ({kind})")

    def test_icon_font_has_one_glyph_per_ability(self):
        icons = art.ability_icons()
        java = art.ICONS_JAVA.read_text(encoding="utf-8")
        self.assertEqual(len(re.findall(r"^\s*icon\(", java, re.M)), len(icons), "an icon( line the regex missed")
        self.assertEqual(42, len(icons))
        self.assertEqual(len(icons), len({c for _, c, _ in icons}))
        files = [p["file"] for p in art.icons_font()["providers"]]
        self.assertTrue(all(re.fullmatch(r"minecraft:(item|block)/[a-z_0-9]+\.png", f) for f in files), files)

    def test_join_pack_carries_the_art(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "pack.zip"
            build_server_pack.build(out)
            with zipfile.ZipFile(out) as z:
                self.assertEqual(art.tree_font(), json.loads(z.read("assets/classes/font/tree.json")))
                self.assertEqual(art.icons_font(), json.loads(z.read("assets/classes/font/icons.json")))
                for scroll in range(3):
                    self.assertIn(f"assets/classes/textures/gui/tree_{scroll}.png", z.namelist())


if __name__ == "__main__":
    unittest.main()
