"""Checks Thessa the Chronicler's skin (chronicler_skin.py, the pack texture claudesmp:entity/chronicler):
64x64 RGBA, an opaque face, nothing painted in the texels a slim (3px) arm layout leaves unused, and
the committed PNG matching what the script draws.

Run from server/resourcepacks/: python -m unittest test_chronicler_skin
"""

import tempfile
import unittest
from pathlib import Path

import chronicler_skin as cs
from guild_skins import LARM, RARM, face_rects
from PIL import Image


def slim_arm_gaps(part):
    """Texels inside the arm's 4-wide UV footprint that a 3px-wide arm never uses."""
    gaps = []
    for (u, v), (w, h, d) in ((part[0], part[2]), (part[1], part[2])):
        used = set()
        for x, y, fw, fh in face_rects(u, v, 3, h, d).values():
            used |= {(x + i, y + j) for i in range(fw) for j in range(fh)}
        footprint = {(x, y) for x in range(u, u + 2 * d + 2 * w) for y in range(v, v + d + h)}
        gaps += sorted(footprint - used)
    return gaps


class ChroniclerSkinTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        tmp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(tmp.cleanup)
        _, path = cs.write(Path(tmp.name))
        cls.generated = Image.open(path)
        cls.generated.load()

    def test_is_64x64_rgba(self):
        self.assertEqual(self.generated.size, (64, 64))
        self.assertEqual(self.generated.mode, "RGBA")

    def test_head_front_face_is_opaque(self):
        # A hole in the base head face shows the world through her face.
        face = self.generated.crop((8, 8, 16, 16))
        self.assertEqual(face.getextrema()[3], (255, 255))

    def test_slim_arm_leaves_the_fourth_pixel_column_transparent(self):
        for part in (RARM, LARM):
            gaps = slim_arm_gaps(part)
            self.assertTrue(gaps, part)
            for xy in gaps:
                self.assertEqual(self.generated.getpixel(xy)[3], 0, (part, xy))

    def test_slim_arm_faces_are_painted(self):
        # The 3px front of each arm (rows 20..31 / 52..63) must be fully opaque.
        for part in (RARM, LARM):
            x, y, w, h = face_rects(*part[0], 3, 12, 4)["front"]
            region = self.generated.crop((x, y, x + w, y + h))
            self.assertEqual(region.getextrema()[3], (255, 255), part)

    def test_committed_pack_texture_matches_the_script(self):
        committed = Image.open(cs.OUT / "chronicler.png").convert("RGBA")
        self.assertEqual(committed.tobytes(), self.generated.convert("RGBA").tobytes())


if __name__ == "__main__":
    unittest.main()
