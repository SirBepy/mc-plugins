"""Checks the Bedrock dragon pack: it is up to date with its generator, every part has a client
entity, geometry and the pose animation, the set textures carry DragonModel's colours, and, the
main point, every part posed through Bedrock's chain lands on exactly the same corners as the Java
item display draws (same yaw and pitch, same size).

The Java chain is the one javap-verified for 26.3 (project memory "item display model
conventions"): world = Ry(-yaw) Rx(pitch) scale Ry(pi) (model - 8) / 16, element rotation Rz Ry Rx
about its origin. The Bedrock chain is the convention dragon_bedrock.py's docstring states: geometry
mirrored in x, a rotation [x, y, z] meaning right-handed (-x, y, -z) applied x first, root bone
turned [pitch, yaw], and the entity at yaw 0 drawing geometry -z toward world +z (z mirrored), as a
Bedrock mob faces south at yaw 0 with its head at geometry -z and its right arm at geometry -x.

Run from server/resourcepacks/: python -m unittest test_dragon_bedrock
"""

import io
import json
import math
import random
import unittest
import zipfile

import numpy as np
from PIL import Image

import dragon_bedrock as db
import dragon_models as dm


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def corners(lo, hi):
    return [np.array([x, y, z], float) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]


def java_world(element, yaw, pitch, scale):
    pts = corners(element["from"], element["to"])
    if "rotation" in element:
        rot = element["rotation"]
        m = rz(math.radians(rot["z"])) @ ry(math.radians(rot["y"])) @ rx(math.radians(rot["x"]))
        o = np.array(rot["origin"], float)
        pts = [m @ (p - o) + o for p in pts]
    pose = ry(math.radians(-yaw)) @ rx(math.radians(pitch)) @ (scale * ry(math.pi))
    return [pose @ ((p - 8) / 16) for p in pts]


def bedrock_world(cube, yaw, pitch, scale):
    lo = np.array(cube["origin"], float)
    pts = corners(lo, lo + np.array(cube["size"], float))
    if "rotation" in cube:
        x, y, z = (math.radians(a) for a in cube["rotation"])
        m = rz(-z) @ ry(y) @ rx(-x)
        o = np.array(cube["pivot"], float)
        pts = [m @ (p - o) + o for p in pts]
    root = ry(math.radians(yaw)) @ rx(math.radians(-pitch))
    to_world = np.diag([1.0, 1.0, -1.0])
    return [to_world @ (scale * (root @ (p / 16))) for p in pts]


class DragonBedrockPackTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.data = db.build()
        cls.zip = zipfile.ZipFile(io.BytesIO(cls.data))

    def json(self, path):
        return json.loads(self.zip.read(path))

    def test_the_jar_ships_the_current_pack(self):
        self.assertEqual(db.OUT.read_bytes(), self.data,
                         "run python server/resourcepacks/dragon_bedrock.py and commit the pack")

    def test_every_part_has_an_entity_geometry_and_the_pose(self):
        for part in dm.PARTS:
            entity = self.json(f"entity/dragon_{part}.entity.json")["minecraft:client_entity"]["description"]
            self.assertEqual(f"claudesmp:dragon_{part}", entity["identifier"])
            geo_id = entity["geometry"]["default"]
            geo = self.json(f"models/entity/dragon_{part}.geo.json")["minecraft:geometry"][0]
            self.assertEqual(geo_id, geo["description"]["identifier"])
            self.assertEqual(len(dm.PARTS[part][0]), len(geo["bones"][0]["cubes"]))
            self.assertEqual(["pose"], entity["scripts"]["animate"])
            for s in db.SETS:
                self.assertIn(entity["textures"][s] + ".png", self.zip.namelist())
        root = self.json("animations/dragon.animation.json")["animations"]["animation.claudesmp.dragon.pose"]["bones"]["root"]
        self.assertEqual(["query.property('claudesmp:pitch')", "query.property('claudesmp:yaw')", 0], root["rotation"])
        textures = self.json("render_controllers/dragon.render_controllers.json")["render_controllers"]["controller.render.claudesmp.dragon"]["textures"]
        self.assertEqual(["Array.skins[query.property('claudesmp:skin')]"], textures)

    def test_bedrock_draws_every_part_where_java_does(self):
        rng = random.Random(3)
        for part, (elements, pivot) in dm.PARTS.items():
            java = dm.part(part, elements, pivot)["elements"]
            cubes = self.json(f"models/entity/dragon_{part}.geo.json")["minecraft:geometry"][0]["bones"][0]["cubes"]
            for _ in range(3):
                yaw, pitch, size = rng.uniform(-180, 180), rng.uniform(-80, 80), rng.uniform(0.5, 3)
                for i, (j, b) in enumerate(zip(java, cubes)):
                    expected = java_world(j, yaw, pitch, dm.MODEL_SCALE * size)
                    actual = bedrock_world(b, yaw, pitch, size)
                    # The x mirror reorders a cube's corners, so match each to its nearest.
                    miss = max(min(np.linalg.norm(e - a) for a in actual) for e in expected)
                    self.assertLess(miss, 2e-3, f"{part} element {i} at yaw {yaw:.0f} pitch {pitch:.0f}")

    def test_the_chain_faces_the_part_where_minecraft_yaw_and_pitch_point(self):
        # A part's front is rig -z; at yaw 90 / pitch 0 it faces -x, at pitch 90 straight down.
        front = {"origin": [-0.5, -0.5, -16], "size": [1, 1, 1]}
        tip = max(bedrock_world(front, 90, 0, 1), key=lambda p: -p[0])
        self.assertLess(tip[0], -0.9)
        tip = min(bedrock_world(front, 0, 90, 1), key=lambda p: p[1])
        self.assertLess(tip[1], -0.9)

    def test_each_set_texture_carries_dragon_models_colours(self):
        for dragon, sets in db.COLORS.items():
            for s, (body, accent) in sets.items():
                img = Image.open(io.BytesIO(self.zip.read(f"textures/entity/dragon/{dragon}_{s}.png"))).convert("RGBA")
                self.assertEqual(rgb(body), img.getpixel(db.SWATCH)[:3])
                self.assertEqual(rgb(accent), img.getpixel((db.SWATCH[0] + 1, db.SWATCH[1]))[:3])
                x0, y0, x1, y1 = dm.REGIONS["scales"]
                r, g, b, _ = img.getpixel((x0 + 2, y0 + 2))
                br, bg, bb = rgb(body)
                # Scales are light greyscale times the body colour: same channel order, never brighter.
                self.assertTrue(r <= br and g <= bg and b <= bb)
                self.assertEqual(sorted(range(3), key=lambda k: (br, bg, bb)[k]), sorted(range(3), key=lambda k: (r, g, b)[k]))

    def test_the_version_follows_the_content(self):
        version = self.json("manifest.json")["header"]["version"]
        content = db.files()
        content["textures/entity/dragon/shenron_end.png"] += b"\0"
        self.assertNotEqual(version, db.manifest(content)["header"]["version"])
        self.assertEqual(version, db.manifest(db.files())["header"]["version"])


def rgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255)


if __name__ == "__main__":
    unittest.main()
