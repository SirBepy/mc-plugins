"""Generates the Overgrown Sentinel's body: a stone-and-root colossus built from item displays, as
item models of claudesmp-items plus the rig file the minibosses plugin poses them with.

Same pattern as dragon_models.py: one item definition (assets/claudesmp/items/guardian/) and one
cuboid model (models/item/guardian/) per part, all sharing the 128x128 atlas
textures/item/guardian/sentinel.png. Every definition tints from the item's custom_model_data
colours: index 0 the stone, 1 the moss and vines, 2 the glow (heart, eyes, crystals, vein seams).
So one set of models serves all three tiers; a tier differs by palette plus its own extra parts
(heroic crystals, mythic crown, blossoms and vein seams).

The rig (minibosses/src/main/resources/rigs/overgrown_sentinel.json) is the single source of truth
for the part tree, pivots, tier palettes and animation poses; its "_doc" lists every convention.
The models are drawn at 1/MODEL_SCALE of the real size (an element must sit in -16..32) and the
display transformation scales them back up.

Geometry below is authored in rig pixels (16 per block) with the feet at the origin, +Y up,
-Z the front and +X the character's right, then shifted so each part's pivot lands on the model
point (8, 8, 8), which an item display renders at its own position (javap of the 26.3 client:
ItemTransform.apply ends with translate(-0.5, -0.5, -0.5) on both of its paths).

Run from anywhere: python server/resourcepacks/guardian_models.py
"""

import math
from pathlib import Path

import numpy as np
from PIL import Image

from pack_io import write_json

ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / "server" / "resourcepacks" / "claudesmp-items" / "assets" / "claudesmp"
RIG_PATH = ROOT / "minibosses" / "src" / "main" / "resources" / "rigs" / "overgrown_sentinel.json"
TEX_ID = "claudesmp:item/guardian/sentinel"
TEX_PATH = PACK / "textures" / "item" / "guardian" / "sentinel.png"
MODEL_DIR = PACK / "models" / "item" / "guardian"
ITEMS_DIR = PACK / "items" / "guardian"
ATLAS = 128

MODEL_SCALE = 2.0
ALL = ["normal", "heroic", "mythic"]
UP = ["heroic", "mythic"]

# Texture regions in atlas pixels: (x0, y0, x1, y1).
REGIONS = {
    "stone": (0, 0, 64, 64),
    "cracked": (64, 0, 128, 64),
    "moss": (0, 64, 64, 112),
    "bark": (64, 64, 128, 112),
    "vine": (0, 112, 32, 128),
    "glow": (32, 112, 48, 128),
    "eye": (48, 112, 56, 120),
    "crystal": (56, 112, 72, 128),
    "blossom": (72, 112, 80, 120),
    "thorn": (80, 112, 88, 120),
    "vein": (88, 112, 104, 128),
}
# Which tint each material takes: 0 stone, 1 moss, 2 glow, None untinted.
TINT = {"stone": 0, "cracked": 0, "moss": 1, "vine": 1, "glow": 2, "eye": 2, "crystal": 2,
        "thorn": 2, "vein": 2}
# Materials stretched over a whole face window sized 1 texel per rig pixel; the rest use their whole region.
TILED = {"stone", "cracked", "moss", "bark", "vine"}
EMISSION = {"glow": 15, "eye": 15, "vein": 15, "crystal": 8, "blossom": 3}

# Palettes: colours for custom_model_data indices 0 (stone), 1 (moss), 2 (glow).
PALETTES = {
    "normal": [0xA9ABA5, 0x66A83C, 0x58FF45],
    "heroic": [0x5A5F73, 0x3E9C78, 0x2DFFD6],
    "mythic": [0x3A3544, 0x2E7A3A, 0xFFC62E],
}


# --- Element helpers (absolute rig pixels) ---------------------------------------------------------

def B(f, t, mat, rot=None):
    """One cuboid. rot: (origin, (x, y, z) degrees) in the 26.3 free-angle form, Euler XYZ."""
    return {"from": list(f), "to": list(t), "mat": mat, "rot": rot}


def mirror(elements):
    """Mirrors elements across x = 0 (the rig is centred on x = 0)."""
    out = []
    for e in elements:
        f, t = e["from"], e["to"]
        m = dict(e)
        m["from"] = [-t[0], f[1], f[2]]
        m["to"] = [-f[0], t[1], t[2]]
        if e["rot"]:
            o, (rx, ry, rz) = e["rot"]
            m["rot"] = ((-o[0], o[1], o[2]), (rx, -ry, -rz))
        out.append(m)
    return out


def seg(p, q, w, mat):
    """A w x w beam from p to q: a box along Y, turned so +Y points at q (Rx then Ry)."""
    p, q = np.array(p, float), np.array(q, float)
    d = q - p
    length = float(np.linalg.norm(d))
    d /= length
    ax = math.degrees(math.acos(max(-1.0, min(1.0, d[1]))))
    ay = math.degrees(math.atan2(d[0], d[2]))
    m = (p + q) / 2
    half = np.array([w / 2, length / 2, w / 2])
    return B(m - half, m + half, mat, (tuple(m), (round(ax, 3), round(ay, 3), 0)))


def chain(points, widths, mat):
    return [seg(points[i], points[i + 1], widths[i], mat) for i in range(len(points) - 1)]


def shard(p, d, length, w, mat="crystal"):
    """A crystal: a wide base beam with a narrower tip, growing from p along direction d."""
    p, d = np.array(p, float), np.array(d, float)
    d /= np.linalg.norm(d)
    mid = p + d * length * 0.6
    return [seg(p, mid, w, mat), seg(mid, p + d * length, w * 0.5, mat)]


# --- Part geometry ----------------------------------------------------------------------------------

def pelvis():
    return [
        B((-12, 24, -8), (12, 34, 8), "stone"),
        B((-9, 25, -10), (9, 33, -8), "cracked"),
        B((-11, 33, -7), (11, 35.5, 7), "bark"),
        B((-7, 28, 7), (7, 34, 10), "moss"),
        B((-17, 21, -11), (-8, 32, -7), "cracked", ((-12, 27, -9), (0, 0, 14))),
        B((8, 21, -11), (17, 32, -7), "stone", ((12, 27, -9), (0, 0, -14))),
        B((-4, 20, -11), (4, 30, -8), "cracked"),
        seg((-6, 34, -11.5), (-7, 21, -13), 2.6, "bark"),
        seg((6, 34, -11.5), (9, 20, -13), 2.4, "bark"),
        seg((0, 34, -11.5), (-1, 19, -13.5), 2.0, "bark"),
    ]


def leg(right):
    els = [
        B((3, 6, -8), (16, 26, 8), "stone"),
        B((4, 14, -10), (15, 21, -8), "cracked"),
        B((1, 0, -13), (18, 7, 9), "cracked"),
        B((2, 0, -17), (6, 5, -13), "stone"),
        B((8, 0, -18), (12, 6, -13), "stone"),
        B((13.5, 0, -17), (17.5, 4.5, -13), "stone"),
        B((16, 8, -5), (19, 22, 4), "bark"),
    ]
    if right:
        els.append(B((2, 7, -12), (17, 9, -1), "moss"))
        els.append(B((3.5, 24, -9), (15.5, 27, 7), "moss"))
    else:
        els.append(B((3, 12, -9.5), (16, 15.5, 9.5), "bark"))
        els.append(B((3, 20, 7), (15, 26, 10), "moss"))
    return els if right else mirror(els)


def leg_roots(right):
    els = chain([(16, 18, -3), (21, 11, -5), (26, 5, -3), (31, 1.5, 0)], [3.5, 2.8, 2.2], "bark")
    els += chain([(16, 14, 3), (22, 8, 6), (27, 3, 10), (31, 1.5, 14)], [3.2, 2.6, 2.0], "bark")
    if not right:
        els += chain([(16, 20, 0), (23, 15, 2), (28, 8, 3), (33, 2, 5)], [2.6, 2.2, 1.8], "bark")
    return els if right else mirror(els)


def torso():
    return [
        B((-11, 34, -8), (11, 46, 9), "stone"),
        B((-16, 44, -10), (16, 58, 11), "stone"),
        B((-11, 34, -10), (11, 38, -8), "cracked"),
        B((-11.5, 34, -11.5), (11.5, 36.5, -9.5), "bark"),
        B((-10, 56, -8), (10, 61, 8), "stone"),
        B((-14, 42, 10), (14, 62, 17), "cracked"),
        B((-12, 58, 9), (2, 62.5, 17), "moss"),
        B((5, 56, 11), (13, 61, 17), "moss"),
        B((-9, 48, 16.6), (-2, 55, 17.6), "moss"),
        B((3, 44, 16.6), (11, 50, 17.6), "moss"),
        seg((-8, 60, 14), (-12, 70, 26), 5, "cracked"),
        seg((2, 61, 14), (3, 73, 28), 5.5, "cracked"),
        seg((11, 58, 14), (16, 67, 25), 4.5, "cracked"),
        # Right shoulder: huge broken boulders, it carries the slam arm.
        B((17, 49, -11), (37, 64, 11), "stone", ((27, 56, 0), (0, 0, -10))),
        B((19, 58, -12), (35, 70, 7), "cracked", ((27, 63, -2), (12, 14, -18))),
        B((20, 63, -8), (33, 67, 8), "moss", ((27, 65, 0), (0, 0, -10))),
        B((20, 50, -13), (34, 60, -12), "cracked"),
        # Left shoulder: smaller, wrapped in roots.
        B((-33, 50, -9), (-17, 62, 10), "stone", ((-25, 56, 0), (0, 0, 8))),
        B((-31, 60, -7), (-20, 64, 6), "moss"),
        B((-35, 52, -7), (-31, 60, 6), "bark"),
        seg((-33, 61, -8), (-21, 52, 6), 3, "bark"),
        seg((-21, 61, 8), (-34, 53, -4), 2.6, "bark"),
    ]


def plate():
    return [
        B((4, 38, -12), (16, 58, -8), "cracked"),
        B((4, 56, -13), (16, 59, -8), "stone"),
        B((4, 40, -13), (6, 56, -12), "stone"),
        B((9, 42, -12.3), (10.2, 52, -11.8), "glow"),
        B((12, 46, -12.3), (13, 56, -11.8), "glow"),
    ]


def heart():
    return [
        B((-4, 41, -11), (4, 50, -6), "glow"),
        B((-3, 42.5, -11.5), (3, 48.5, -6.5), "glow", ((0, 45.5, -8.5), (0, 45, 0))),
        B((-3, 42.5, -11.5), (3, 48.5, -6.5), "glow", ((0, 45.5, -8.5), (45, 0, 45))),
    ]


def head():
    els = [
        B((-7, 55, -10), (7, 67, 4), "stone"),
        B((-8, 62.5, -12), (8, 66.5, -8), "cracked"),
        B((-5.5, 52, -11), (5.5, 56, 1), "cracked"),
        B((-3, 50, -10), (3, 52.5, -6), "moss"),
        B((-1.5, 55, -11), (1.5, 61, -10), "stone"),
        B((-6, 59, -10.6), (-2, 62, -9.9), "eye"),
        B((2, 59, -10.6), (6, 62, -9.9), "eye"),
        B((-5, 67, -7), (5, 69, 2), "moss"),
    ]
    horn = [seg((-6.5, 63, -2), (-11, 71, -4.5), 2.6, "bark"), seg((-11, 71, -4.5), (-12.5, 76, -7.5), 1.7, "bark")]
    return els + horn + mirror(horn)


def arm_r_upper():
    return [
        B((21, 45, -9.5), (33, 55, 9.5), "stone"),
        B((22.5, 36, -8), (31.5, 46, 8), "cracked"),
        B((20.5, 42, -10), (33.5, 46.5, 10), "stone"),
        B((21.5, 50, 3), (32, 55.5, 9), "moss"),
        B((20, 38, -10.5), (34, 41.5, 10.5), "bark"),
        seg((22, 54, -8), (32, 42, -10.5), 2.6, "bark"),
    ]


def arm_r_fore():
    return [
        B((22.5, 24, -7.5), (31.5, 37, 7.5), "cracked"),
        B((21.5, 25, -8.5), (32.5, 29, 8.5), "stone"),
        B((23, 33, -8), (31, 37.5, -3), "moss"),
        B((21, 27.5, -8.8), (33, 30.5, 8.8), "bark"),
    ]


def fist_r():
    els = [
        B((15, 2, -14), (39, 21, 11), "stone"),
        B((17, 19, -12), (37, 31, 9), "cracked", ((27, 25, -1), (0, 0, 9))),
        B((21, 29, -6), (34, 35, 5), "stone", ((27, 32, 0), (18, 25, -14))),
        B((21, 27, -8), (31, 30.5, 2), "moss", ((26, 28, -3), (0, 0, 9))),
        B((13, 8, -9), (14.6, 22, 8), "stone"),
        B((15, 12.5, -20.4), (39, 14, -19.9), "glow"),
        B((26, 4, -20.4), (27.6, 20, -19.9), "glow"),
        B((14.4, 6, -2), (14.9, 24, 1), "glow"),
        B((15, 5, -16), (39, 11, -14), "cracked"),
    ]
    for i in range(4):
        els.append(B((15.5 + 6.1 * i, 7, -22), (20.5 + 6.1 * i, 17, -20), "stone"))
        els.append(B((15.5 + 6.1 * i, 9, -20.5), (20.5 + 6.1 * i, 17, -14), "cracked"))
    return els


def arm_l_upper():
    els = chain([(-26, 54, 0), (-28, 44, -1), (-25, 35, 1), (-26, 32, 0)], [8, 7.5, 7], "bark")
    els += chain([(-22, 52, 3), (-30, 46, 4), (-23, 38, 3), (-27, 33, 3)], [3, 2.6, 2.4], "bark")
    els += [
        B((-31, 47, -5), (-22, 53, 5), "stone"),
        B((-31, 49, -5), (-22, 54.5, 5), "moss"),
        seg((-29, 41, -2), (-34, 44, -7), 2.2, "bark"),
        seg((-24, 40, 1), (-20, 43, 5), 2.0, "bark"),
    ]
    return els


def arm_l_fore():
    els = chain([(-26, 32, 0), (-24, 22, -2), (-27, 14, 0)], [6.5, 5.5], "bark")
    els += chain([(-29, 30, 2), (-22, 24, 3), (-28, 16, 2)], [2.6, 2.4], "bark")
    els += [
        B((-29, 22, -4.5), (-21, 27, 4.5), "stone"),
        seg((-22, 26, -4), (-18, 22, -8), 1.8, "bark"),
        B((-28.5, 29, -4), (-23, 31.5, 1), "moss"),
    ]
    return els


def claw_l():
    els = [
        B((-32, 9, -4.5), (-22, 16, 4.5), "bark"),
        B((-31, 12, -5), (-23, 15, 5), "stone"),
    ]
    fingers = [(-31, -2.5, -1.5), (-28.5, -3.5, -9.5), (-26, -3.5, -9.0), (-23, -2.5, -7.5)]
    for x, z0, zt in fingers:
        els += chain([(x, 11, z0), (x - 0.5, 5, (z0 + zt) / 2), (x - 1, 0.8, zt)], [3.0, 2.2], "bark")
        els.append(seg((x - 1, 1.5, zt), (x - 1, 0.3, zt - 3), 1.6, "stone"))
    return els


VINES_R = [(20, 10, 22), (26, 11, 28), (31, 9, 20), (36, 3, 24), (35.5, -6, 16)]
VINES_L = [(-18, 11, 24), (-23, 12, 30), (-28, 10, 22), (-33, 4, 26), (-31.5, -8, 18)]


def vines(strands):
    els = []
    for x, z, length in strands:
        els.append(B((x - 0.8, 62 - length, z - 0.8), (x + 0.8, 63, z + 0.8), "vine"))
        for frac in (0.3, 0.62, 1.0):
            y = 62 - length * frac
            els.append(B((x - 2.2, y - 1.5, z - 2), (x + 2.2, y + 1.5, z + 2), "vine"))
    return els


def blossoms(strands):
    els = []
    for x, z, length in strands:
        for frac in (0.45, 1.0):
            y = 62 - length * frac
            c = ((x, y, z), (0, 0, 45))
            els.append(B((x - 3, y - 3, z - 2.7), (x + 3, y + 3, z + 2.7), "blossom"))
            els.append(B((x - 3, y - 3, z - 2.7), (x + 3, y + 3, z + 2.7), "blossom", c))
    return els


def roots_back():
    els = []
    paths = [
        [(-9, 54, 17.5), (-12, 40, 22), (-10, 22, 26), (-13, 3, 31)],
        [(-3, 56, 17.5), (0, 42, 24), (-3, 24, 27), (1, 3, 34)],
        [(4, 54, 17.5), (8, 38, 23), (5, 20, 28), (9, 3, 33)],
        [(10, 52, 17.5), (14, 36, 21), (15, 18, 24), (19, 3, 28)],
    ]
    for pts in paths:
        els += chain(pts, [4, 3.4, 2.8], "bark")
    return els


def roots_low():
    els = []
    paths = [
        [(-8, 31, 9), (-12, 22, 16), (-17, 10, 22), (-20, 1.5, 30)],
        [(0, 28, 9), (1, 18, 17), (-2, 8, 25), (2, 1.5, 35)],
        [(8, 31, 9), (13, 20, 15), (16, 9, 23), (21, 1.5, 31)],
    ]
    for pts in paths:
        els += chain(pts, [4.5, 3.5, 2.6], "bark")
    return els


def crystals_r():
    els = []
    for p, d, length, w in [((24, 64, -4), (0.1, 1, -0.2), 15, 4.4), ((30, 65, 2), (0.3, 1, 0.2), 22, 5.0),
                            ((35, 62, -3), (0.8, 1, -0.1), 13, 3.8), ((27, 64, 7), (0, 0.8, 0.7), 14, 3.6)]:
        els += shard(p, d, length, w)
    return els


def crystals_l():
    els = []
    for p, d, length, w in [((-25, 62, 0), (-0.2, 1, 0.1), 14, 4.0), ((-30, 61, 5), (-0.6, 1, 0.3), 11, 3.4),
                            ((-20, 62, 6), (0, 0.9, 0.6), 12, 3.2)]:
        els += shard(p, d, length, w)
    return els


def crystals_back():
    els = []
    for p, d, length, w in [((0, 61, 14), (0, 0.8, 0.6), 24, 5.0), ((-7, 59, 14), (-0.4, 0.8, 0.5), 16, 4.0),
                            ((7, 59, 14), (0.4, 0.8, 0.5), 18, 4.2), ((-12, 56, 14), (-0.7, 0.6, 0.4), 11, 3.4),
                            ((12, 56, 14), (0.7, 0.6, 0.4), 12, 3.4)]:
        els += shard(p, d, length, w)
    return els


def crown():
    els = [B((-7, 66.5, -9), (7, 68.5, 4.5), "thorn")]
    cx, cz = 0.0, -2.5
    n = 10
    for i in range(n):
        a = 2 * math.pi * i / n
        x, z = cx + 6.4 * math.sin(a), cz - 7.0 * math.cos(a)
        tall = 15 if i % 2 == 0 else 10
        tip = (x * 1.35, 68 + tall, cz + (z - cz) * 1.35)
        els += [seg((x, 68, z), (x * 1.15, 68 + tall * 0.6, cz + (z - cz) * 1.15), 2.2, "thorn"),
                seg((x * 1.15, 68 + tall * 0.6, cz + (z - cz) * 1.15), tip, 1.1, "thorn")]
    return els


def veins_torso():
    zig = lambda pts: chain(pts, [1.6] * (len(pts) - 1), "vein")
    els = zig([(21, 59, -13.5), (25, 55, -13.5), (23, 52, -13.5), (30, 50.8, -13.5)])
    els += zig([(31, 59, -13.5), (28, 56.5, -13.5), (33, 53, -13.5)])
    els += zig([(-32, 60, -9.7), (-27, 56, -9.7), (-30, 52, -9.7), (-22, 51, -9.7)])
    els += zig([(-8, 44, 17.5), (-3, 49, 17.5), (-7, 54, 17.5), (-2, 59, 17.5)])
    els += zig([(4, 45, 17.5), (9, 51, 17.5), (5, 57, 17.5)])
    els += zig([(16.4, 46, -4), (16.4, 51, 1), (16.4, 55, -3), (16.4, 58, 2)])
    els += zig([(-16.4, 46, 4), (-16.4, 50, 0), (-16.4, 54, 3), (-16.4, 57.5, -2)])
    els += zig([(11.4, 36, -3), (11.4, 41, 2), (11.4, 45, -2)])
    els += zig([(-11.4, 36, 3), (-11.4, 40, -1), (-11.4, 45, 3)])
    els += zig([(24, 63.4, -8), (28, 63.4, -2), (25, 63.4, 4), (31, 63.4, 9)])
    els += zig([(-22, 62.4, -6), (-26, 62.4, 0), (-22, 62.4, 6)])
    return els


def veins_pelvis():
    zig = lambda pts: chain(pts, [1.6] * (len(pts) - 1), "vein")
    return zig([(-10.5, 23.5, -11.8), (-13, 27, -11.8), (-11, 30, -11.8), (-14, 32, -11.8)]) +         zig([(10.5, 23.5, -11.8), (13, 27, -11.8), (11, 30, -11.8), (14, 32, -11.8)]) +         zig([(-3, 21.5, -11.4), (-1.5, 25, -11.4), (-3.5, 28.5, -11.4)])


def veins_fist():
    zig = lambda pts: chain(pts, [1.2] * (len(pts) - 1), "vein")
    return zig([(40.4, 6, -8), (40.4, 12, -2), (40.4, 18, -7), (40.4, 25, -1)]) + \
        zig([(18, 28.4, -10), (24, 28.4, -3), (20, 28.4, 3), (28, 28.4, 8)])


# Parts in parent-before-child order. pivot is the joint's absolute rig position in pixels
# (feet at the origin); the file stores it relative to the parent's pivot.
PARTS = [
    ("pelvis", None, (0, 28, 0), pelvis(), ALL),
    ("leg_r", "pelvis", (9, 26, 0), leg(True), ALL),
    ("leg_l", "pelvis", (-9, 26, 0), leg(False), ALL),
    ("torso", "pelvis", (0, 34, 0), torso(), ALL),
    ("plate_r", "torso", (16, 48, -10), plate(), ALL),
    ("plate_l", "torso", (-16, 48, -10), mirror(plate()), ALL),
    ("heart", "torso", (0, 46, -8), heart(), ALL),
    ("head", "torso", (0, 57, -1), head(), ALL),
    ("arm_r_upper", "torso", (27, 54, 0), arm_r_upper(), ALL),
    ("arm_r_fore", "arm_r_upper", (27, 36, 0), arm_r_fore(), ALL),
    ("fist_r", "arm_r_fore", (27, 26, 0), fist_r(), ALL),
    ("arm_l_upper", "torso", (-26, 54, 0), arm_l_upper(), ALL),
    ("arm_l_fore", "arm_l_upper", (-26, 32, 0), arm_l_fore(), ALL),
    ("claw_l", "arm_l_fore", (-27, 14, 0), claw_l(), ALL),
    ("vines_r", "torso", (28, 62, 8), vines(VINES_R), ALL),
    ("vines_l", "torso", (-26, 62, 8), vines(VINES_L), ALL),
    ("roots_back", "torso", (0, 46, 15), roots_back(), ALL),
    ("roots_low", "pelvis", (0, 30, 8), roots_low(), ALL),
    ("roots_leg_r", "leg_r", (17, 16, 0), leg_roots(True), ALL),
    ("roots_leg_l", "leg_l", (-17, 16, 0), leg_roots(False), ALL),
    ("crystals_r", "torso", (28, 62, 0), crystals_r(), UP),
    ("crystals_l", "torso", (-26, 61, 2), crystals_l(), UP),
    ("crystals_back", "torso", (0, 58, 14), crystals_back(), UP),
    ("crown", "head", (0, 67, -2.5), crown(), ["mythic"]),
    ("blossoms_r", "vines_r", (28, 62, 8), blossoms(VINES_R), ["mythic"]),
    ("blossoms_l", "vines_l", (-26, 62, 8), blossoms(VINES_L), ["mythic"]),
    ("veins_torso", "torso", (0, 34, 0), veins_torso(), ["mythic"]),
    ("veins_pelvis", "pelvis", (0, 28, 0), veins_pelvis(), ["mythic"]),
    ("veins_fist_r", "fist_r", (27, 26, 0), veins_fist(), ["mythic"]),
]
ABS_PIVOT = {name: pivot for name, _, pivot, _, _ in PARTS}

# Poses. rot is degrees (x, y, z) applied x first then y then z; offset is pixels (stored as
# blocks). A part a pose does not name keeps the identity. Joint reminders: a positive x turns a
# hanging limb forward (-Z) and a torso top backward (+Z); a positive z swings a hanging limb to +X,
# the right side.
POSES_PX = {
    "idle": {
        "torso": {"rot": (-6, 0, 0)},
        "head": {"rot": (6, 0, 0)},
        "arm_r_upper": {"rot": (3, 0, 3)},
        "arm_r_fore": {"rot": (6, 0, 0)},
        "fist_r": {"rot": (-3, 0, 0)},
        "arm_l_upper": {"rot": (-3, 0, -6)},
        "arm_l_fore": {"rot": (8, 0, 0)},
        "claw_l": {"rot": (10, 0, 0)},
        "vines_r": {"rot": (0, 0, 3)},
        "vines_l": {"rot": (0, 0, -3)},
        "roots_back": {"rot": (3, 0, 0)},
    },
    "walk_a": {
        "pelvis": {"offset": (0, 1, 0), "rot": (0, -4, 0)},
        "torso": {"rot": (-7, 6, 0)},
        "head": {"rot": (7, -5, 0)},
        "leg_r": {"rot": (26, 0, 0)},
        "leg_l": {"rot": (-26, 0, 0)},
        "arm_r_upper": {"rot": (-22, 0, 6)},
        "arm_r_fore": {"rot": (8, 0, 0)},
        "fist_r": {"rot": (-4, 0, 0)},
        "arm_l_upper": {"rot": (26, 0, -8)},
        "arm_l_fore": {"rot": (12, 0, 0)},
        "claw_l": {"rot": (10, 0, 0)},
        "vines_r": {"rot": (-5, 0, 6)},
        "vines_l": {"rot": (-5, 0, -6)},
        "roots_back": {"rot": (8, 0, 3)},
        "roots_low": {"rot": (6, 0, -3)},
        "roots_leg_r": {"rot": (-20, 0, 0)},
        "roots_leg_l": {"rot": (26, 0, 0)},
    },
    "walk_b": {
        "pelvis": {"offset": (0, 1, 0), "rot": (0, 4, 0)},
        "torso": {"rot": (-7, -6, 0)},
        "head": {"rot": (7, 5, 0)},
        "leg_r": {"rot": (-26, 0, 0)},
        "leg_l": {"rot": (26, 0, 0)},
        "arm_r_upper": {"rot": (20, 0, 6)},
        "arm_r_fore": {"rot": (14, 0, 0)},
        "fist_r": {"rot": (-4, 0, 0)},
        "arm_l_upper": {"rot": (-24, 0, -8)},
        "arm_l_fore": {"rot": (6, 0, 0)},
        "claw_l": {"rot": (12, 0, 0)},
        "vines_r": {"rot": (-5, 0, 0)},
        "vines_l": {"rot": (-5, 0, 0)},
        "roots_back": {"rot": (8, 0, -3)},
        "roots_low": {"rot": (6, 0, 3)},
        "roots_leg_r": {"rot": (26, 0, 0)},
        "roots_leg_l": {"rot": (-20, 0, 0)},
    },
    "slam_windup": {
        "pelvis": {"offset": (0, 0, 2)},
        "torso": {"rot": (14, 0, 0)},
        "head": {"rot": (12, 0, 0)},
        "leg_r": {"rot": (-6, 0, 4)},
        "leg_l": {"rot": (8, 0, -4)},
        "arm_r_upper": {"rot": (155, 0, 10)},
        "arm_r_fore": {"rot": (28, 0, 0)},
        "fist_r": {"rot": (14, 0, 0)},
        "arm_l_upper": {"rot": (150, 0, -12)},
        "arm_l_fore": {"rot": (22, 0, 0)},
        "claw_l": {"rot": (-10, 0, 0)},
        "vines_r": {"rot": (14, 0, 8)},
        "vines_l": {"rot": (14, 0, -8)},
        "roots_back": {"rot": (-8, 0, 0)},
    },
    "slam_hit": {
        "pelvis": {"offset": (0, -3, -3)},
        "torso": {"rot": (-34, 0, 0)},
        "head": {"rot": (28, 0, 0)},
        "leg_r": {"rot": (14, 0, 4)},
        "leg_l": {"rot": (-8, 0, -4)},
        "arm_r_upper": {"rot": (58, 0, 4)},
        "arm_r_fore": {"rot": (34, 0, 0)},
        "fist_r": {"rot": (20, 0, 0)},
        "arm_l_upper": {"rot": (60, 0, -6)},
        "arm_l_fore": {"rot": (26, 0, 0)},
        "claw_l": {"rot": (30, 0, 0)},
        "vines_r": {"rot": (-24, 0, 10)},
        "vines_l": {"rot": (-24, 0, -10)},
        "roots_back": {"rot": (22, 0, 0)},
        "roots_low": {"rot": (10, 0, 0)},
    },
    "sweep_windup": {
        "pelvis": {"rot": (0, 10, 0)},
        "torso": {"rot": (-6, 34, 0)},
        "head": {"rot": (6, -30, 0)},
        "leg_r": {"rot": (8, 0, 3)},
        "leg_l": {"rot": (-8, 0, -3)},
        "arm_r_upper": {"rot": (30, 0, 12)},
        "arm_r_fore": {"rot": (40, 0, 0)},
        "fist_r": {"rot": (-10, 0, 0)},
        "arm_l_upper": {"rot": (-42, 0, -88)},
        "arm_l_fore": {"rot": (-14, 0, -6)},
        "claw_l": {"rot": (-20, 0, 0)},
        "vines_l": {"rot": (20, 0, -16)},
        "roots_back": {"rot": (6, 14, 0)},
    },
    "sweep_hit": {
        "pelvis": {"rot": (0, -10, 0)},
        "torso": {"rot": (-10, -38, 0)},
        "head": {"rot": (8, 30, 0)},
        "leg_r": {"rot": (-8, 0, 3)},
        "leg_l": {"rot": (10, 0, -3)},
        "arm_r_upper": {"rot": (-24, 0, 14)},
        "arm_r_fore": {"rot": (30, 0, 0)},
        "fist_r": {"rot": (-6, 0, 0)},
        "arm_l_upper": {"rot": (122, 0, -88)},
        "arm_l_fore": {"rot": (10, 0, 4)},
        "claw_l": {"rot": (-6, 0, 0)},
        "vines_l": {"rot": (-10, 0, 16)},
        "roots_back": {"rot": (6, -14, 0)},
    },
    "rooted": {
        "pelvis": {"offset": (0, -4, 1)},
        "torso": {"rot": (-30, 0, 0)},
        "head": {"rot": (26, 0, 0)},
        "leg_r": {"rot": (22, 0, 20)},
        "leg_l": {"rot": (22, 0, -20)},
        "arm_r_upper": {"rot": (50, 0, 14)},
        "arm_r_fore": {"rot": (30, 0, 0)},
        "fist_r": {"rot": (14, 0, 0)},
        "arm_l_upper": {"rot": (52, 0, -14)},
        "arm_l_fore": {"rot": (28, 0, 0)},
        "claw_l": {"rot": (30, 0, 0)},
        "vines_r": {"rot": (-30, 0, 14)},
        "vines_l": {"rot": (-30, 0, -14)},
        "roots_back": {"rot": (20, 0, 0), "offset": (0, -3, 3)},
        "roots_low": {"rot": (22, 0, 0), "offset": (0, -5, 2)},
        "roots_leg_r": {"rot": (0, 0, 20), "offset": (3, -5, 0)},
        "roots_leg_l": {"rot": (0, 0, -20), "offset": (-3, -5, 0)},
    },
    "heartguard": {
        "torso": {"rot": (8, 0, 0)},
        "head": {"rot": (14, 0, 0)},
        "plate_r": {"rot": (0, -78, 0)},
        "plate_l": {"rot": (0, 78, 0)},
        "heart": {"offset": (0, 0, -2.5), "scale": 1.45},
        "leg_r": {"rot": (-4, 0, 6)},
        "leg_l": {"rot": (4, 0, -6)},
        "arm_r_upper": {"rot": (-6, 0, 32)},
        "arm_r_fore": {"rot": (14, 0, 0)},
        "fist_r": {"rot": (-8, 0, 0)},
        "arm_l_upper": {"rot": (-8, 0, -34)},
        "arm_l_fore": {"rot": (14, 0, 0)},
        "claw_l": {"rot": (6, 0, 0)},
        "vines_r": {"rot": (6, 0, 16)},
        "vines_l": {"rot": (6, 0, -16)},
        "roots_back": {"rot": (-6, 0, 0)},
    },
    "stagger": {
        "pelvis": {"offset": (0, 0, 5), "rot": (6, 0, 0)},
        "torso": {"rot": (26, 4, 0)},
        "head": {"rot": (30, 14, 0)},
        "plate_r": {"rot": (0, -12, 0)},
        "plate_l": {"rot": (0, 12, 0)},
        "leg_r": {"rot": (-20, 0, 6)},
        "leg_l": {"rot": (14, 0, -6)},
        "arm_r_upper": {"rot": (-28, 0, 44)},
        "arm_r_fore": {"rot": (20, 0, 8)},
        "fist_r": {"rot": (-6, 0, 0)},
        "arm_l_upper": {"rot": (-44, 0, -52)},
        "arm_l_fore": {"rot": (16, 0, -8)},
        "claw_l": {"rot": (-14, 0, 0)},
        "vines_r": {"rot": (28, 0, 14)},
        "vines_l": {"rot": (28, 0, -14)},
        "roots_back": {"rot": (-16, 0, 0)},
        "roots_low": {"rot": (-14, 0, 0)},
    },
}

# Named points on parts (parent-local blocks) for the plugin's damage, particles and sounds.
ANCHORS_PX = {
    "heart": ("heart", (0, 0, -4)),
    "eyes": ("head", (0, 4.5, -10.5)),
    "slam_fist": ("fist_r", (0, -24, -3)),
    "sweep_claw": ("claw_l", (0, -13.5, -12)),
}

# Entity: Ravager is 1.95 x 2.2 blocks; the SCALE attribute multiplies both.
HITBOX = {"entity": "ravager", "scaleAttribute": 1.4}


# --- Models -----------------------------------------------------------------------------------------

FACE_DIMS = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}


def face_uv(mat, w, h, seed):
    """A uv rect inside the material's region, 1 texel per rig pixel so density matches 16 px/block."""
    x0, y0, x1, y1 = REGIONS[mat]
    rw, rh = x1 - x0, y1 - y0
    if mat in TILED:
        pw, ph = min(rw, max(2.0, round(w))), min(rh, max(2.0, round(h)))
        u0 = x0 + (seed * 7) % max(1, int(rw - pw) + 1)
        v0 = y0 + (seed * 13) % max(1, int(rh - ph) + 1)
        r = ATLAS / 16
        return [u0 / r, v0 / r, (u0 + pw) / r, (v0 + ph) / r]
    r = ATLAS / 16
    return [x0 / r, y0 / r, x1 / r, y1 / r]


def part_model(name, elements, pivot):
    def at(v):
        return [round((v[k] - pivot[k]) / MODEL_SCALE + 8, 3) for k in range(3)]

    out = []
    for i, e in enumerate(elements):
        f, t = at(e["from"]), at(e["to"])
        for v in f + t:
            if v < -16 or v > 32:
                raise SystemExit(f"{name}: element {i} {f}..{t} leaves the -16..32 model range")
        size = [e["to"][k] - e["from"][k] for k in range(3)]
        faces = {}
        for face, (a, b) in FACE_DIMS.items():
            entry = {"uv": face_uv(e["mat"], size[a], size[b], i * 6 + len(faces)), "texture": "#t"}
            if TINT.get(e["mat"]) is not None:
                entry["tintindex"] = TINT[e["mat"]]
            faces[face] = entry
        el = {"from": f, "to": t, "faces": faces}
        if e["rot"]:
            o, (rx, ry, rz) = e["rot"]
            el["rotation"] = {"origin": at(o), "x": rx, "y": ry, "z": rz}
        if e["mat"] in EMISSION:
            el["light_emission"] = EMISSION[e["mat"]]
        out.append(el)
    return {"textures": {"t": TEX_ID, "particle": TEX_ID}, "elements": out}


def item_definition(name):
    tints = [{"type": "minecraft:custom_model_data", "index": i, "default": c}
             for i, c in enumerate(PALETTES["normal"])]
    return {"model": {"type": "minecraft:model", "model": f"claudesmp:item/guardian/sentinel_{name}", "tints": tints}}


# --- Texture ----------------------------------------------------------------------------------------

def voronoi(w, h, n, rng):
    """Nearest and second-nearest seed distance per pixel on a torus, plus the nearest seed index."""
    pts = rng.random((n, 2)) * [w, h]
    ys, xs = np.mgrid[0:h, 0:w] + 0.5
    d = []
    for px, py in pts:
        dx = np.abs(xs - px)
        dy = np.abs(ys - py)
        d.append(np.hypot(np.minimum(dx, w - dx), np.minimum(dy, h - dy)))
    d = np.stack(d)
    s = np.sort(d, axis=0)
    return np.argmin(d, axis=0), s[0], s[1]


def smooth_noise(w, h, cell, rng):
    grid = rng.random((h // cell + 2, w // cell + 2)) * 255
    img = Image.fromarray(grid.astype(np.uint8)).resize((w // cell * cell + cell * 2, h // cell * cell + cell * 2),
                                                        Image.BICUBIC)
    return np.asarray(img, float)[:h, :w] / 255


def cracks(g, rng, count, depth, length):
    h, w = g.shape
    for _ in range(count):
        x, y = rng.random() * w, rng.random() * h
        a = rng.random() * 2 * math.pi
        for _ in range(int(length * (0.6 + rng.random()))):
            g[int(y) % h, int(x) % w] -= depth
            a += rng.normal(0, 0.5)
            x += math.cos(a)
            y += math.sin(a)
    return g


def plates(w, h, n, rng, base, spread, edge, crack_count):
    idx, d1, d2 = voronoi(w, h, n, rng)
    g = (base + rng.uniform(-spread, spread, n))[idx]
    e = d2 - d1
    g = np.where(e < edge, g - 62, g)
    g = np.where((e >= edge) & (e < edge * 2.6), g + 12, g)
    g += rng.normal(0, 5, (h, w))
    g[rng.random((h, w)) < 0.05] -= 26
    g[rng.random((h, w)) < 0.03] += 20
    g = cracks(g, rng, crack_count, 55, 22)
    return np.round(g / 10) * 10


def texture():
    rng = np.random.default_rng(11)
    img = np.zeros((ATLAS, ATLAS, 4), np.uint8)

    def put(region, rgb):
        x0, y0, x1, y1 = REGIONS[region]
        rgb = np.broadcast_to(np.asarray(rgb, float), (y1 - y0, x1 - x0, 3))
        img[y0:y1, x0:x1, :3] = np.clip(rgb, 0, 255)
        img[y0:y1, x0:x1, 3] = 255

    def grey(g):
        return np.repeat(g[:, :, None], 3, 2)

    put("stone", grey(plates(64, 64, 15, rng, 218, 18, 0.9, 4)))
    put("cracked", grey(plates(64, 64, 24, rng, 190, 20, 1.1, 9)))

    # Moss: clumped three-tone noise with darker pores.
    n = 0.6 * smooth_noise(64, 48, 6, rng) + 0.4 * smooth_noise(64, 48, 3, rng)
    moss = np.where(n > 0.62, 240, np.where(n > 0.38, 196, 150)) + rng.normal(0, 5, (48, 64))
    moss[rng.random((48, 64)) < 0.05] = 110
    put("moss", grey(moss))

    # Bark: brown vertical grain with knots. Untinted, so this is real colour.
    ys, xs = np.mgrid[0:48, 0:64]
    grain = np.sin(xs * 1.7 + 2.5 * smooth_noise(64, 48, 8, rng)) * 0.5 + 0.5
    knots = np.zeros((48, 64))
    for _ in range(6):
        kx, ky = rng.random() * 64, rng.random() * 48
        knots += np.exp(-(((xs - kx) / 3.0) ** 2 + ((ys - ky) / 5.0) ** 2))
    light = 0.55 + 0.45 * grain - 0.35 * np.clip(knots, 0, 1) + rng.normal(0, 0.04, (48, 64))
    light = np.round(light * 8) / 8
    put("bark", np.stack([140 * light + 20, 98 * light + 14, 62 * light + 8], 2))

    # Vines: leaf blobs with a midrib.
    ys, xs = np.mgrid[0:16, 0:32]
    leaf = 215 - 45 * (((xs % 8) == 3) | ((ys % 8) == 4)) + rng.normal(0, 8, (16, 32))
    leaf[rng.random((16, 32)) < 0.18] = 140
    put("vine", grey(np.round(leaf / 12) * 12))

    # Glow: hot core fading outward, darker network lines so the heart reads as cracked.
    ys, xs = np.mgrid[0:16, 0:16]
    r = np.hypot(xs - 7.5, ys - 7.5) / 8
    core = 255 - 80 * np.clip(r, 0, 1) ** 1.6 + rng.normal(0, 4, (16, 16))
    core = np.where(((xs + ys) % 5 == 0) & (r > 0.45), core - 45, core)
    put("glow", grey(np.round(core / 8) * 8))
    eye = np.full((8, 8), 215.0)
    eye[1:7, 1:7] = 240
    eye[2:6, 2:6] = 255
    put("eye", grey(eye))

    # Crystal: faceted bands with white glints.
    ys, xs = np.mgrid[0:16, 0:16]
    band = ((xs * 2 + ys) // 3) % 4
    crystal = np.choose(band, [255, 232, 206, 240]) + rng.normal(0, 3, (16, 16))
    crystal[(xs + ys) % 11 == 0] = 255
    put("crystal", grey(crystal))

    blossom = np.zeros((8, 8, 3))
    blossom[:] = (246, 148, 190)
    blossom[(np.mgrid[0:8, 0:8][0] // 2 + np.mgrid[0:8, 0:8][1] // 2) % 2 == 1] = (255, 244, 248)
    blossom[3:5, 3:5] = (255, 214, 96)
    put("blossom", blossom)

    ys, _ = np.mgrid[0:8, 0:8]
    put("thorn", grey(250 - ys * 9.0))
    ys, xs = np.mgrid[0:16, 0:16]
    put("vein", grey(255 - 45 * (np.abs(ys - 7.5) / 8) ** 2 + rng.normal(0, 3, (16, 16))))
    return Image.fromarray(img, "RGBA")


# --- Rig file ---------------------------------------------------------------------------------------

DOC = [
    "Overgrown Sentinel rig. Single source of truth for the pack generator, the Python preview twin and the Java runtime.",
    "UNITS: every length is in blocks (1 block = 16 model pixels). Angles are degrees. Matrices are column-vector (v' = M * v), right-handed, as in JOML.",
    "RIG FRAME: origin on the ground under the body's centre (feet at y = 0). +Y up. -Z is the front (the way the Sentinel faces). +X is the character's own right (the slam-fist side). -X is its left (the root-claw side).",
    "PARTS: array in parent-before-child order. name is unique. model is the item_model id to put on the display's item (ItemStack PAPER with meta.setItemModel(NamespacedKey claudesmp:guardian/sentinel_<part>)). parent is a part name or null. pivot is the joint position in the PARENT's local frame (the frame whose origin is the parent's pivot, before the parent's pose), or relative to the rig origin (feet) when parent is null. scale is a uniform extra scale for that part (1.0 = as authored). tiers lists the tiers that spawn this part.",
    "MODEL ORIGIN: an item display with ItemDisplayTransform NONE draws its item model with the model-space point (8, 8, 8) (the centre of the 0..16 cube) exactly at the display's own origin. Verified in the 26.3 client jar: ItemStackRenderState$LayerRenderState.applyTransform calls ItemTransform.apply, whose NO_TRANSFORM path and its normal path both end with PoseStack$Pose.translate(-0.5, -0.5, -0.5) after the model has been divided by 16. Every part model here is authored so its pivot sits at model point (8, 8, 8): the pivot IS the display origin, and a display rotates and scales about its pivot.",
    "MODEL SCALE: models are drawn at 1/modelScale of their true size so a part fits the item model's -16..32 range. A model point m (model units) therefore lands, in the part's own frame, at (m - 8) / 16 * modelScale * part.scale blocks from the pivot.",
    "POSE: poses.<name>.<part> = { rot: [x, y, z] degrees, offset: [dx, dy, dz] blocks, scale: number } and any key may be missing (identity rot, zero offset, scale 1). Rotation matrix R = Rz(z) * Ry(y) * Rx(x), i.e. rotate about X first, then Y, then Z, each about the part's pivot, in the parent's frame (JOML rotationZYX(z, y, x)). Rx(a)(0,-1,0) = (0,-cos a,-sin a): a positive x swings a hanging limb forward (-Z) and tips an upright torso backward (+Z). Rz(a)(0,-1,0) = (sin a,-cos a,0): a positive z swings a hanging limb to +X (the right). Ry(a)(-1,0,0) = (-cos a,0,sin a): a positive y turns the part to its left. pose.scale is optional, multiplies only this part's own drawn size (children are unaffected, it never enters the parent chain) and is used for the heart's pulse.",
    "COMPOSITION: local(part) = Translate(pivot + pose.offset) * R(pose.rot). world(part) = world(parent) * local(part), with world(null) = identity. rigMatrix(part) = world(part) * Scale(part.scale * pose.scale * modelScale). A model point m (model units) is at rigMatrix(part) * ((m - 8) / 16) in the rig frame. Note world(parent) carries the parent's rotation and offset but NOT its scale.",
    "DISPLAYS: spawn every part as an ItemDisplay at the base mob's FEET position, with the base mob's yaw as the display's yaw, pitch 0, billboard FIXED, ItemDisplayTransform NONE, and put the part's whole placement into the display Transformation. The 26.3 client draws a display as: Ry(-yaw) * Transformation(T * Lrot * S * Rrot) * Ry(pi) * ((m - 8) / 16), and the Ry(pi) turns a model's -Z side to face the way the entity looks. The rig frame is the model frame, so the Transformation must be conjugated by Ry(pi) = diag(-1, 1, -1): Transformation = Ry(pi) * rigMatrix * Ry(pi).",
    "TRANSFORMATION FIELDS: for Bukkit Transformation(translation, leftRotation, scale, rightRotation): translation = (-t.x, t.y, -t.z) where t is the translation column of rigMatrix(part) (= world(part) origin); leftRotation = quaternion (-q.x, q.y, -q.z, q.w) where q is the quaternion of the rotation part of world(part); scale = (s, s, s) with s = part.scale * pose.scale * modelScale; rightRotation = identity. Set the interpolation duration and start-interpolation on the display to animate between poses client-side.",
    "FACING: rig -Z follows the entity yaw (yaw 0 = south = +Z in the world, so the Sentinel looks along +Z at yaw 0). world position of a model point = feet + Ry(-yaw) * Transformation * Ry(pi) * ((m - 8) / 16). Ry(a) is the standard matrix [[cos,0,sin],[0,1,0],[-sin,0,cos]].",
    "PALETTES: palettes.<tier> = [stone, moss, glow] as 0xRRGGBB ints, for custom_model_data colour indices 0, 1, 2. Set them with CustomModelDataComponent.setColors(List.of(Color.fromRGB(...) x3)) on every part's item. Glow tints the heart, eyes, plate slits, fist seams, crystals, crown thorns and vein seams; bark and blossoms are untinted.",
    "TIERS: a part is spawned only when its tiers contains the fight's tier (normal, heroic, mythic). Heroic adds crystals_r, crystals_l, crystals_back; mythic keeps those and adds crown, blossoms_r, blossoms_l, veins_torso, veins_pelvis, veins_fist_r. blossoms_* are children of vines_*, veins_* of the part they trace, so they move with it.",
    "POSES: idle, walk_a and walk_b (alternate them for the stride), slam_windup (both arms raised), slam_hit (both fists slammed down in front), sweep_windup (torso twisted, root arm drawn back to its left), sweep_hit (root arm swept across the front), rooted (crouched, roots dug in, the healing phase), heartguard (chest plates open, heart exposed and enlarged), stagger (reeling back, phase transitions). Blend between poses by interpolating rot/offset/scale per part, or rely on display interpolation.",
    "ANCHORS: anchors.<name> = { part, local: [x, y, z] blocks } a point in that part's local frame (origin at its pivot, before scale). World position in the rig frame = world(part) * local (no part scale). Use for the heart, the eyes, the slam impact point under the fist and the claw tip. Convert rig -> world with feet + Ry(-yaw) * Ry(pi) * rigPoint (rig -Z becomes the entity's forward).",
    "HITBOX: hitbox.entity is the invisible base mob and hitbox.scaleAttribute the generic.scale to give it; the Ravager's 1.95 x 2.2 box then covers the body's lower two thirds (about 2.7 wide, 3.1 tall). The body stands about 4.3 blocks tall to the top of the head (horns and spikes reach about 4.8), about 4.7 wide from fist to claw.",
]


def blocks(v):
    return [round(x / 16, 5) for x in v]


def rig_json():
    parts = []
    for name, parent, pivot, _, tiers in PARTS:
        rel = pivot if parent is None else [pivot[k] - ABS_PIVOT[parent][k] for k in range(3)]
        parts.append({"name": name, "model": f"claudesmp:guardian/sentinel_{name}", "parent": parent,
                      "pivot": blocks(rel), "scale": 1.0, "tiers": tiers})
    poses = {}
    for pose, entries in POSES_PX.items():
        poses[pose] = {}
        for part, spec in entries.items():
            out = {}
            if "rot" in spec:
                out["rot"] = [float(a) for a in spec["rot"]]
            if "offset" in spec:
                out["offset"] = blocks(spec["offset"])
            if "scale" in spec:
                out["scale"] = spec["scale"]
            poses[pose][part] = out
    return {
        "_doc": DOC,
        "id": "overgrown_sentinel",
        "modelScale": MODEL_SCALE,
        "tiers": ALL,
        "hitbox": HITBOX,
        "palettes": PALETTES,
        "parts": parts,
        "poses": poses,
        "anchors": {k: {"part": p, "local": blocks(v)} for k, (p, v) in ANCHORS_PX.items()},
    }


def outputs():
    """Every file the generator owns: (path, dict for JSON or a PIL image for the atlas)."""
    out = [(TEX_PATH, texture())]
    for name, _, pivot, elements, _ in PARTS:
        out.append((MODEL_DIR / f"sentinel_{name}.json", part_model(name, elements, pivot)))
        out.append((ITEMS_DIR / f"sentinel_{name}.json", item_definition(name)))
    out.append((RIG_PATH, rig_json()))
    return out


def main():
    for path, data in outputs():
        if isinstance(data, Image.Image):
            path.parent.mkdir(parents=True, exist_ok=True)
            data.save(path)
        else:
            write_json(path, data)
    print(f"wrote {len(PARTS)} sentinel parts, the atlas and {RIG_PATH.name}")


if __name__ == "__main__":
    main()
