"""Checks the built join pack: the Pollos gun and katana looks resolve to a model and texture inside
the zip, the gunshot Tweaks plays resolves to an .ogg inside it, the Dragon Radar and Shenron looks
are wired up and have the expected pixels, and every other crossbow, netherite sword, compass and
vanilla sound still uses the vanilla 26.3 definition.

Run from server/resourcepacks/: python -m unittest test_build_server_pack
"""

import io
import json
import re
import tempfile
import unittest
import zipfile
from pathlib import Path

import build_server_pack
from PIL import Image

ROOT = build_server_pack.ROOT

# The 26.3 client jar's own assets/minecraft/items/crossbow.json model.
VANILLA_CROSSBOW = {
    "type": "minecraft:select",
    "cases": [
        {"model": {"type": "minecraft:model", "model": "minecraft:item/crossbow_arrow"}, "when": "arrow"},
        {"model": {"type": "minecraft:model", "model": "minecraft:item/crossbow_firework"}, "when": "rocket"},
    ],
    "fallback": {
        "type": "minecraft:condition",
        "on_false": {"type": "minecraft:model", "model": "minecraft:item/crossbow"},
        "on_true": {
            "type": "minecraft:range_dispatch",
            "entries": [
                {"model": {"type": "minecraft:model", "model": "minecraft:item/crossbow_pulling_1"}, "threshold": 0.58},
                {"model": {"type": "minecraft:model", "model": "minecraft:item/crossbow_pulling_2"}, "threshold": 1.0},
            ],
            "fallback": {"type": "minecraft:model", "model": "minecraft:item/crossbow_pulling_0"},
            "property": "minecraft:crossbow/pull",
        },
        "property": "minecraft:using_item",
    },
    "property": "minecraft:charge_type",
}

# The 26.3 client jar's own assets/minecraft/items/netherite_sword.json model.
VANILLA_NETHERITE_SWORD = {"type": "minecraft:model", "model": "minecraft:item/netherite_sword"}


def _vanilla_compass_branch(target):
    # The 26.3 client jar's own compass.json: 33 entries, compass_16 at both ends (frames
    # 16..31 then 00..16) so the needle loops back around to vanilla's own frame 16.
    order = list(range(16, 32)) + list(range(0, 17))
    entries = [
        {
            "model": {"type": "minecraft:model", "model": f"minecraft:item/compass_{nn:02d}"},
            "threshold": 0.0 if i == 0 else i - 0.5,
        }
        for i, nn in enumerate(order)
    ]
    return {
        "type": "minecraft:range_dispatch",
        "entries": entries,
        "property": "minecraft:compass",
        "scale": 32.0,
        "target": target,
    }


# The 26.3 client jar's own assets/minecraft/items/compass.json model.
VANILLA_COMPASS = {
    "type": "minecraft:condition",
    "component": "minecraft:lodestone_tracker",
    "on_false": _vanilla_compass_branch("spawn"),
    "on_true": _vanilla_compass_branch("lodestone"),
    "property": "minecraft:has_component",
}


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

    def assert_look_resolves(self, item, when):
        definition = self.json(f"assets/minecraft/items/{item}.json")["model"]
        self.assertEqual(definition["property"], "minecraft:custom_model_data")
        case = next(c for c in definition["cases"] if c["when"] == when)
        model = self.json(asset(case["model"]["model"], "models", "json"))
        self.assertIn(asset(model["textures"]["layer0"], "textures", "png"), self.entries)

    def resolved(self, ref):
        """A model's layer0 texture and display contexts, following its parent chain in the zip."""
        texture, display = None, {}
        while ref.startswith("claudesmp:"):
            model = self.json(asset(ref, "models", "json"))
            texture = texture or model.get("textures", {}).get("layer0")
            display = {**model.get("display", {}), **display}
            ref = model["parent"]
        return texture, display

    def test_pollos_gun_has_a_look_per_crossbow_state(self):
        definition = self.json("assets/minecraft/items/crossbow.json")["model"]
        gun = next(c for c in definition["cases"] if c["when"] == "pollos_gun")["model"]
        self.assertEqual(gun["property"], "minecraft:charge_type")
        states = {c["when"]: c["model"]["model"] for c in gun["cases"]}
        states["unloaded"] = gun["fallback"]["model"]
        self.assertEqual(set(states), {"arrow", "rocket", "unloaded"})
        textures, displays = {}, {}
        for state, ref in states.items():
            texture, display = self.resolved(ref)
            self.assertIn(asset(texture, "textures", "png"), self.entries, state)
            for context in ("thirdperson_righthand", "firstperson_righthand"):
                self.assertIn(context, display, state)
            textures[state], displays[state] = texture, display
        self.assertEqual(len(set(textures.values())), 3)
        # Unloaded lies flat like a vanilla crossbow; loaded is held upright.
        self.assertEqual(displays["arrow"], displays["rocket"])
        self.assertNotEqual(displays["unloaded"]["thirdperson_righthand"], displays["arrow"]["thirdperson_righthand"])

    def test_pollos_gunshot_is_the_sound_tweaks_plays(self):
        listener = (ROOT / "tweaks/src/main/java/dev/sirbepy/mc/tweaks/listener/PollosGunListener.java").read_text()
        played = re.search(r'SHOT_SOUND = "([^"]+)"', listener).group(1)
        namespace, event = played.split(":", 1)
        sounds = self.json(f"assets/{namespace}/sounds.json")
        for sound in sounds[event]["sounds"]:
            self.assertIn(asset(sound, "sounds", "ogg"), self.entries)
        subtitles = self.json(f"assets/{namespace}/lang/en_us.json")
        self.assertIn(sounds[event]["subtitle"], subtitles)

    def test_vanilla_sounds_are_untouched(self):
        self.assertNotIn("assets/minecraft/sounds.json", self.entries)
        self.assertFalse(any(name.startswith("assets/minecraft/sounds/") for name in self.entries))

    def test_other_crossbows_stay_vanilla(self):
        crossbow = self.json("assets/minecraft/items/crossbow.json")["model"]
        self.assertEqual(crossbow["fallback"], VANILLA_CROSSBOW)

    def test_katana_resolves_to_its_texture(self):
        self.assert_look_resolves("netherite_sword", "katana")

    def test_katana_blade_is_longer_at_the_same_pixel_size(self):
        model = self.json("assets/claudesmp/models/item/katana.json")
        png = self.entries["assets/claudesmp/textures/item/katana.png"]
        width = int.from_bytes(png[16:20], "big")
        self.assertEqual(width, 32)
        # Twice vanilla item/handheld's in-hand scale, so a 32 px texel is as big as a 16 px one.
        self.assertEqual(model["display"]["thirdperson_righthand"]["scale"], [1.7, 1.7, 1.7])
        self.assertEqual(model["display"]["firstperson_righthand"]["scale"], [1.36, 1.36, 1.36])

    def test_other_netherite_swords_stay_vanilla(self):
        sword = self.json("assets/minecraft/items/netherite_sword.json")["model"]
        self.assertEqual(sword["fallback"], VANILLA_NETHERITE_SWORD)

    def test_bow_is_untouched(self):
        self.assertNotIn("assets/minecraft/items/bow.json", self.entries)

    def test_dragon_radar_idle_resolves_to_its_texture(self):
        self.assert_look_resolves("compass", "dragon_radar_idle")

    def test_dragon_radar_has_32_frames_with_a_texture_each(self):
        compass = self.json("assets/minecraft/items/compass.json")["model"]
        radar = next(c for c in compass["cases"] if c["when"] == "dragon_radar")["model"]
        self.assertEqual(radar["property"], "minecraft:compass")
        self.assertEqual(radar["target"], "spawn")
        self.assertEqual(radar["scale"], 32.0)
        refs = {e["model"]["model"] for e in radar["entries"]}
        self.assertEqual(len(refs), 32)
        for ref in refs:
            model = self.json(asset(ref, "models", "json"))
            self.assertIn(asset(model["textures"]["layer0"], "textures", "png"), self.entries, ref)

    def test_other_compasses_stay_vanilla(self):
        compass = self.json("assets/minecraft/items/compass.json")["model"]
        self.assertEqual(compass["fallback"], VANILLA_COMPASS)

    def test_dragon_wings_are_transparent_and_body_is_not(self):
        png = Image.open(io.BytesIO(self.entries["assets/minecraft/textures/entity/enderdragon/dragon.png"]))
        px = png.convert("RGBA").load()
        self.assertEqual(px[20, 100][3], 0, "wing membrane should be transparent")
        self.assertEqual(px[150, 95][3], 0, "wing bone strut should be transparent")
        self.assertGreater(px[70, 70][3], 0, "body should still be opaque")

    def test_dragon_body_is_bright_green_not_near_black(self):
        # Vanilla's body luminance sits in a narrow near-black band; the pack stretches it
        # across a jade gradient so the scale pattern stays visible but the body reads bright.
        png = Image.open(io.BytesIO(self.entries["assets/minecraft/textures/entity/enderdragon/dragon.png"]))
        px = png.convert("RGBA").load()
        r, g, b, _a = px[70, 70]
        self.assertGreater(g, r)
        self.assertGreater(g, b)
        self.assertGreater(g, 120, "body should read as bright green, not near-black")

    def test_dragon_png_eyes_are_red_to_match_the_glow_overlay(self):
        # dragon.png itself bakes in a handful of non-gray eye pixels (same 4 clusters, same
        # colours, as dragon_eyes.png's opaque pixels) that must be re-hued too, or the base
        # skin shows vanilla's purple under the red glow overlay.
        png = Image.open(io.BytesIO(self.entries["assets/minecraft/textures/entity/enderdragon/dragon.png"]))
        px = png.convert("RGBA").load()
        for x, y in [(131, 50), (208, 44), (197, 67), (133, 58)]:
            r, g, b, _a = px[x, y]
            self.assertGreater(r, g, f"eye pixel at {(x, y)} should be red, not green/purple")
            self.assertGreater(r, b, f"eye pixel at {(x, y)} should be red, not green/purple")

    def test_dragon_eyes_are_red_not_vanilla_purple(self):
        png = Image.open(io.BytesIO(self.entries["assets/minecraft/textures/entity/enderdragon/dragon_eyes.png"]))
        px = png.convert("RGBA").load()
        opaque = [px[x, y] for x in range(png.width) for y in range(png.height) if px[x, y][3] > 0]
        self.assertTrue(opaque)
        r, g, b, _a = opaque[0]
        self.assertGreater(r, b, "eyes should be re-hued red, not vanilla's blue-leaning purple")
        self.assertGreater(r, g)


if __name__ == "__main__":
    unittest.main()
