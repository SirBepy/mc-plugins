"""Generates the Golden Halo, Cosmetic token request #3 (dvolic21, 2026-10-10): the worn look of his
netherite helmet with custom_model_data string `cosmetic_3`. His idea: "a golden halo that shines
really bright and floats above my head".

Worn, a helmet shows either its armor layer or, with no equippable asset id, its item model on the
head (CustomHeadLayer, see dragonscale_helmet_model.py), never both; the Market jar's
cosmetic-looks.yml clears the asset id for request 3. Pack-only shine, no particles: every element
has light_emission 15, a highlight runs round the ring (animated texture) and a translucent outer
shell gives it a soft glow.

Writes into claudesmp-items/assets/claudesmp/:
    models/item/golden_halo.json                 the ring, in the head display context only
    textures/item/golden_halo.png(.mcmeta)       16 frames, 32x32 each
    models/item/golden_halo_icon.json            flat sprite everywhere else (inventory, hand, ground)
    textures/item/golden_halo_icon.png(.mcmeta)  8 frames, 16x16 each

    python server/resourcepacks/golden_halo.py

Placement: a worn head item's model point v (pixels) lands at head-bone point
(-0.625 (v.x/16 - 0.5), -0.25 - 0.625 (v.y/16 - 0.5), 0.625 (v.z/16 - 0.5)) blocks
(test_build_server_pack._worn_head_item, javap-verified), so one model pixel is 0.625 head pixels
and the head's top (head-bone y = -8px) is model y = 14.4.
"""

import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "claudesmp-items" / "assets" / "claudesmp"
MODEL = ASSETS / "models" / "item" / "golden_halo.json"
ICON_MODEL = ASSETS / "models" / "item" / "golden_halo_icon.json"
TEXTURE = ASSETS / "textures" / "item" / "golden_halo.png"
ICON_TEXTURE = ASSETS / "textures" / "item" / "golden_halo_icon.png"

HEAD_PX = 1.6  # model pixels per head pixel

# 2.5 head pixels above the top of the head (2 above the skin's hat layer), centred over it.
HEAD_TOP = 14.4
CENTER = (8.0, HEAD_TOP + 2.5 * HEAD_PX, 8.0)
SEGMENTS = 16
RADIUS = 4.5 * HEAD_PX       # mid-line of the ring; the head is 4 head pixels from centre to side
THICK = 1.0 * HEAD_PX        # radial
TALL = 0.75 * HEAD_PX
GLOW = 0.35 * HEAD_PX        # how far the translucent shell stands off the ring on every side

FRAMES = 16
ICON_FRAMES = 8

CORE_TOP = (255, 228, 110)
CORE_SIDE_HI = (255, 206, 58)
CORE_SIDE_LO = (214, 142, 16)
CORE_BOTTOM = (178, 112, 10)
SPARK = (255, 252, 226)
GLOW_RGB = (255, 200, 60)


def segment(radius, thick, tall, angle, row):
    """One box of the ring: centred on the ring's mid-line at `angle` degrees round the Y axis,
    long enough that neighbours meet on the outside. `row` picks the texture band (v units)."""
    outer = radius + thick / 2
    length = 2 * outer * math.tan(math.pi / SEGMENTS) + 0.05
    cx, cy, cz = CENTER
    u0 = (angle / 360 * SEGMENTS) % SEGMENTS
    band = lambda v0: {"uv": [u0, v0, u0 + 1, v0 + 4], "texture": "#halo"}
    return {
        "from": [round(cx + radius - thick / 2, 4), round(cy - tall / 2, 4), round(cz - length / 2, 4)],
        "to": [round(cx + radius + thick / 2, 4), round(cy + tall / 2, 4), round(cz + length / 2, 4)],
        "rotation": {"origin": [cx, cy, cz], "x": 0, "y": angle, "z": 0},
        "light_emission": 15,
        "faces": {
            "east": band(row),       # outside of the ring
            "west": band(row),       # inside
            "up": band(row + 4),
            "down": band(row + 8),
        },
    }


def model():
    elements = []
    for i in range(SEGMENTS):
        angle = i * 360 / SEGMENTS
        elements.append(segment(RADIUS, THICK, TALL, angle, 0))
    for i in range(SEGMENTS):
        angle = i * 360 / SEGMENTS
        # The glow shell reads the translucent bottom quarter of the texture for every face.
        glow = segment(RADIUS, THICK + 2 * GLOW, TALL + 2 * GLOW, angle, 12)
        for face in glow["faces"].values():
            face["uv"][1], face["uv"][3] = 12, 16
        elements.append(glow)
    identity = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
    return {
        "textures": {"halo": "claudesmp:item/golden_halo", "particle": "claudesmp:item/golden_halo"},
        "elements": elements,
        "display": {"head": identity},
    }


def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def highlight(position, frame, frames):
    """0..1 brightness boost at `position` (0..1 round the ring) for `frame`: two sparks on
    opposite sides chasing each other round, so some part of the halo is always flaring."""
    best = 0.0
    for offset in (0.0, 0.5):
        centre = (frame / frames + offset) % 1.0
        d = min(abs(position - centre), 1 - abs(position - centre))
        best = max(best, math.exp(-(d * 8) ** 2))
    return best


def texture():
    """FRAMES stacked 32x32 frames. Per frame, one 2-texel column per segment; rows (in 2-texel
    units of v): 0-3 sides, 4-7 top, 8-11 bottom, 12-15 the glow shell's translucent colour."""
    sheet = Image.new("RGBA", (32, 32 * FRAMES), (0, 0, 0, 0))
    px = sheet.load()
    for f in range(FRAMES):
        for x in range(32):
            b = highlight((x + 0.5) / 32, f, FRAMES)
            for y in range(32):
                if y < 8:
                    c = mix(mix(CORE_SIDE_HI, CORE_SIDE_LO, y / 7), SPARK, 0.85 * b)
                    a = 255
                elif y < 16:
                    c = mix(CORE_TOP, SPARK, b)
                    a = 255
                elif y < 24:
                    c = mix(CORE_BOTTOM, CORE_SIDE_HI, 0.7 * b)
                    a = 255
                else:
                    c = mix(GLOW_RGB, SPARK, 0.5 * b)
                    a = round(70 + 70 * b)
                px[x, f * 32 + y] = c + (a,)
    return sheet


def icon_frame(frame):
    """16x16: the ring seen from slightly above (an ellipse), the near half brighter, a spark
    running round it and a one-texel translucent glow outside the ring."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    rx, ry, cx, cy = 6.6, 3.2, 7.5, 8.0
    for y in range(16):
        for x in range(16):
            dx, dy = (x - cx) / rx, (y - cy) / ry
            r = math.hypot(dx, dy)
            pos = (math.atan2(dy, dx) / (2 * math.pi)) % 1.0
            b = highlight(pos, frame, ICON_FRAMES)
            near = dy > 0
            if abs(r - 1) < 0.17:
                base = CORE_SIDE_HI if near else CORE_SIDE_LO
                px[x, y] = mix(base, SPARK, 0.9 * b) + (255,)
            elif abs(r - 1) < 0.42:
                px[x, y] = mix(GLOW_RGB, SPARK, 0.5 * b) + (round(60 + 90 * b),)
    return img


def icon_texture():
    sheet = Image.new("RGBA", (16, 16 * ICON_FRAMES), (0, 0, 0, 0))
    for f in range(ICON_FRAMES):
        sheet.paste(icon_frame(f), (0, 16 * f))
    return sheet


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    write_json(MODEL, model())
    TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    texture().save(TEXTURE)
    write_json(TEXTURE.with_suffix(".png.mcmeta"), {"animation": {"frametime": 2, "interpolate": True}})
    icon_texture().save(ICON_TEXTURE)
    write_json(ICON_TEXTURE.with_suffix(".png.mcmeta"), {"animation": {"frametime": 3, "interpolate": True}})
    write_json(ICON_MODEL, {"parent": "minecraft:item/generated",
                            "textures": {"layer0": "claudesmp:item/golden_halo_icon"}})


if __name__ == "__main__":
    main()
