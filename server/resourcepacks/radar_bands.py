"""Generates the Dragon Radar's distance-band frames for all 3 sets, and the radar cases of
claudesmp-items' compass.json.

The radar shows one small square dot (Joe, 2026-10-08: "only 1 dot, it doesnt have to be
circle") on its screen, pointing at the tracked ball. How far out from the centre the dot sits
says how far away the ball is, in 4 bands; the plugin picks the band by swapping the item's
custom_model_data string (RadarItems.modelFor). The "warm" band keeps the radar's original
model names (dragon_radar, dragon_radar_nether, dragon_radar_end), so a radar item that predates
the bands still renders until the plugin restamps it.

Each frame is the set's own blank screen (the blink-off half of its existing frame 00, so the
casing colours of the Overworld, Nether and End radars stay as drawn) with the dot painted on,
stacked over the blank screen again for the blink. Frame NN's dot sits at (180 + NN*11.25) mod
360 degrees clockwise from up, matching vanilla's compass_NN needle, so vanilla's own compass
range_dispatch thresholds drive it.

Re-running is idempotent: it only reads the blink-off half, which it never changes.

Run from anywhere: python server/resourcepacks/radar_bands.py
"""

import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / "server" / "resourcepacks" / "claudesmp-items" / "assets"
TEX_DIR = PACK / "claudesmp" / "textures" / "item"
MODEL_DIR = PACK / "claudesmp" / "models" / "item"
COMPASS = PACK / "minecraft" / "items" / "compass.json"

SIZE = 32
CENTER = (SIZE - 1) / 2.0
# The screen disk the original art paints (radar.py's base_device): the dot never leaves it.
SCREEN_RADIUS = 11.6
DOT = (255, 150, 30, 255)

SETS = ["dragon_radar", "dragon_radar_nether", "dragon_radar_end"]

# suffix -> (dot distance from the centre in pixels, blink frametime in ticks). The radii sit
# between the screen's grid rings (4.2-4.9 and 8.2-8.9) so the dot never merges into one.
# "here" keeps a small offset rather than the dead centre so the dot still gives a rough heading.
BANDS = {
    "_far": (10.0, 10),
    "": (6.6, 10),
    "_close": (3.3, 10),
    "_here": (1.6, 4),
}


def angle_of(nn):
    return (180 + nn * 11.25) % 360


def dot_pixels(radius, angle_deg):
    """The 2x2 block of pixel indices centred as close as the grid allows to the dot's spot."""
    theta = math.radians(angle_deg)
    px = CENTER + math.sin(theta) * radius
    py = CENTER - math.cos(theta) * radius
    # A block starting at index i is centred on i + 0.5, so floor() picks the nearest one.
    x0 = math.floor(px)
    y0 = math.floor(py)
    return [(x0 + dx, y0 + dy) for dy in (0, 1) for dx in (0, 1)]


def blank_screen(base):
    strip = np.array(Image.open(TEX_DIR / f"{base}_00.png").convert("RGBA"))
    return strip[SIZE:, :, :].copy()


def frame(blank, radius, angle_deg):
    on = blank.copy()
    for x, y in dot_pixels(radius, angle_deg):
        if (x - CENTER) ** 2 + (y - CENTER) ** 2 <= SCREEN_RADIUS ** 2:
            on[y, x] = DOT
    return np.concatenate([on, blank], axis=0)


def frame_name(base, suffix, nn):
    return f"{base}{suffix}_{nn:02d}"


def write_json(path, doc):
    path.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")


def band_case(base, suffix):
    # Vanilla's compass order: frame 16 first and last, closing the loop at both ends.
    order = list(range(16, 32)) + list(range(0, 17))
    entries = [
        {
            "model": {"type": "minecraft:model", "model": f"claudesmp:item/{frame_name(base, suffix, nn)}"},
            "threshold": 0.0 if i == 0 else i - 0.5,
        }
        for i, nn in enumerate(order)
    ]
    return {
        "when": f"{base}{suffix}",
        "model": {
            "type": "minecraft:range_dispatch",
            "entries": entries,
            "property": "minecraft:compass",
            "scale": 32.0,
            "target": "spawn",
        },
    }


def idle_case(base):
    return {"when": f"{base}_idle", "model": {"type": "minecraft:model", "model": f"claudesmp:item/{base}_idle"}}


def main():
    for base in SETS:
        blank = blank_screen(base)
        for suffix, (radius, frametime) in BANDS.items():
            for nn in range(32):
                name = frame_name(base, suffix, nn)
                Image.fromarray(frame(blank, radius, angle_of(nn)), mode="RGBA").save(TEX_DIR / f"{name}.png")
                write_json(TEX_DIR / f"{name}.png.mcmeta", {"animation": {"frametime": frametime}})
                write_json(MODEL_DIR / f"{name}.json", {
                    "parent": "minecraft:item/generated",
                    "textures": {"layer0": f"claudesmp:item/{name}"},
                })

    compass = json.loads(COMPASS.read_text(encoding="utf-8"))
    cases = []
    for base in SETS:
        cases.extend(band_case(base, suffix) for suffix in BANDS)
        cases.append(idle_case(base))
    compass["model"]["cases"] = cases
    write_json(COMPASS, compass)
    print(f"wrote {len(SETS) * len(BANDS) * 32} radar frames and {len(cases)} compass cases")


if __name__ == "__main__":
    main()
