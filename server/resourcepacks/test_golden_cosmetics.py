"""Checks Cosmetic token requests #2 (golden_wings.py) and #3 (golden_halo.py), dvolic21, inside the
built join pack: the item definitions pick the new looks only for cosmetic_2/cosmetic_3 and keep
every other elytra and netherite helmet (dragon helmets included) as before, the wings asset paints
more of the elytra box than vanilla and never the player's cape, the halo floats clear above the
head and glows, the animated textures split into whole frames, and every worn look the Market
jar's cosmetic-looks.yml names exists here.

Run from server/resourcepacks/: python -m unittest test_golden_cosmetics
"""

import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

import build_server_pack
import golden_halo as gh
import golden_wings as gw
from PIL import Image
from test_build_server_pack import _worn_head_item

ROOT = Path(__file__).resolve().parents[2]
LOOKS = ROOT / "market" / "src" / "main" / "resources" / "cosmetic-looks.yml"

# The 26.3 client jar's assets/minecraft/items/elytra.json.
VANILLA_ELYTRA = {
    "type": "minecraft:condition",
    "on_false": {"type": "minecraft:model", "model": "minecraft:item/elytra"},
    "on_true": {"type": "minecraft:model", "model": "minecraft:item/elytra_broken"},
    "property": "minecraft:broken",
}
# Texels the 26.3 jar's own wings/elytra.png paints on the box's back face (south, 36..46 x 2..22).
VANILLA_BACK_TEXELS = 173


class GoldenCosmeticsTest(unittest.TestCase):
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

    def png(self, ref):
        namespace, path = ref.split(":", 1)
        name = f"assets/{namespace}/textures/{path}.png"
        self.assertIn(name, self.entries)
        return Image.open(io.BytesIO(self.entries[name])).convert("RGBA")

    def test_cosmetic_2_picks_golden_icons_and_every_other_elytra_stays_vanilla(self):
        definition = self.json("assets/minecraft/items/elytra.json")["model"]
        self.assertEqual((definition["property"], definition["index"]), ("minecraft:custom_model_data", 0))
        case = {c["when"]: c["model"] for c in definition["cases"]}["cosmetic_2"]
        self.assertEqual(case["property"], "minecraft:broken")
        self.assertEqual(case["on_false"]["model"], "claudesmp:item/golden_wings")
        self.assertEqual(case["on_true"]["model"], "claudesmp:item/golden_wings_broken")
        for ref in ("golden_wings", "golden_wings_broken"):
            model = self.json(f"assets/claudesmp/models/item/{ref}.json")
            self.assertEqual(self.png(model["textures"]["layer0"]).size, (16, 16))
        self.assertEqual(definition["fallback"], VANILLA_ELYTRA)

    def test_wings_asset_is_gold_on_a_bigger_back_face_and_never_the_players_cape(self):
        layers = self.json("assets/claudesmp/equipment/golden_wings.json")["layers"]
        self.assertEqual(list(layers), ["wings"])
        self.assertEqual(layers["wings"], [{"texture": "claudesmp:golden_wings"}])  # no use_player_texture
        tex = self.png("claudesmp:entity/equipment/wings/golden_wings")
        self.assertEqual(tex.size, (64, 32))
        back = [tex.getpixel((x, y))[3] for x in range(36, 46) for y in range(2, 22)]
        self.assertGreater(sum(1 for a in back if a), VANILLA_BACK_TEXELS)
        # Only the back face is painted: the inflated box is 4px deep, so edges would read as a slab.
        for x in range(64):
            for y in range(32):
                if not (36 <= x < 46 and 2 <= y < 22):
                    self.assertEqual(tex.getpixel((x, y))[3], 0, (x, y))

    def test_cosmetic_3_draws_the_halo_on_the_head_and_the_icon_elsewhere(self):
        definition = self.json("assets/minecraft/items/netherite_helmet.json")["model"]
        cases = {c["when"]: c["model"] for c in definition["cases"]}
        self.assertIn("dragonscale", cases)
        self.assertIn("dragon_head", cases)
        halo = cases["cosmetic_3"]
        self.assertEqual(halo["property"], "minecraft:display_context")
        self.assertEqual({c["when"]: c["model"]["model"] for c in halo["cases"]}, {"head": "claudesmp:item/golden_halo"})
        self.assertEqual(halo["fallback"]["model"], "claudesmp:item/golden_halo_icon")

    def test_halo_model_is_what_the_generator_writes_and_fits_the_element_range(self):
        model = self.json("assets/claudesmp/models/item/golden_halo.json")
        self.assertEqual(model, json.loads(json.dumps(gh.model())))
        for el in model["elements"]:
            for lo, hi in zip(el["from"], el["to"]):
                self.assertTrue(-16 <= lo <= hi <= 32, el)
            self.assertEqual(el["light_emission"], 15)

    def test_halo_floats_above_the_hat_layer_centred_over_the_head(self):
        """Every corner, through the worn head item chain, sits at least 1 head pixel above the
        skin's hat layer (its top is 8.5 px above the neck) and no further than 4.5 above it."""
        ys = []
        for el in self.json("assets/claudesmp/models/item/golden_halo.json")["elements"]:
            for x in (el["from"][0], el["to"][0]):
                for y in (el["from"][1], el["to"][1]):
                    for z in (el["from"][2], el["to"][2]):
                        # Segments only turn about the ring's vertical axis, so y is unrotated and
                        # the ring's x/z spread stays symmetric about the centre.
                        p = _worn_head_item([x / 16, y / 16, z / 16])
                        ys.append(p[1] * 16)
        lowest = max(ys)  # head-bone y points down
        highest = min(ys)
        self.assertLessEqual(lowest, -8.5 - 1.0)
        self.assertGreaterEqual(highest, -8.5 - 4.5)
        cx, cy, cz = gh.CENTER
        centre = _worn_head_item([cx / 16, cy / 16, cz / 16])
        self.assertAlmostEqual(centre[0], 0)
        self.assertAlmostEqual(centre[2], 0)

    def test_animated_textures_split_into_whole_frames(self):
        for name, size, frames in (("golden_halo", 32, gh.FRAMES), ("golden_halo_icon", 16, gh.ICON_FRAMES)):
            png = f"assets/claudesmp/textures/item/{name}.png"
            self.assertIn("animation", json.loads(self.entries[png + ".mcmeta"]))
            self.assertEqual(Image.open(io.BytesIO(self.entries[png])).size, (size, size * frames), name)
            sheet = Image.open(io.BytesIO(self.entries[png])).convert("RGBA")
            distinct = {sheet.crop((0, size * f, size, size * (f + 1))).tobytes() for f in range(frames)}
            self.assertGreater(len(distinct), 1, name)  # the spark moves

    def test_every_worn_look_the_market_jar_names_exists_in_the_pack(self):
        looks = {}
        for line in LOOKS.read_text(encoding="utf-8").splitlines():
            line = line.split("#", 1)[0].strip()
            if line:
                key, value = (part.strip() for part in line.split(":", 1))
                looks[int(key)] = value
        self.assertEqual(looks, {2: "claudesmp:golden_wings", 3: "none"})
        for value in looks.values():
            if value != "none":
                namespace, path = value.split(":", 1)
                self.assertIn(f"assets/{namespace}/equipment/{path}.json", self.entries)


if __name__ == "__main__":
    unittest.main()
