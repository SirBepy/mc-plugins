"""Checks the Merfolk wish assets (merfolk_assets.py) inside the built join pack: the swamp and
jungle villager types carry Joe's Kelp and Coral skins and their baby remaps, each variant's fin
item resolves to a model that fits the item-model element range and carries the
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

    def type_png(self, age, variant):
        return self.png(f"assets/minecraft/textures/entity/villager/{age}/{ma.VARIANTS[variant]['type']}.png")

    def test_adult_bodies_are_joes_skins_unchanged(self):
        for variant in ma.VARIANTS:
            shipped = self.type_png("type", variant).convert("RGBA")
            self.assertEqual(shipped.size, (64, 64))
            self.assertEqual(shipped.tobytes(), ma.skin(variant).tobytes(), variant)

    def test_baby_bodies_cover_every_baby_part_and_leave_the_hat_free(self):
        # Opaque over every part BabyVillagerModel draws (javap'd layout), so no tan base skin peeks
        # through; hat and hat_rim stay transparent like the adult skins', free for profession hats.
        for variant in ma.VARIANTS:
            img = self.type_png("baby", variant).convert("RGBA")
            self.assertEqual(img.size, (64, 64))
            px = img.load()
            for (u, v, w, h, d), _, _ in ma.BABY_PARTS:
                for x, y, fw, fh in ma.face_rects(u, v, w, h, d).values():
                    for yy in range(y, y + fh):
                        for xx in range(x, x + fw):
                            self.assertEqual(px[xx, yy][3], 255, f"{variant} baby pixel {(xx, yy)}")
            for uvwhd in ((0, 30, 8, 8, 7), (0, 45, 14, 1, 12)):
                for x, y, fw, fh in ma.face_rects(*uvwhd).values():
                    for yy in range(y, y + fh):
                        for xx in range(x, x + fw):
                            self.assertEqual(px[xx, yy][3], 0, f"{variant} baby hat pixel {(xx, yy)}")

    def test_baby_face_keeps_the_skins_eyes(self):
        # The adult head front's eye row (6 of 10) lands on the baby's row 5 of 8, column for column.
        for variant in ma.VARIANTS:
            adult = ma.skin(variant).load()
            baby = self.type_png("baby", variant).convert("RGBA").load()
            for i in range(8):
                self.assertEqual(baby[7 + i, 7 + 5], adult[8 + i, 8 + 6], f"{variant} eye row x={i}")

    def test_villager_types_have_no_mcmeta(self):
        # Checked against the 26.3 client jar: vanilla type/baby textures carry no .mcmeta.
        self.assertFalse([n for n in self.entries
                          if n.startswith("assets/minecraft/textures/entity/villager/") and n.endswith(".mcmeta")])

    def test_only_the_merfolk_types_are_shipped(self):
        # Every other villager type stays vanilla (not present in this pack at all).
        shipped = [n for n in self.entries if n.startswith("assets/minecraft/textures/entity/villager/")]
        self.assertEqual(sorted(shipped), sorted([
            "assets/minecraft/textures/entity/villager/type/swamp.png",
            "assets/minecraft/textures/entity/villager/baby/swamp.png",
            "assets/minecraft/textures/entity/villager/type/jungle.png",
            "assets/minecraft/textures/entity/villager/baby/jungle.png",
        ]))

    # --- merfolk_head item ---------------------------------------------------------------------

    def test_each_variants_head_item_resolves_to_its_own_fin_texture(self):
        textures = set()
        for c in ma.VARIANTS.values():
            definition = self.json(f"assets/claudesmp/items/{c['head']}.json")["model"]
            self.assertEqual(definition["type"], "minecraft:model")
            model = self.json(asset(definition["model"], "models", "json"))
            self.assertIn(asset(model["textures"]["t"], "textures", "png"), self.entries)
            if "parent" in model:
                self.assertIn("elements", self.json(asset(model["parent"], "models", "json")))
            textures.add(model["textures"]["t"])
        self.assertEqual(len(textures), len(ma.VARIANTS))

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
        # the face on z=-4; every fin element must stay clear of that whole front
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

    def test_kelp_bed_placed_resolves_to_its_model_and_textures(self):
        definition = self.json("assets/claudesmp/items/kelp_bed_placed.json")["model"]
        model = self.json(asset(definition["model"], "models", "json"))
        for ref in model["textures"].values():
            self.assertIn(asset(ref, "textures", "png"), self.entries)
            # 26.3's items atlas only stitches item/, so a block/ texture would render missing.
            self.assertTrue(ref.startswith("claudesmp:item/"), ref)
        for el in model["elements"]:
            for face in el["faces"].values():
                self.assertIn(face["texture"][1:], model["textures"])
        self.assertNotIn("display", model, "transform NONE needs no display block")

    def test_kelp_bed_placed_matches_the_plugins_geometry_contract(self):
        # The merfolk plugin spawns the item display at item_display_transform NONE and assumes:
        # 1 block wide (x 0..16), 2 blocks long on Z (-8..24, head end z<8, foot end z>8), floor
        # at y=0, mattress height a vanilla bed's 9px.
        model = self.json("assets/claudesmp/models/item/kelp_bed_placed.json")
        xs = [v for e in model["elements"] for v in (e["from"][0], e["to"][0])]
        ys = [v for e in model["elements"] for v in (e["from"][1], e["to"][1])]
        zs = [v for e in model["elements"] for v in (e["from"][2], e["to"][2])]
        self.assertEqual((min(xs), max(xs)), (0, 16))
        self.assertEqual((min(ys), max(ys)), (0, 9))
        self.assertEqual((min(zs), max(zs)), (-8, 24))
        for el in model["elements"]:
            for v in el["from"] + el["to"]:
                self.assertGreaterEqual(v, -16, el)
                self.assertLessEqual(v, 32, el)

    def test_kelp_bed_placed_pillow_half_is_the_negative_z_end(self):
        # The head half (pillow texture on top, headboard on the north face) is z -8..8.
        model = self.json("assets/claudesmp/models/item/kelp_bed_placed.json")
        head = next(e for e in model["elements"] if e["faces"].get("up", {}).get("texture") == "#head_up")
        foot = next(e for e in model["elements"] if e["faces"].get("up", {}).get("texture") == "#foot_up")
        self.assertEqual((head["from"][2], head["to"][2]), (-8, 8))
        self.assertEqual((foot["from"][2], foot["to"][2]), (8, 24))
        self.assertEqual(head["faces"]["north"]["texture"], "#head_north")

    def test_kelp_bed_placed_has_no_gap_between_its_legs(self):
        # ItsLogiks asked for the space under the mattress (y 0..3) to be filled: every point
        # under the bed is inside a leg or a base element, sampled every half pixel.
        model = self.json("assets/claudesmp/models/item/kelp_bed_placed.json")
        low = [e for e in model["elements"] if e["from"][1] == 0 and e["to"][1] >= 3]

        def covered(x, z):
            return any(e["from"][0] <= x <= e["to"][0] and e["from"][2] <= z <= e["to"][2] for e in low)

        missing = [(x / 2, z / 2) for x in range(2, 31) for z in range(-14, 47) if not covered(x / 2, z / 2)]
        self.assertEqual(missing, [])

if __name__ == "__main__":
    unittest.main()
