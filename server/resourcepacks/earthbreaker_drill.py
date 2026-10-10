"""Generates the Earthbreaker drill, Cosmetic token request #1 (dvolic21, 2026-10-10): the look of
custom_model_data string `cosmetic_1` on a netherite pickaxe (his Shenron's Earthbreaker, the 5x5
Overworld wish pickaxe). His idea: "a drill skin ... since it mines 5x5, also give it aura whenever
im holding it like lightning". A cosmetic token buys a look, not a feature, so the aura is pack-only:
an animated, emissive coil around the motor and two crossed planes of animated lightning around the
bit, both with light_emission 15 so they glow in the dark.

Writes into claudesmp-items/assets/claudesmp/:
    models/item/earthbreaker_drill.json               the 3D drill, everywhere but the inventory
    models/item/earthbreaker_drill_icon.json          the flat inventory sprite
    textures/item/earthbreaker_drill_icon.png(.mcmeta) 4 frames of the sprite, sparks flicker
    textures/item/earthbreaker_drill.png              static parts (plate, grip, housing, bit)
    textures/item/earthbreaker_coil.png(.mcmeta)      8 frames of crackling coil
    textures/item/earthbreaker_arcs.png(.mcmeta)      8 frames of lightning, 32x48 each

    python server/resourcepacks/earthbreaker_drill.py

The drill is built standing up along the model's Y axis (x = z = 8), then every element is turned
-45 degrees about Z around the model centre, so it lies along the same bottom-left to top-right
diagonal as a vanilla pickaxe sprite and item/handheld's displays hold it like one. 26.3 element
rotations are rotationZYX(z, y, x) (CuboidRotation$EulerXYZRotation, javap'd 2026-10-10), so the bit
tiers' Y twist turns them about the drill axis before the shared Z tilt.
"""

import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "claudesmp-items" / "assets" / "claudesmp"
MODEL = ASSETS / "models" / "item" / "earthbreaker_drill.json"
ICON_MODEL = ASSETS / "models" / "item" / "earthbreaker_drill_icon.json"
TEXTURES = ASSETS / "textures" / "item"

TILT = -45.0
# The vanilla handheld sword's fist pixel (3.5, 12.5) sits 6.36 model units below the centre along
# the diagonal, so the grip is centred at y = 8 - 6.36 to land in the same hand position.
GRIP = (-2.5, 5.0)

# Static texture regions on the 32x32 atlas, in UV units (0..16 spans the whole texture).
PLATE = (0, 0, 8, 8)
WRAP = (8, 0, 12, 8)
TRIM = (12, 0, 16, 4)
HOUSING = (0, 8, 8, 16)
FLUTES = (8, 8, 16, 16)

# Bit tiers: (y from, y to, half width, twist about the drill axis).
BIT = [
    (13.0, 15.5, 2.5, 0.0),
    (15.5, 18.0, 2.0, 22.5),
    (18.0, 20.0, 1.5, 45.0),
    (20.0, 22.0, 1.0, 67.5),
    (22.0, 23.5, 0.5, 90.0),
]

ARC_FRAMES = 8
COIL_FRAMES = 8


def box(y0, y1, half, tex, uv, twist=0.0, glow=0, sides_only=False):
    faces = {}
    for face in ("north", "south", "east", "west"):
        faces[face] = {"uv": list(uv), "texture": tex}
    if not sides_only:
        for face in ("up", "down"):
            faces[face] = {"uv": list(uv), "texture": tex}
    el = {
        "from": [8 - half, y0, 8 - half],
        "to": [8 + half, y1, 8 + half],
        "rotation": {"origin": [8, 8, 8], "x": 0, "y": twist, "z": TILT},
        "faces": faces,
    }
    if glow:
        el["light_emission"] = glow
    return el


def arc_plane(twist):
    # Zero-thickness plane through the drill axis, 14 x 21 units (the 32x48 frame's 2:3), drawn on
    # both sides; the drill body hides the middle, so only the bolts reaching past it show.
    return {
        "from": [1, 8, 8],
        "to": [15, 29, 8],
        "rotation": {"origin": [8, 8, 8], "x": 0, "y": twist, "z": TILT},
        "faces": {
            "north": {"uv": [16, 0, 0, 16], "texture": "#arcs"},
            "south": {"uv": [0, 0, 16, 16], "texture": "#arcs"},
        },
        "light_emission": 15,
        "shade": False,
    }


def model():
    elements = [
        box(-4.0, -2.5, 1.5, "#drill", PLATE),                    # pommel
        box(GRIP[0], GRIP[1], 1.0, "#drill", WRAP, sides_only=True),  # grip
        box(5.0, 6.5, 2.0, "#drill", TRIM),                       # guard collar
        box(6.5, 12.0, 3.0, "#drill", HOUSING),                   # motor housing
        box(8.0, 10.5, 3.25, "#coil", (0, 0, 16, 16), glow=15, sides_only=True),  # coil band
        box(12.0, 13.0, 2.75, "#drill", TRIM),                    # chuck
    ]
    for y0, y1, half, twist in BIT:
        elements.append(box(y0, y1, half, "#drill", FLUTES, twist=twist))
    elements += [arc_plane(45.0), arc_plane(-45.0)]
    return {
        "parent": "minecraft:item/handheld",
        "textures": {
            "particle": "minecraft:item/netherite_pickaxe",
            "drill": "claudesmp:item/earthbreaker_drill",
            "coil": "claudesmp:item/earthbreaker_coil",
            "arcs": "claudesmp:item/earthbreaker_arcs",
        },
        "elements": elements,
        "display": {
            # item/handheld's hand poses at about 0.65x its scale (a 27.5 unit long, 6 unit thick
            # drill at the full scale filled half the first person view), translated so the grip's
            # middle lands where handheld puts a sword's fist pixel. Lefthand is the righthand
            # mirrored, written the way item/handheld writes its own.
            "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [-0.42, 2.46, 0.77], "scale": [0.55, 0.55, 0.55]},
            "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [-0.42, 2.46, 0.77], "scale": [0.55, 0.55, 0.55]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [0.79, 2.03, 0.7], "scale": [0.44, 0.44, 0.44]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [0.79, 2.03, 0.7], "scale": [0.44, 0.44, 0.44]},
            # item/handheld's ground/fixed come from item/generated, sized for a flat 16x16 sprite.
            # The drill's middle sits 1.24 units up and right of the model centre, so fixed (an
            # item frame, turned 180 about Y) shifts it back to centre the drill in the frame.
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0.87, -0.87, 0], "scale": [0.7, 0.7, 0.7]},
        },
    }


def icon_model():
    # The inventory shows a flat sprite instead of the 3D drill (Joe picked it over the 3D icon from
    # hotbar mockups at GUI scale 4, 2026-10-10); items/netherite_pickaxe.json picks it for gui only.
    return {"parent": "minecraft:item/generated", "textures": {"layer0": "claudesmp:item/earthbreaker_drill_icon"}}


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


NETHERITE = (68, 61, 63)
NETHERITE_DARK = (42, 36, 39)
NETHERITE_LIGHT = (104, 94, 96)
STEEL = (122, 118, 124)
STEEL_LIGHT = (176, 172, 180)
WRAP_BLUE = (36, 52, 92)
WRAP_DARK = (20, 24, 40)
TRIM_GOLD = (196, 150, 64)
ORB = (244, 150, 36)
STAR = (204, 36, 28)


def static_texture():
    rng = random.Random(1)
    img = Image.new("RGBA", (32, 32))
    px = img.load()

    def region(uv):
        u0, v0, u1, v1 = uv
        return range(u0 * 2, u1 * 2), range(v0 * 2, v1 * 2)

    xs, ys = region(PLATE)
    for y in ys:
        for x in xs:
            px[x, y] = shade(NETHERITE, 0.9 + 0.2 * rng.random())
    for i in xs:
        px[i, ys.start] = NETHERITE_LIGHT + (255,)
        px[i, ys.stop - 1] = NETHERITE_DARK + (255,)

    # Grip: dark blue wrap in diagonal bands.
    xs, ys = region(WRAP)
    for y in ys:
        for x in xs:
            px[x, y] = (WRAP_DARK if (x + y) % 4 == 0 else WRAP_BLUE) + (255,)

    xs, ys = region(TRIM)
    for y in ys:
        for x in xs:
            f = 1.15 if y == ys.start else 0.8 if y == ys.stop - 1 else 1.0
            px[x, y] = shade(TRIM_GOLD, f)

    # Housing: netherite plates with rivets, a vent grille, and a Dragon Ball on the face (it is a
    # Shenron wish reward).
    xs, ys = region(HOUSING)
    for y in ys:
        for x in xs:
            px[x, y] = shade(NETHERITE, 0.88 + 0.18 * rng.random())
    x0, y0 = xs.start, ys.start
    for i in range(16):
        px[x0 + i, y0] = NETHERITE_LIGHT + (255,)
        px[x0 + i, y0 + 15] = NETHERITE_DARK + (255,)
        px[x0, y0 + i] = NETHERITE_LIGHT + (255,)
        px[x0 + 15, y0 + i] = NETHERITE_DARK + (255,)
    for rx, ry in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[x0 + rx, y0 + ry] = STEEL_LIGHT + (255,)
    for vy in (11, 13):
        for vx in range(4, 12):
            px[x0 + vx, y0 + vy] = NETHERITE_DARK + (255,)
    d = ImageDraw.Draw(img)
    d.ellipse((x0 + 5, y0 + 3, x0 + 10, y0 + 8), fill=ORB + (255,))
    px[x0 + 7, y0 + 5] = STAR + (255,)
    px[x0 + 8, y0 + 5] = STAR + (255,)
    px[x0 + 7, y0 + 6] = STAR + (255,)
    px[x0 + 8, y0 + 6] = STAR + (255,)
    px[x0 + 6, y0 + 4] = (255, 214, 140, 255)

    # Bit: steel with diagonal flutes and bright cutting edges, which the stepped twist of the tiers
    # turns into a spiral.
    xs, ys = region(FLUTES)
    ramp = [STEEL_LIGHT, STEEL_LIGHT, STEEL, STEEL, NETHERITE_LIGHT, NETHERITE, NETHERITE_DARK, NETHERITE_DARK]
    for y in ys:
        for x in xs:
            px[x, y] = shade(ramp[(x - y) % 8], 0.95 + 0.1 * rng.random())
    return img


ELECTRIC_CORE = (236, 250, 255, 255)
ELECTRIC = (110, 220, 255, 255)
ELECTRIC_DEEP = (40, 110, 230, 255)


def coil_texture():
    # A dark band with a cyan zigzag that shifts every frame, so the coil crackles.
    img = Image.new("RGBA", (16, 16 * COIL_FRAMES))
    px = img.load()
    for f in range(COIL_FRAMES):
        rng = random.Random(100 + f)
        top = 16 * f
        for y in range(16):
            for x in range(16):
                px[x, top + y] = (14, 22, 48, 255) if y in (0, 15) else (24, 40, 86, 255)
        y = rng.randint(4, 11)
        for x in range(16):
            y = max(3, min(12, y + rng.choice((-2, -1, 0, 1, 2))))
            px[x, top + y] = ELECTRIC_CORE
            for dy in (-1, 1):
                if px[x, top + y + dy] != ELECTRIC_CORE:
                    px[x, top + y + dy] = ELECTRIC
        for _ in range(3):
            sx, sy = rng.randrange(16), rng.randint(2, 13)
            px[sx, top + sy] = ELECTRIC_DEEP
    return img


def bolt(px, w, h, x, y, dx, steps, rng, branch=True):
    """A jagged bolt walking outward from (x, y), cyan around a white core."""
    pts = []
    for _ in range(steps):
        x += dx * rng.choice((1, 1, 2))
        y += rng.choice((-2, -1, -1, 0, 1, 1, 2))
        if not (1 <= x < w - 1 and 1 <= y < h - 1):
            break
        pts.append((x, y))
        if branch and rng.random() < 0.18:
            bolt(px, w, h, x, y, dx, rng.randint(2, 4), rng, branch=False)
    prev = None
    for p in pts:
        line = [p] if prev is None else _line(prev, p)
        for lx, ly in line:
            for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                if px[lx + ox, ly + oy][3] == 0:
                    px[lx + ox, ly + oy] = ELECTRIC
            px[lx, ly] = ELECTRIC_CORE
        prev = p


def _line(a, b):
    (x0, y0), (x1, y1) = a, b
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    return [(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)) for i in range(n + 1)]


def arcs_texture():
    # Frame layout matches arc_plane: 32 px across x 1..15, 48 px down y 29..8. Bolts start at the
    # drill's surface (the bit narrows toward the top) and reach outward; some frames are near empty
    # so the aura flickers instead of sitting there.
    w, h = 32, 48
    img = Image.new("RGBA", (w, h * ARC_FRAMES), (0, 0, 0, 0))
    counts = [4, 2, 5, 1, 3, 4, 2, 3]
    for f in range(ARC_FRAMES):
        frame = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        px = frame.load()
        rng = random.Random(200 + f)
        for _ in range(counts[f]):
            y_model = rng.uniform(9.5, 23.0)
            half = 3.0 if y_model < 13 else next(b[2] for b in BIT if b[0] <= y_model < b[1] + 0.01)
            side = rng.choice((-1, 1))
            sx = round((8 + side * half - 1) / 14 * w)
            sy = round((29 - y_model) / 21 * h)
            bolt(px, w, h, sx, sy, side, rng.randint(6, 11), rng)
        img.paste(frame, (0, h * f))
    return img


ICON_FRAMES = 4
# The sprite's drill axis, bottom-left pommel to top-right tip, in pixel coordinates (y down).
ICON_FROM, ICON_TO = (1.2, 14.8), (14.9, 1.1)
# The sprite is brighter than the 3D textures: a 16 px icon has no lighting to lift it.
ICON = {
    "neth": (68, 61, 63), "neth_d": (38, 33, 36), "neth_l": (110, 100, 102),
    "wrap": (44, 64, 120), "wrap_d": (24, 30, 58),
    "gold": (214, 166, 70), "gold_d": (150, 104, 40),
    "steel_l": (200, 198, 206), "steel": (138, 134, 142), "steel_d": (78, 74, 82),
}
ICON_SPARKS = [(10, 0), (13, 5), (9, 2), (15, 6), (12, 0), (8, 3), (15, 3), (14, 8)]


def icon_part(t):
    """Which part sits at fraction t along the axis, and its half width in pixels."""
    if t < 0.08:
        return "pommel", 1.5
    if t < 0.33:
        return "grip", 1.0
    if t < 0.39:
        return "collar", 2.2
    if t < 0.60:
        return ("coil" if 0.46 <= t < 0.53 else "housing"), 3.0
    if t < 0.64:
        return "chuck", 2.5
    return "bit", 0.4 + 2.3 * (1 - (t - 0.64) / 0.36)


def icon_frame(frame):
    (ax, ay), (bx, by) = ICON_FROM, ICON_TO
    length = ((bx - ax) ** 2 + (by - ay) ** 2) ** 0.5
    ux, uy = (bx - ax) / length, (by - ay) / length
    nx, ny = -uy, ux  # toward the bottom-right edge, the shadow side
    core, cyan = ELECTRIC_CORE[:3], ELECTRIC[:3]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px_, py_ = x + 0.5 - ax, y + 0.5 - ay
            t = (px_ * ux + py_ * uy) / length
            d = px_ * nx + py_ * ny
            if not 0 <= t <= 1:
                continue
            name, half = icon_part(t)
            if abs(d) > half:
                continue
            side = d / half  # -1 lit edge .. +1 shadow edge
            lit = side < -0.35
            dark = side > 0.35
            if name in ("pommel", "housing"):
                c = ICON["neth_l"] if lit else ICON["neth_d"] if dark else ICON["neth"]
            elif name == "grip":
                c = ICON["wrap_d"] if int(t * length * 1.4) % 2 else ICON["wrap"]
            elif name in ("collar", "chuck"):
                c = ICON["gold_d"] if dark else ICON["gold"]
            elif name == "coil":
                c = core if int(d * 1.6 + frame) % 3 == 0 else cyan
            else:  # bit: bright cutting edges in a spiral
                k = math.floor(t * length * 0.95 - d * 0.95) % 3
                f = 1.12 if side < -0.3 else 0.8 if side > 0.3 else 1.0
                c = tuple(int(v * f) for v in (ICON["steel_l"], ICON["steel"], ICON["steel_d"])[k])
            px[x, y] = tuple(c) + (255,)
    # Vanilla tool sprites edge the silhouette in a dark line, softer on the lit (top-left) side.
    edge = []
    for y in range(16):
        for x in range(16):
            if not px[x, y][3] or px[x, y][:3] in (core, cyan):
                continue
            open_ = [(dx, dy) for dx, dy in ((1, 0), (0, 1), (-1, 0), (0, -1))
                     if not (0 <= x + dx < 16 and 0 <= y + dy < 16) or px[x + dx, y + dy][3] == 0]
            if open_:
                edge.append((x, y, 0.45 if any(dx > 0 or dy > 0 for dx, dy in open_) else 0.75))
    for x, y, f in edge:
        px[x, y] = shade(px[x, y], f)
    # Three sparks off the bit, a different three every frame.
    rng = random.Random(7 + frame)
    for sx, sy in rng.sample(ICON_SPARKS, 3):
        if px[sx, sy][3] == 0:
            px[sx, sy] = ELECTRIC_CORE if rng.random() < 0.5 else ELECTRIC
    return img


def icon_texture():
    img = Image.new("RGBA", (16, 16 * ICON_FRAMES), (0, 0, 0, 0))
    for f in range(ICON_FRAMES):
        img.paste(icon_frame(f), (0, 16 * f))
    return img


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    write_json(MODEL, model())
    write_json(ICON_MODEL, icon_model())
    TEXTURES.mkdir(parents=True, exist_ok=True)
    icon_texture().save(TEXTURES / "earthbreaker_drill_icon.png")
    write_json(TEXTURES / "earthbreaker_drill_icon.png.mcmeta", {"animation": {"frametime": 3}})
    static_texture().save(TEXTURES / "earthbreaker_drill.png")
    coil_texture().save(TEXTURES / "earthbreaker_coil.png")
    write_json(TEXTURES / "earthbreaker_coil.png.mcmeta", {"animation": {"frametime": 2}})
    arcs_texture().save(TEXTURES / "earthbreaker_arcs.png")
    write_json(TEXTURES / "earthbreaker_arcs.png.mcmeta",
               {"animation": {"frametime": 2, "width": 32, "height": 48}})


if __name__ == "__main__":
    main()
