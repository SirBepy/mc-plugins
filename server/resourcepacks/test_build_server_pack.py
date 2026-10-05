"""Checks the built join pack: the Pollos gun and katana looks resolve to a model and texture inside
the zip, and every other crossbow and netherite sword still uses the vanilla 26.3 definition.

Run from server/resourcepacks/: python -m unittest test_build_server_pack
"""

import json
import tempfile
import unittest
import zipfile
from pathlib import Path

import build_server_pack

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
        states["loading"] = gun["fallback"]["on_true"]["model"]
        states["unloaded"] = gun["fallback"]["on_false"]["model"]
        self.assertEqual(set(states), {"arrow", "rocket", "loading", "unloaded"})
        textures = {}
        for state, ref in states.items():
            texture, display = self.resolved(ref)
            self.assertIn(asset(texture, "textures", "png"), self.entries, state)
            for context in ("thirdperson_righthand", "firstperson_righthand"):
                self.assertIn(context, display, state)
            textures[state] = texture
        self.assertEqual(len({textures["arrow"], textures["rocket"], textures["unloaded"]}), 3)

    def test_other_crossbows_stay_vanilla(self):
        crossbow = self.json("assets/minecraft/items/crossbow.json")["model"]
        self.assertEqual(crossbow["fallback"], VANILLA_CROSSBOW)

    def test_katana_resolves_to_its_texture(self):
        self.assert_look_resolves("netherite_sword", "katana")

    def test_other_netherite_swords_stay_vanilla(self):
        sword = self.json("assets/minecraft/items/netherite_sword.json")["model"]
        self.assertEqual(sword["fallback"], VANILLA_NETHERITE_SWORD)

    def test_bow_is_untouched(self):
        self.assertNotIn("assets/minecraft/items/bow.json", self.entries)


if __name__ == "__main__":
    unittest.main()
