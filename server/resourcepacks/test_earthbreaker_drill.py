"""Checks the Earthbreaker drill (earthbreaker_drill.py, Cosmetic token request #1) inside the built
join pack: cosmetic_1 on a netherite pickaxe picks the flat sprite in the inventory and the 3D drill
everywhere else, every other netherite pickaxe keeps the vanilla model, the models are what the
generator writes, its elements fit the 26.3 element range
and resolve their textures, the animated textures split into whole frames, the coil and lightning
glow, and the in-hand displays put the grip where item/handheld puts a pickaxe's handle.

Run from server/resourcepacks/: python -m unittest test_earthbreaker_drill
"""

import io
import json
import math
import tempfile
import unittest
import zipfile
from pathlib import Path

import build_server_pack
import earthbreaker_drill as eb
from PIL import Image

MODEL = "assets/claudesmp/models/item/earthbreaker_drill.json"
ICON = "assets/claudesmp/models/item/earthbreaker_drill_icon.json"

# The 26.3 client jar's item/handheld hand displays and the fist pixel (3.5, 12.5) of a sword under
# them (.for_bepy/item-display-solver/pose.py), as a model-space point.
HANDHELD = {
    "thirdperson_righthand": ([0, -90, 55], [0, 4.0, 0.5], 0.85),
    "firstperson_righthand": ([0, -90, 25], [1.13, 3.2, 1.13], 0.68),
}
SWORD_FIST = (3.5, 3.5, 8.5)


def rotation(rx, ry, rz):
    def rx_(a):
        c, s = math.cos(a), math.sin(a)
        return [[1, 0, 0], [0, c, -s], [0, s, c]]

    def ry_(a):
        c, s = math.cos(a), math.sin(a)
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]

    def rz_(a):
        c, s = math.cos(a), math.sin(a)
        return [[c, -s, 0], [s, c, 0], [0, 0, 1]]

    def mul(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]

    d = math.pi / 180
    return mul(mul(rx_(rx * d), ry_(ry * d)), rz_(rz * d))


def apply_display(rot, tr, sc, point):
    """ItemTransform.apply for the right hand: T(t/16) * Rxyz * S * T(-0.5), on a 0..16 point."""
    r = rotation(*rot)
    p = [sc * (v / 16 - 0.5) for v in point]
    return [sum(r[i][k] * p[k] for k in range(3)) + tr[i] / 16 for i in range(3)]


def tilted(y):
    """Where a point on the drill axis at local height y ends up after the -45 degree Z tilt."""
    a = math.radians(eb.TILT)
    dy = y - 8
    return (8 - math.sin(a) * dy, 8 + math.cos(a) * dy, 8)


class EarthbreakerDrillTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        tmp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(tmp.cleanup)
        out = Path(tmp.name) / "pack.zip"
        build_server_pack.build(out)
        with zipfile.ZipFile(out) as zf:
            cls.entries = {name: zf.read(name) for name in zf.namelist()}

    def json(self, name):
        self.assertIn(name, self.entries)
        return json.loads(self.entries[name])

    def texture(self, ref):
        namespace, path = ref.split(":", 1)
        name = f"assets/{namespace}/textures/{path}.png"
        if namespace == "minecraft":
            return name, None  # vanilla texture, from the client jar
        self.assertIn(name, self.entries)
        return name, Image.open(io.BytesIO(self.entries[name]))

    def test_cosmetic_1_picks_the_drill_and_other_pickaxes_stay_vanilla(self):
        definition = self.json("assets/minecraft/items/netherite_pickaxe.json")["model"]
        self.assertEqual(definition["property"], "minecraft:custom_model_data")
        self.assertEqual(definition["index"], 0)
        looks = {c["when"]: c["model"] for c in definition["cases"]}
        # The flat sprite in the inventory ("gui" is the 26.3 ItemDisplayContext name), the 3D
        # drill in every other context.
        drill = looks["cosmetic_1"]
        self.assertEqual(drill["property"], "minecraft:display_context")
        self.assertEqual({c["when"]: c["model"]["model"] for c in drill["cases"]},
                         {"gui": "claudesmp:item/earthbreaker_drill_icon"})
        self.assertEqual(drill["fallback"]["model"], "claudesmp:item/earthbreaker_drill")
        # The 26.3 client jar's own assets/minecraft/items/netherite_pickaxe.json model.
        self.assertEqual(definition["fallback"], {"type": "minecraft:model", "model": "minecraft:item/netherite_pickaxe"})

    def test_models_are_what_the_generator_writes(self):
        self.assertEqual(self.json(MODEL), eb.model())
        self.assertEqual(self.json(ICON), eb.icon_model())

    def test_icon_is_a_flat_sprite_whose_sparks_flicker(self):
        _, png = self.texture(self.json(ICON)["textures"]["layer0"])
        self.assertEqual(png.size, (16, 16 * eb.ICON_FRAMES))
        frames = [png.convert("RGBA").crop((0, 16 * f, 16, 16 * f + 16)).tobytes() for f in range(eb.ICON_FRAMES)]
        self.assertEqual(len(set(frames)), eb.ICON_FRAMES)
        self.assertIn("assets/claudesmp/textures/item/earthbreaker_drill_icon.png.mcmeta", self.entries)

    def test_elements_fit_the_range_and_every_face_texture_resolves(self):
        model = self.json(MODEL)
        for el in model["elements"]:
            for lo, hi in zip(el["from"], el["to"]):
                self.assertTrue(-16 <= lo <= hi <= 32, el)
            for face in el["faces"].values():
                self.assertIn(face["texture"][1:], model["textures"])
        for ref in model["textures"].values():
            self.texture(ref)

    def test_animated_textures_split_into_whole_frames(self):
        for name, frame_w, frame_h, frames in (("earthbreaker_coil", 16, 16, eb.COIL_FRAMES),
                                                ("earthbreaker_arcs", 32, 48, eb.ARC_FRAMES)):
            png = f"assets/claudesmp/textures/item/{name}.png"
            meta = json.loads(self.entries[png + ".mcmeta"])["animation"]
            self.assertEqual(meta.get("width", frame_w), frame_w, name)
            self.assertEqual(meta.get("height", meta.get("width", frame_w)), frame_h, name)
            size = Image.open(io.BytesIO(self.entries[png])).size
            self.assertEqual(size, (frame_w, frame_h * frames), name)

    def test_the_coil_and_the_lightning_glow_and_nothing_else_does(self):
        model = self.json(MODEL)
        for el in model["elements"]:
            textures = {f["texture"] for f in el["faces"].values()}
            glows = textures & {"#coil", "#arcs"}
            self.assertEqual(el.get("light_emission", 0), 15 if glows else 0, el)

    def test_the_grip_lands_where_handheld_puts_the_handle(self):
        display = self.json(MODEL)["display"]
        grip = tilted((eb.GRIP[0] + eb.GRIP[1]) / 2)
        for context, (rot, tr, sc) in HANDHELD.items():
            ours = display[context]
            self.assertEqual(ours["rotation"], rot, context)
            fist = apply_display(rot, tr, sc, SWORD_FIST)
            held = apply_display(ours["rotation"], ours["translation"], ours["scale"][0], grip)
            # Within a quarter pixel of the player's fist.
            self.assertLess(max(abs(a - b) for a, b in zip(fist, held)), 0.25 / 16, context)

    def test_lefthand_displays_mirror_the_righthand_ones(self):
        display = self.json(MODEL)["display"]
        for side in ("thirdperson", "firstperson"):
            right, left = display[f"{side}_righthand"], display[f"{side}_lefthand"]
            self.assertEqual(left["rotation"], [right["rotation"][0], -right["rotation"][1], -right["rotation"][2]])
            self.assertEqual(left["translation"], right["translation"])
            self.assertEqual(left["scale"], right["scale"])


if __name__ == "__main__":
    unittest.main()
