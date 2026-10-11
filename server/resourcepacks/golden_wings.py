"""Generates Golden Wings, Cosmetic token request #2 (dvolic21, 2026-10-10): the look of his elytra
with custom_model_data string `cosmetic_2`. His idea: "Golden wings skin, that are pretty large when
standing, and when flying they flap".

26.3 limits (javap'd ElytraModel 2026-10-11): the wing is one fixed 10x20x2 box per side (inflated
1px), folded at 15 degrees standing and spread when gliding, with no flap animation; only the
texture can change. Vanilla's wing paints 173 of the 200 texels on that box's back face; these
paint 188, close to all of it, which is as large as an elytra can look. The game picks a worn texture from the item's equippable
asset id, not its item model, so the Market jar's cosmetic-looks.yml points request 2 at
claudesmp:golden_wings (see market CosmeticLooks).

Writes into claudesmp-items/assets/claudesmp/:
    equipment/golden_wings.json                        wings layer only, never the player's cape
    textures/entity/equipment/wings/golden_wings.png   64x32, ElytraModel's box UV (texOffs 22,0)
    models/item/golden_wings.json(+_broken)            inventory icons, vanilla elytra's shape in gold
    textures/item/golden_wings.png(+_broken)

    python server/resourcepacks/golden_wings.py
"""

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "claudesmp-items" / "assets" / "claudesmp"
EQUIPMENT = ASSETS / "equipment" / "golden_wings.json"
WINGS_TEX = ASSETS / "textures" / "entity" / "equipment" / "wings" / "golden_wings.png"
ICON_TEX = ASSETS / "textures" / "item"
ICON_MODEL = ASSETS / "models" / "item"

# Dark to light. The darkest two only outline feathers so the wing still reads gold from afar.
GOLD = [(84, 50, 8), (150, 96, 14), (206, 146, 26), (238, 188, 48), (255, 222, 104), (255, 244, 186)]

# ElytraModel's box: texOffs(22, 0), 10 wide, 20 tall, 2 deep. South is the face vanilla paints
# (the render type has no culling, so it shows from both sides); north stays empty like vanilla's,
# or it would be drawn 4px in front of it.
U0, V0, W, H, D = 22, 0, 10, 20, 2
SOUTH = (U0 + D + W + D, V0 + D)   # (36, 2)
WEST = (U0 + D + W, V0 + D)        # (34, 2), the wing's outer edge strip
EAST = (U0, V0 + D)                # (22, 2), the edge along the spine
UP = (U0 + D, V0)                  # (24, 0)
DOWN = (U0 + D + W, V0)            # (34, 0)

# The south face's u runs from the spine (u = 0) to the wing's outer edge (u = 9), v from the
# shoulder (0) down to the lower end (19), checked against the folded render.
COVERT_ROWS = 7    # small shoulder feathers above the long ones
# Where each 2-texel long feather ends (v of its last texel), spine side first: the lower edge
# steps down toward the outside, the outer three feathers running the full 20 texels.
TIPS = [17, 18, 19, 19, 19]
# Only the shoulder's outermost texel is cut, to round the corner off.
SHOULDER = {0: 9}


def feather_texel(u, v):
    """Shade index (into GOLD) of the back face at (u, v), or None for transparent."""
    if u >= SHOULDER.get(v, W):
        return None
    feather, outer_half = divmod(u, 2)
    tip = TIPS[feather]
    if v > tip or (v == tip and not outer_half):
        return None  # the last texel of each feather is a single point, so the edge is jagged
    if v < COVERT_ROWS:
        # Coverts: 3-texel scallops in offset rows, a darker rim under a lit crown.
        row = v // 2
        x = (u + 2 * row) % 3
        if v % 2 == 1:
            return 2 if x == 0 else 3
        return 5 if x == 1 else 4
    if v == COVERT_ROWS:
        return 2  # the shadow line the coverts cast on the long feathers
    # Long feathers: deeper gold near the shoulder, brightening toward the tips; the spine-side
    # texel is the shaft, a step darker, and the tip catches the light.
    if v >= tip - 1:
        return 5
    shade = 3 if v < 12 else 4
    return shade - 1 if not outer_half else shade


def wings_texture():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    for v in range(H):
        for u in range(W):
            s = feather_texel(u, v)
            if s is not None:
                px[SOUTH[0] + u, SOUTH[1] + v] = GOLD[s] + (255,)
    # The box's other faces stay empty like vanilla's: the inflated box is 4px deep, and painted
    # edges read as a thick slab from the side instead of a wing.
    return img


def gold_icon(vanilla):
    """Vanilla's elytra sprite with its greys mapped onto GOLD by brightness, alpha kept."""
    src = vanilla.convert("RGBA")
    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    pixels = [src.getpixel((x, y)) for y in range(src.height) for x in range(src.width)]
    lum = [(0.299 * r + 0.587 * g + 0.114 * b) for r, g, b, a in pixels if a]
    lo, hi = min(lum), max(lum)
    data = []
    for r, g, b, a in pixels:
        if not a:
            data.append((0, 0, 0, 0))
            continue
        t = ((0.299 * r + 0.587 * g + 0.114 * b) - lo) / max(1.0, hi - lo)
        data.append(GOLD[min(len(GOLD) - 1, int(t * len(GOLD)))] + (a,))
    out.putdata(data)
    return out


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def main(vanilla_dir):
    """vanilla_dir: an extracted 26.3 client jar (assets/minecraft/textures/item/elytra*.png)."""
    write_json(EQUIPMENT, {"layers": {"wings": [{"texture": "claudesmp:golden_wings"}]}})
    WINGS_TEX.parent.mkdir(parents=True, exist_ok=True)
    wings_texture().save(WINGS_TEX)
    for name in ("elytra", "elytra_broken"):
        sprite = Image.open(Path(vanilla_dir) / "assets/minecraft/textures/item" / f"{name}.png")
        out = name.replace("elytra", "golden_wings")
        ICON_TEX.mkdir(parents=True, exist_ok=True)
        gold_icon(sprite).save(ICON_TEX / f"{out}.png")
        write_json(ICON_MODEL / f"{out}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"claudesmp:item/{out}"}})


if __name__ == "__main__":
    import sys

    main(sys.argv[1] if len(sys.argv) > 1 else ROOT.parents[1] / ".for_bepy/cosmetics-2-3/vanilla")
