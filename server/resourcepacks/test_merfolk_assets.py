"""Checks the Merfolk wish assets (merfolk_assets.py) inside the built join pack: the repainted
swamp villager type textures are vanilla-sized and keep the base skin's eyes visible, the
merfolk_head item resolves to a model that fits the item-model element range and carries the
identity "head" display the CustomHeadLayer derivation in merfolk_assets.py relies on, and the
kelp_bed / kelp_bed_placed items resolve and the placed model matches the plugin's geometry
contract (x 0..16, z -8..24, lowest y 0).

Run from server/resourcepacks/: python -m unittest test_merfolk_assets
"""

import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

import build_server_pack
import merfolk_assets as ma
from PIL import Image

ROOT = build_server_pack.ROOT


def asset(ref, kind, ext):
    namespace, path = ref.split(":", 1)
    return f"assets/{namespace}/{kind}/{path}.{ext}"


class BuiltPackTest(unittest.TestCase):
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

    def png(self, name):
        self.assertIn(name, self.entries)
        return Image.open(io.BytesIO(self.entries[name]))

    # --- villager type textures --------------------------------------------------------------

    def test_swamp_type_textures_are_vanilla_sized(self):
        # Same dimensions as the 26.3 client jar's own type/swamp.png and baby/swamp.png (dumped
        # this session): the client's VillagerModel/BabyVillagerModel box-UV layout assumes these
        # exact sizes, so a mismatch would scramble every face onto the wrong part of the model.
        adult = self.png("assets/minecraft/textures/entity/villager/type/swamp.png")
        self.assertEqual(adult.size, (64, 64))
        baby = self.png("assets/minecraft/textures/entity/villager/baby/swamp.png")
        self.assertEqual(baby.size, (64, 64))

    def test_swamp_type_textures_leave_the_shared_eyes_visible(self):
        # The base villager.png/villager_baby.png (untouched, shared by every type) paints its
        # green-eyed face at these exact pixels (sampled from the 26.3 jar this session); every
        # type texture, including this one, must leave them transparent so the SAME eyes show
        # through on a merfolk villager, same as on every other type.
        for name, holes in (
            ("assets/minecraft/textures/entity/villager/type/swamp.png", ma.EYE_HOLES_ADULT),
            ("assets/minecraft/textures/entity/villager/baby/swamp.png", ma.EYE_HOLES_BABY),
        ):
            img = self.png(name).convert("RGBA")
            px = img.load()
            for x, y in holes:
                self.assertEqual(px[x, y][3], 0, f"{name} eye pixel {(x, y)} should stay transparent")

    def test_swamp_type_textures_are_mostly_opaque_around_the_eyes(self):
        # Unlike vanilla's own swamp hood (a big transparent face opening), merfolk paint the whole
        # head/face/nose opaque (Joe's brief: "paint ... scaly skin over the head, face, nose and
        # hands"), punching out only the eye pixels themselves.
        img = self.png("assets/minecraft/textures/entity/villager/type/swamp.png").convert("RGBA")
        px = img.load()
        for x, y in ((8, 14), (11, 14), (12, 14), (15, 14)):
            self.assertEqual(px[x, y][3], 255, f"adult pixel {(x, y)} next to an eye should be opaque")

    def test_swamp_type_textures_have_no_mcmeta(self):
        # Checked against the 26.3 client jar directly this session: neither vanilla type/swamp.png
        # nor baby/swamp.png carries a .mcmeta (no hat blink/flap animation), so this pack doesn't
        # ship one either.
        self.assertNotIn("assets/minecraft/textures/entity/villager/type/swamp.png.mcmeta", self.entries)
        self.assertNotIn("assets/minecraft/textures/entity/villager/baby/swamp.png.mcmeta", self.entries)

    def test_other_villager_types_are_not_shipped(self):
        # Only the swamp type is reskinned; every other type/baby texture stays vanilla (not
        # present in this pack at all).
        shipped = [n for n in self.entries if n.startswith("assets/minecraft/textures/entity/villager/")]
        self.assertEqual(sorted(shipped), sorted([
            "assets/minecraft/textures/entity/villager/type/swamp.png",
            "assets/minecraft/textures/entity/villager/baby/swamp.png",
        ]))

    # --- merfolk_head item ---------------------------------------------------------------------

    def test_merfolk_head_item_resolves_to_its_model(self):
        definition = self.json("assets/claudesmp/items/merfolk_head.json")["model"]
        self.assertEqual(definition["type"], "minecraft:model")
        model = self.json(asset(definition["model"], "models", "json"))
        self.assertIn(asset(model["textures"]["t"], "textures", "png"), self.entries)

    def test_merfolk_head_model_stays_in_the_item_model_range(self):
        model = self.json("assets/claudesmp/models/item/merfolk_head.json")
        self.assertGreater(len(model["elements"]), 0)
        for el in model["elements"]:
            for v in el["from"] + el["to"]:
                self.assertGreaterEqual(v, -16, el)
                self.assertLessEqual(v, 32, el)

    def test_merfolk_head_model_carries_the_identity_head_display(self):
        # Numerically equivalent to the engine's own NO_TRANSFORM (see merfolk_assets.py's
        # CustomHeadLayer derivation), written explicitly so the fit math is on record in the file
        # that uses it -- same convention as dragonscale_helmet_model.py's worn helm.
        model = self.json("assets/claudesmp/models/item/merfolk_head.json")
        self.assertEqual(model["display"]["head"],
                          {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]})

    def test_merfolk_head_elements_sit_outside_the_villager_head_box_front_face(self):
        # The villager head box (javap'd) is x -4..4, y -10..0, z -4..4 in the design frame, with
        # the face on z=-4; every ear/crest/kelp-strand element must stay clear of that whole front
        # face plane so it can never cover the eyes. Checked on the PRE-conversion design-frame
        # elements (merfolk_assets.merfolk_head_elements()), which is the frame that fact is true
        # in; the item-space model file is a fixed, derived function of these.
        for el in ma.merfolk_head_elements():
            f, t = el["from"], el["to"]
            z_lo, z_hi = min(f[2], t[2]), max(f[2], t[2])
            self.assertGreater(z_hi, -4, "an element entirely in front of the face plane")

    # --- kelp bed --------------------------------------------------------------------------------

    def test_kelp_bed_icon_resolves_to_a_flat_16x16_sprite(self):
        definition = self.json("assets/claudesmp/items/kelp_bed.json")["model"]
        model = self.json(asset(definition["model"], "models", "json"))
        self.assertEqual(model["parent"], "minecraft:item/generated")
        png = self.png(asset(model["textures"]["layer0"], "textures", "png"))
        self.assertEqual(png.size, (16, 16))

    def test_kelp_bed_placed_resolves_to_its_model_and_texture(self):
        definition = self.json("assets/claudesmp/items/kelp_bed_placed.json")["model"]
        model = self.json(asset(definition["model"], "models", "json"))
        self.assertIn(asset(model["textures"]["t"], "textures", "png"), self.entries)
        self.assertNotIn("display", model, "transform NONE needs no display block")

    def test_kelp_bed_placed_matches_the_plugins_geometry_contract(self):
        # The merfolk plugin spawns the item display at item_display_transform NONE and assumes:
        # 1 block wide (x 0..16), 2 blocks long on Z (-8..24, head end z<0, foot end z>16), floor
        # at y=0, mattress height roughly a vanilla bed's 9px.
        model = self.json("assets/claudesmp/models/item/kelp_bed_placed.json")
        xs = [v for e in model["elements"] for v in (e["from"][0], e["to"][0])]
        ys = [v for e in model["elements"] for v in (e["from"][1], e["to"][1])]
        zs = [v for e in model["elements"] for v in (e["from"][2], e["to"][2])]
        self.assertEqual((min(xs), max(xs)), (0, 16))
        self.assertEqual(min(ys), 0)
        self.assertLessEqual(max(ys), 9)
        self.assertEqual((min(zs), max(zs)), (-8, 24))
        for el in model["elements"]:
            for v in el["from"] + el["to"]:
                self.assertGreaterEqual(v, -16, el)
                self.assertLessEqual(v, 32, el)

    def test_kelp_bed_placed_head_end_is_the_negative_z_half(self):
        # "Head end (pillow) is the z -8..0 half (-Z side)": the pillow element must sit there.
        model = self.json("assets/claudesmp/models/item/kelp_bed_placed.json")
        pillow = next(e for e in model["elements"] if e["from"][1] > 0)  # raised above the mat slab
        self.assertLessEqual(pillow["to"][2], 0)
        self.assertGreaterEqual(pillow["from"][2], -8)


if __name__ == "__main__":
    unittest.main()
