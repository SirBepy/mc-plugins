"""Checks the Overgrown Sentinel pack and rig: every rig part has a cuboid model and an item
definition inside the model range, the part tree and poses are consistent, the generator's output
is deterministic and matches what is committed, and the display Transformation recipe in the rig's
_doc reproduces the rig-frame placement the 26.3 client draws.

Run from server/resourcepacks/: python -m unittest test_guardian_models
"""

import io
import json
import math
import unittest

import numpy as np
from PIL import Image

import guardian_models as gm

REQUIRED_POSES = {"idle", "walk_a", "walk_b", "slam_windup", "slam_hit", "sweep_windup", "sweep_hit",
                  "rooted", "heartguard", "stagger"}


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


class GuardianModelsTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.rig = json.loads(gm.RIG_PATH.read_text(encoding="utf-8"))
        cls.parts = {p["name"]: p for p in cls.rig["parts"]}

    def model(self, name):
        return json.loads((gm.MODEL_DIR / f"sentinel_{name}.json").read_text(encoding="utf-8"))

    def test_the_rig_parses_and_documents_itself(self):
        self.assertTrue(self.rig["_doc"])
        self.assertTrue(all(isinstance(line, str) and line for line in self.rig["_doc"]))
        self.assertEqual("overgrown_sentinel", self.rig["id"])
        self.assertGreater(self.rig["modelScale"], 0)
        self.assertEqual("ravager", self.rig["hitbox"]["entity"])
        self.assertGreater(self.rig["hitbox"]["scaleAttribute"], 0)
        self.assertEqual(len(self.parts), len(self.rig["parts"]), "part names must be unique")

    def test_every_part_has_a_model_and_an_item_definition(self):
        for name, part in self.parts.items():
            self.assertEqual(f"claudesmp:guardian/sentinel_{name}", part["model"])
            definition = json.loads((gm.ITEMS_DIR / f"sentinel_{name}.json").read_text(encoding="utf-8"))["model"]
            self.assertEqual("minecraft:model", definition["type"])
            self.assertEqual(f"claudesmp:item/guardian/sentinel_{name}", definition["model"])
            self.assertEqual([0, 1, 2], [t["index"] for t in definition["tints"]])
            self.assertEqual(gm.PALETTES["normal"], [t["default"] for t in definition["tints"]])
            self.assertTrue(self.model(name)["elements"], name)

    def test_every_element_stays_inside_the_item_model_range(self):
        for name in self.parts:
            for i, el in enumerate(self.model(name)["elements"]):
                for v in el["from"] + el["to"]:
                    self.assertTrue(-16 <= v <= 32, f"{name} element {i} leaves -16..32")
                for face in el["faces"].values():
                    self.assertTrue(all(0 <= u <= 16 for u in face["uv"]), f"{name} element {i} uv")
                    self.assertIn(face.get("tintindex", 0), (0, 1, 2))
                    self.assertEqual("#t", face["texture"])

    def test_a_pivot_lands_on_model_point_8_8_8(self):
        # The rig's _doc promises the pivot is the display origin, which renders model point (8, 8, 8).
        pivot = (3.0, 20.0, -7.0)
        model = gm.part_model("probe", [gm.B((3 - 1, 19, -8), (3 + 1, 21, -6), "stone")], pivot)
        el = model["elements"][0]
        half = 1 / gm.MODEL_SCALE
        self.assertEqual([8 - half] * 3, el["from"])
        self.assertEqual([8 + half] * 3, el["to"])

    def test_the_part_tree_is_acyclic_and_parents_come_first(self):
        seen = set()
        for part in self.rig["parts"]:
            parent = part["parent"]
            if parent is not None:
                self.assertIn(parent, self.parts, part["name"])
                self.assertIn(parent, seen, f"{part['name']} is listed before its parent")
                self.assertTrue(set(part["tiers"]) <= set(self.parts[parent]["tiers"]),
                                f"{part['name']} would spawn without its parent")
            seen.add(part["name"])
        for name in self.parts:
            hops, node = 0, name
            while node is not None:
                node = self.parts[node]["parent"]
                hops += 1
                self.assertLessEqual(hops, len(self.parts), f"cycle through {name}")
        self.assertEqual(1, [p["parent"] for p in self.rig["parts"]].count(None), "exactly one root")

    def test_poses_name_only_existing_parts(self):
        self.assertTrue(REQUIRED_POSES <= set(self.rig["poses"]))
        for pose, entries in self.rig["poses"].items():
            for part, spec in entries.items():
                self.assertIn(part, self.parts, f"{pose} names an unknown part")
                self.assertLessEqual(set(spec), {"rot", "offset", "scale"})
                for key in ("rot", "offset"):
                    if key in spec:
                        self.assertEqual(3, len(spec[key]), f"{pose}.{part}.{key}")

    def test_palettes_and_tiers(self):
        self.assertEqual(["normal", "heroic", "mythic"], self.rig["tiers"])
        for tier in self.rig["tiers"]:
            colours = self.rig["palettes"][tier]
            self.assertEqual(3, len(colours))
            self.assertTrue(all(isinstance(c, int) and 0 <= c <= 0xFFFFFF for c in colours))
        for tier in self.rig["tiers"]:
            self.assertTrue(any(tier in p["tiers"] for p in self.rig["parts"]))
        count = {t: sum(t in p["tiers"] for p in self.rig["parts"]) for t in self.rig["tiers"]}
        self.assertLess(count["normal"], count["heroic"])
        self.assertLess(count["heroic"], count["mythic"])
        self.assertEqual(3, len({tuple(c) for c in self.rig["palettes"].values()}))

    def test_the_generator_is_deterministic(self):
        def snapshot():
            out = {}
            for path, data in gm.outputs():
                if isinstance(data, Image.Image):
                    buf = io.BytesIO()
                    data.save(buf, "PNG")
                    out[path] = buf.getvalue()
                else:
                    out[path] = json.dumps(data, indent=2).encode()
            return out

        self.assertEqual(snapshot(), snapshot())

    def test_the_committed_files_are_what_the_generator_writes(self):
        for path, data in gm.outputs():
            self.assertTrue(path.exists(), f"{path.name} missing: run python server/resourcepacks/guardian_models.py")
            if isinstance(data, Image.Image):
                on_disk = Image.open(path).convert("RGBA")
                self.assertTrue(np.array_equal(np.asarray(on_disk), np.asarray(data.convert("RGBA"))), path.name)
            else:
                self.assertEqual(data, json.loads(path.read_text(encoding="utf-8")),
                                 f"{path.name} is stale: run python server/resourcepacks/guardian_models.py")

    def test_the_display_transformation_recipe_matches_the_rig_frame(self):
        # _doc: Transformation = Ry(pi) * rigMatrix * Ry(pi); the client then draws Ry(-yaw) * T * Ry(pi) * u.
        c = ry(math.pi)
        rng = np.random.default_rng(5)
        for _ in range(20):
            a = rng.uniform(-math.pi, math.pi, 3)
            rot = rz(a[2]) @ ry(a[1]) @ rx(a[0])
            t, s, yaw = rng.uniform(-3, 3, 3), rng.uniform(0.5, 3), rng.uniform(-math.pi, math.pi)
            u = rng.uniform(-0.5, 0.5, 3)
            # Bukkit fields per _doc: translation (-tx, ty, -tz), left rotation C R C, uniform scale s.
            display = ry(-yaw) @ ((c @ t) + (c @ rot @ c) @ (s * (c @ u)))
            rig_frame = ry(-yaw) @ c @ (t + rot @ (s * u))
            self.assertTrue(np.allclose(display, rig_frame), "conjugating by Ry(pi) must give the rig-frame placement")
        # And the facing rule: model -Z (rig front) faces the entity's look direction (yaw 0 looks along +Z).
        self.assertTrue(np.allclose(c @ [0, 0, -1], [0, 0, 1]))


if __name__ == "__main__":
    unittest.main()
