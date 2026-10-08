"""Generates the Dragon Balls summon dragons: Shenron (a long serpent built from a head, body
segments and a tail tip) and Porunga (a hulking upper body - head, torso with shoulder
spikes and a back crest, two arms - over a serpent tail built like Shenron's body), as item models of claudesmp-items.

The dragonballs plugin spawns one item display per part (DragonModel.java) and moves them, so the
real Ender Dragon keeps its vanilla look (Joe, 2026-10-08: "the actual normal ender dragon isn't
affected by the skin"). Every part's item definition, assets/claudesmp/items/dragon/<part>.json,
tints its model from the item's custom_model_data colours: index 0 is the body colour and index 1
the belly/accent colour, so one model covers every set (green Overworld Shenron and Porunga, red
Nuova Shenron and purple Porunga in the Nether, gold Super Shenron and Porunga in the End). Eyes,
horns, teeth, claws and whiskers are untinted, and eyes glow (light_emission 15).

Parts face north (-Z) with up +Y, and each part's pivot sits at the model's (8, 8, 8), which an
item display renders at its own position. Rig coordinates below are in model units (1/16 block)
around each dragon's own origin; `part()` shifts them so the part's pivot lands on (8, 8, 8), and
refuses any element outside the item model's -16..32 range.

Run from anywhere: python server/resourcepacks/dragon_models.py
"""

import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / "server" / "resourcepacks" / "claudesmp-items" / "assets" / "claudesmp"
TEX = "claudesmp:item/dragon/parts"
TEX_PATH = PACK / "textures" / "item" / "dragon" / "parts.png"
MODEL_DIR = PACK / "models" / "item" / "dragon"
ITEMS_DIR = PACK / "items" / "dragon"

# Texture regions in pixels of the 64x64 atlas: (x0, y0, x1, y1).
REGIONS = {
    "scales": (0, 0, 32, 32),
    "belly": (32, 0, 64, 16),
    "skin": (32, 16, 64, 32),
    "eye": (0, 32, 8, 40),
    "horn": (8, 32, 16, 40),
    "teeth": (16, 32, 24, 40),
    "mouth": (24, 32, 32, 40),
    "dark": (32, 32, 40, 40),
    "claw": (40, 32, 48, 40),
    "mane": (0, 40, 16, 56),
    "fin": (48, 32, 64, 48),
    "spike": (16, 40, 24, 48),
    "pale": (24, 40, 48, 56),
}
# Which tint each material takes: 0 body colour, 1 belly/accent colour, None untinted.
TINT = {"scales": 0, "skin": 0, "mane": 0, "fin": 0, "belly": 1, "pale": 1}
GLOWING = {"eye"}

# Default colours (the Overworld pair), used when an item carries no custom_model_data colours.
DEFAULT_BODY = 0x3E9A3A
DEFAULT_ACCENT = 0xD9C77A


def box(f, t, mat, rot=None, faces=None):
    """One cuboid element in rig coordinates. rot: (origin, (x, y, z) degrees), Euler XYZ."""
    return {"from": list(f), "to": list(t), "mat": mat, "rot": rot, "faces": faces}


def mirror(elements):
    """Mirrors elements across x = 0 (rig coordinates are centred on x = 0)."""
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


def sym(*elements):
    return list(elements) + mirror(elements)


# --- Shenron -------------------------------------------------------------------------------------
# Head pivot (0, 0, 0) is where the neck joins: the snout reaches forward to z = -30.

SHENRON_HEAD = [
    box((-8, -6, -10), (8, 10, 8), "scales"),                      # cranium
    box((-9, 6, -14), (9, 11, -6), "scales"),                      # brow ridge
    box((-6, -4, -28), (6, 4, -8), "scales"),                      # upper snout
    box((-5, 4, -24), (5, 6, -8), "scales", ((0, 4, -8), (-10, 0, 0))),  # snout bridge
    box((-5.5, -2, -30), (5.5, 5, -26), "scales"),                 # nose pad
    box((-5, -5, -27), (5, -4, -9), "teeth"),                      # upper teeth row
    box((-5, -10, -26), (5, -6, -6), "scales", ((0, -6, -6), (-16, 0, 0))),   # lower jaw
    box((-4, -7, -24), (4, -5.5, -7), "mouth", ((0, -6, -6), (-16, 0, 0))),   # tongue / mouth
    box((-4.5, -11, -24), (4.5, -10, -6), "belly", ((0, -6, -6), (-16, 0, 0))),  # chin plate
    box((-3, -16, -12), (3, -9, -6), "mane"),                      # beard tuft
    box((-9.5, -5, -14), (-6.5, 7, -2), "scales"),                 # cheek
    box((6.5, -5, -14), (9.5, 7, -2), "scales"),
    box((-9.2, 6, -12.5), (-6, 9, -8), "eye"),                     # eyes
    box((6, 6, -12.5), (9.2, 9, -8), "eye"),
    box((-4.5, 2, -30.2), (-2, 4, -29), "dark"),                   # nostrils
    box((2, 2, -30.2), (4.5, 4, -29), "dark"),
]
SHENRON_HEAD += sym(
    box((-6.5, -9, -27), (-5, -4, -25.5), "teeth"),                # fangs
    box((-6, 9, 0), (-3.5, 11.5, 22), "horn", ((-4.75, 10, 0), (-28, -10, 0))),       # horn
    box((-6, 10, 8), (-4, 12, 18), "horn", ((-5, 11, 8), (-62, -10, 0))),            # horn prong
    box((-10, 2, -6), (-7, 7, 4), "mane", ((-8, 4, -6), (0, -12, 0))),               # ear
    box((-6.5, -1, -26), (-5.5, 0, 18), "dark", ((-6, -0.5, -26), (18, -38, 0))),    # whisker
    box((-6.2, -3, -22), (-5.5, -2.3, 12), "dark", ((-6, -2.5, -22), (34, -48, 0))),  # lower whisker
)
SHENRON_HEAD += [
    box((-1, 8, -2 + i * 6), (1, 15 - i, 4 + i * 6), "fin", ((0, 9, 1 + i * 6), (-30, 0, 0)))
    for i in range(3)                                              # mane spikes down the skull
]


def shenron_segment(legs):
    els = [
        box((-7, -7, -12), (7, 7, 12), "scales"),                  # core
        box((-8, -5, -11), (8, 5, 11), "scales"),                  # side roundness
        box((-5, -8, -11), (5, 8, 11), "scales"),                  # top roundness
        box((-5, -8.6, -12), (5, -6.5, 12), "belly"),              # belly plates
        box((-1, 6, -8), (1, 12, -1), "fin", ((0, 7, -4), (-35, 0, 0))),  # spine spikes
        box((-1, 6, 2), (1, 11, 8), "fin", ((0, 7, 5), (-35, 0, 0))),
    ]
    if legs:
        els += sym(
            box((-12, -6, -4), (-6, 2, 3), "scales", ((-7, -2, 0), (0, 0, -35))),       # upper leg
            box((-15, -14, -5), (-10, -5, 1), "scales", ((-12, -6, -2), (20, 0, -10))),  # lower leg
            box((-16, -16, -9), (-9, -13, 0), "scales"),                                # foot
            box((-16, -16, -12), (-14.6, -14, -8), "claw"),                             # claws
            box((-13.2, -16, -12.5), (-11.8, -14, -8), "claw"),
            box((-10.4, -16, -12), (-9, -14, -8), "claw"),
        )
    return els


SHENRON_TAIL = [
    box((-6, -6, -12), (6, 6, 6), "scales"),
    box((-4.5, -4.5, 4), (4.5, 4.5, 16), "scales"),
    box((-4, -6.6, -12), (4, -5.5, 14), "belly"),
    box((-3, -3, 14), (3, 3, 22), "scales"),
    box((-1, -10, 16), (1, 10, 30), "mane", ((0, 0, 18), (-15, 0, 0))),     # tail tuft
    box((-1, -10, 16), (1, 10, 30), "mane", ((0, 0, 18), (15, 0, 0))),
    box((-8, -1, 16), (8, 1, 30), "mane", ((0, 0, 18), (0, 15, 0))),
]

# --- Porunga -------------------------------------------------------------------------------------
# Rig: feet at y = 0, about 80 units (5 blocks) tall before the display's own scale.

PORUNGA_TORSO = [
    box((-9, 0, -7), (9, 14, 5), "belly"),                         # abs
    box((-10, 0, -5), (10, 14, 8), "skin"),                        # waist sides and back
    box((-13, 12, -9), (13, 27, 4), "pale"),                       # bare chest
    box((-16, 10, -3), (16, 34, 13), "skin"),                      # hunched back
    box((-13, 27, -5), (13, 38, 11), "skin"),                      # traps, above the head
]
PORUNGA_TORSO += sym(
    box((-12.5, 18, -11), (-0.5, 27, -8), "pale"),                 # pec
    box((-25, 21, -8), (-13, 37, 9), "skin"),                      # shoulder
    box((-28, 33, -2.5), (-22, 38, 2.5), "spike", ((-24, 35, 0), (0, 0, -22))),  # shoulder spike
    box((-35, 34, -1.5), (-27, 37, 1.5), "spike", ((-24, 35, 0), (0, 0, -22))),
)
PORUNGA_TORSO += [
    box((-1.5, 30, 2 + i * 3), (1.5, 52 - i * 4, 4 + i * 3), "fin", ((0, 34, 3), (18, 0, 0)))
    for i in range(3)                                              # ribbed crest on the back
]
PORUNGA_TORSO += [box((-1, 30, 1), (1, 50, 12), "fin", ((0, 34, 3), (18, 0, 0)))]

PORUNGA_HEAD = [
    box((-7, 22, -18), (7, 34, -6), "skin"),                       # skull, hunched low
    box((-7.5, 30, -19), (7.5, 34, -14), "skin"),                  # heavy brow
    box((-5, 22, -24), (5, 29, -17), "skin"),                      # muzzle
    box((-4.5, 18.5, -22), (4.5, 22.5, -11), "skin"),              # lower jaw
    box((-4.6, 22, -24.2), (4.6, 23, -21.5), "mouth"),             # mouth line
    box((-5.5, 28.5, -19.2), (-1.5, 31, -18), "eye"),              # eyes
    box((1.5, 28.5, -19.2), (5.5, 31, -18), "eye"),
    box((-3, 26, -24.2), (-1, 27.5, -23.6), "dark"),               # nostrils
    box((1, 26, -24.2), (3, 27.5, -23.6), "dark"),
]
PORUNGA_HEAD += sym(
    box((-4, 20.5, -24.4), (-2.5, 23, -23), "teeth"),              # fangs
    box((-6.5, 32, -15), (-3, 42, -11), "spike", ((-5, 33, -13), (-12, 0, 16))),   # horn
    box((-5.5, 41, -13), (-3.5, 47, -11), "spike", ((-5, 33, -13), (-20, 0, 22))),
    box((-11, 26, -12), (-6.5, 31, -9), "skin", ((-7, 28, -10), (0, 0, -25))),     # cheek fin
)

PORUNGA_ARM_LEFT = [
    box((-27, 10, -7), (-15, 31, 7), "skin", ((-21, 31, 0), (0, 0, -12))),        # upper arm
    box((-28, -6, -10), (-16, 12, 2), "skin", ((-22, 12, -2), (22, 0, -10))),     # forearm
    box((-27, -5, -11), (-17, 10, -9.5), "pale", ((-22, 12, -2), (22, 0, -10))),  # pale inner forearm
    box((-29, -15, -15), (-15, -5, -2), "skin", ((-22, 12, -2), (22, 0, -10))),   # hand
    box((-29.5, -18, -16), (-27, -13, -13), "claw", ((-22, 12, -2), (22, 0, -10))),
    box((-25, -18, -16.5), (-22.5, -13, -13.5), "claw", ((-22, 12, -2), (22, 0, -10))),
    box((-20.5, -18, -16), (-18, -13, -13), "claw", ((-22, 12, -2), (22, 0, -10))),
    box((-16.5, -16, -11), (-14, -11, -8), "claw", ((-22, 12, -2), (22, 0, -10))),
]

# The tail is a chain of these, waist to tip, like Shenron's body: green back, pale front.
PORUNGA_TAIL = [
    box((-8, -8, -12), (8, 8, 12), "skin"),
    box((-9, -6, -11), (9, 6, 11), "skin"),
    box((-6.5, -9, -12), (6.5, -6, 12), "pale"),
]
PORUNGA_TAIL_TIP = [
    box((-7, -7, -12), (7, 7, 6), "skin"),
    box((-5.5, -8, -12), (5.5, -6, 6), "pale"),
    box((-5, -5, 4), (5, 5, 16), "skin"),
    box((-3, -3, 14), (3, 3, 26), "skin"),
    box((-1.5, -1.5, 24), (1.5, 1.5, 32), "skin"),
]

# Each dragon's rig is drawn at MODEL_SCALE times its model size, so it can be bigger than an item
# model's 48-unit range; DragonModel.java multiplies every display's scale back up by the same
# factor, so the parts meet again in game.
MODEL_SCALE = 1.5

# Each part: (rig elements, pivot in rig coordinates).
PARTS = {
    "shenron_head": (SHENRON_HEAD, (0, 0, -6)),
    "shenron_body": (shenron_segment(False), (0, 0, 0)),
    "shenron_body_legs": (shenron_segment(True), (0, -3, 0)),
    "shenron_tail": (SHENRON_TAIL, (0, 0, 8)),
    "porunga_torso": (PORUNGA_TORSO, (0, 22, 2)),
    "porunga_head": (PORUNGA_HEAD, (0, 30, -12)),
    "porunga_arm_left": (PORUNGA_ARM_LEFT, (-21, 10, -4)),
    "porunga_arm_right": (mirror(PORUNGA_ARM_LEFT), (21, 10, -4)),
    "porunga_tail": (PORUNGA_TAIL, (0, 0, 0)),
    "porunga_tail_tip": (PORUNGA_TAIL_TIP, (0, 0, 8)),
}


def face_uv(mat, w, h, seed):
    """A uv rect inside the material's region, sized to the face so pattern density stays even."""
    x0, y0, x1, y1 = REGIONS[mat]
    rw, rh = x1 - x0, y1 - y0
    if mat in ("scales", "skin", "belly", "mane", "fin", "pale"):
        pw, ph = min(rw, max(2.0, w)), min(rh, max(2.0, h))  # 1 px per model unit
        ox = (seed * 7) % max(1, int(rw - pw) + 1)
        oy = (seed * 13) % max(1, int(rh - ph) + 1)
        u0, v0 = x0 + ox, y0 + oy
        return [u0 / 4, v0 / 4, (u0 + pw) / 4, (v0 + ph) / 4]
    return [x0 / 4, y0 / 4, x1 / 4, y1 / 4]


FACE_DIMS = {  # face -> which two box extents (x, y, z indices) span it
    "north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2),
}


def part(name, elements, pivot):
    def at(v):
        return [round((v[k] - pivot[k]) / MODEL_SCALE + 8, 3) for k in range(3)]

    out = []
    for i, e in enumerate(elements):
        f, t = at(e["from"]), at(e["to"])
        for v in f + t:
            if v < -16 or v > 32:
                raise SystemExit(f"{name}: element {i} {f}..{t} leaves the -16..32 model range")
        size = [(t[k] - f[k]) * MODEL_SCALE for k in range(3)]
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
        if e["mat"] in GLOWING:
            el["light_emission"] = 15
        out.append(el)
    return {"textures": {"t": TEX, "particle": TEX}, "elements": out}


def item_definition(name):
    return {
        "model": {
            "type": "minecraft:model",
            "model": f"claudesmp:item/dragon/{name}",
            "tints": [
                {"type": "minecraft:custom_model_data", "index": 0, "default": DEFAULT_BODY},
                {"type": "minecraft:custom_model_data", "index": 1, "default": DEFAULT_ACCENT},
            ],
        }
    }


# --- Texture -------------------------------------------------------------------------------------

def texture():
    rng = np.random.default_rng(7)
    img = np.zeros((64, 64, 4), np.uint8)

    def put(region, rgb):
        x0, y0, x1, y1 = REGIONS[region]
        img[y0:y1, x0:x1, :3] = np.clip(rgb, 0, 255)
        img[y0:y1, x0:x1, 3] = 255

    # Scales: overlapping arcs, light in the middle of each scale, dark at its rim.
    ys, xs = np.mgrid[0:32, 0:32]
    best = np.full((32, 32), 9.0)
    for row in range(-1, 9):
        for col in range(-1, 9):
            cx = col * 4 + (2 if row % 2 else 0)
            cy = row * 4
            d = np.hypot(xs - cx, (ys - cy) * 1.1) / 3.2
            best = np.where((ys >= cy) & (d < best), d, best)
    best = np.where(best > 8, 1.0, best)
    shade = 245 - 85 * np.clip(best, 0, 1) ** 2 + rng.normal(0, 5, (32, 32))
    put("scales", np.repeat(shade[:, :, None], 3, 2))

    # Belly: horizontal plates.
    ys, xs = np.mgrid[0:16, 0:32]
    plate = 235 - 55 * ((ys % 4) == 3) - 12 * ((ys % 4) == 2) + rng.normal(0, 4, (16, 32))
    put("belly", np.repeat(plate[:, :, None], 3, 2))

    # Skin: Porunga's green hide, soft mottling with the dark speckles of the anime.
    ys, xs = np.mgrid[0:16, 0:32]
    skin = 228 + 14 * np.sin(xs * 0.7) * np.sin(ys * 0.9) + rng.normal(0, 5, (16, 32))
    skin[rng.random((16, 32)) < 0.07] = 120
    put("skin", np.repeat(skin[:, :, None], 3, 2))
    # Pale: his bare chest and belly side, smooth with faint muscle lines.
    ys, xs = np.mgrid[0:16, 0:24]
    pale = 240 - 30 * ((ys % 5) == 4) - 20 * (xs == 12) + rng.normal(0, 3, (16, 24))
    put("pale", np.repeat(pale[:, :, None], 3, 2))

    eye = np.zeros((8, 8, 3))
    eye[:] = (200, 20, 20)
    eye[2:6, 2:6] = (255, 60, 40)
    eye[2:4, 2:4] = (255, 220, 200)
    put("eye", eye)
    ys, _ = np.mgrid[0:8, 0:8]
    put("horn", np.stack([236 - ys * 8, 220 - ys * 9, 180 - ys * 10], 2))
    put("teeth", np.full((8, 8, 3), (246, 244, 236)))
    put("mouth", np.full((8, 8, 3), (110, 20, 30)))
    put("dark", np.full((8, 8, 3), (38, 40, 36)))
    put("claw", np.stack([240 - ys * 6, 232 - ys * 7, 205 - ys * 9], 2))
    put("spike", np.stack([24 + ys * 5, 24 + ys * 5, 30 + ys * 5], 2))
    ys, xs = np.mgrid[0:16, 0:16]
    mane = 150 - 30 * ((xs % 3) == 0) + rng.normal(0, 8, (16, 16))
    put("mane", np.repeat(mane[:, :, None], 3, 2))
    fin = 175 - 40 * ((xs + ys) % 4 == 0) + rng.normal(0, 5, (16, 16))
    put("fin", np.repeat(fin[:, :, None], 3, 2))
    return Image.fromarray(img, "RGBA")


def main():
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    ITEMS_DIR.mkdir(parents=True, exist_ok=True)
    TEX_PATH.parent.mkdir(parents=True, exist_ok=True)
    texture().save(TEX_PATH)
    for name, (elements, pivot) in PARTS.items():
        (MODEL_DIR / f"{name}.json").write_text(json.dumps(part(name, elements, pivot), indent=1) + "\n")
        (ITEMS_DIR / f"{name}.json").write_text(json.dumps(item_definition(name), indent=2) + "\n")
    print(f"wrote {len(PARTS)} dragon parts")


if __name__ == "__main__":
    main()
