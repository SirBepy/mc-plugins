"""Draws the Dragonscale armor look: the Ender Dragon kill reward (Joe, 2026-10-09), real netherite
helmet/chestplate/leggings/boots with no enchantments, given by the endfight plugin. This writes the
worn equipment layers (claudesmp:dragonscale, picked by the item's equippable.asset_id, including a
"wings" layer WingsLayer only ever draws while the item sits in the chest slot) and the four
inventory icons (picked by custom_model_data string "dragonscale") into claudesmp-items/.

Theme: the Ender Dragon. Near-black overlapping scales with violet-purple edge highlights, a couple
of magenta "ender eye" glints (helmet brow, chest centre), a ridged spine down the helmet's crest and
spiked pauldrons on the shoulders. The chestplate's wings layer is a membrane instead of scales: a
dark-plum-to-near-black gradient, violet bone/finger lines, a scalloped darker trailing edge and a
small claw at the shoulder joint.

The six masks below (HUMANOID_MASK, LEGGINGS_MASK, ICON_MASKS) are the alpha silhouettes of the 26.3
client jar's own assets/minecraft/textures/entity/equipment/{humanoid,humanoid_leggings}/netherite.png
and assets/minecraft/textures/item/netherite_{helmet,chestplate,leggings,boots}.png (dumped
2026-10-09 from the client jar at piston-data.mojang.com, e877b6a0...client.jar, the 26.3 version
javap/porting notes point at). Painting only inside that silhouette keeps every piece pixel-exact
with vanilla's own UV footprint and model fit; only the colours are new. No network access is needed
to run this script.

    python server/resourcepacks/dragonscale_armor.py                       # writes the pack textures
    python server/resourcepacks/dragonscale_armor.py <out dir>             # also writes previews there
    python server/resourcepacks/dragonscale_armor.py <out dir> <vanilla ref dir>
        # previews also get a side-by-side vs vanilla netherite sheet, reading the vanilla PNGs
        # (humanoid_netherite.png, humanoid_leggings_netherite.png, icon_<piece>.png) from
        # <vanilla ref dir>; without it the comparison panel is skipped.
"""

import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
PACK = ROOT / "claudesmp-items" / "assets" / "claudesmp"
EQUIP_HUMANOID = PACK / "textures" / "entity" / "equipment" / "humanoid"
EQUIP_LEGGINGS = PACK / "textures" / "entity" / "equipment" / "humanoid_leggings"
EQUIP_WINGS = PACK / "textures" / "entity" / "equipment" / "wings"
ICONS = PACK / "textures" / "item"

# --- vanilla netherite silhouettes (26.3 client jar), one row per texel, '#' opaque / '.' empty ---

HUMANOID_MASK = [
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "################################................................",
    "################################................................",
    "################################................................",
    "#########..##..#################................................",
    "####..###..##..###..############................................",
    ".......##......##.......########................................",
    ".......###....###.........####..................................",
    ".......###....###...............................................",
    "........####................................####................",
    "........####................................####................",
    "........####................................####................",
    "........####................................####................",
    "................######....##############################........",
    "................######....##############################........",
    "................#######..###############################........",
    "................########################################........",
    "................########################################........",
    "................#################################..#####........",
    "...######.......########################.##.#.#......#..........",
    "########################################........................",
    "########################################........................",
    "################....########....########........................",
    "################.....######......######.........................",
    "################......####........####..........................",
]

LEGGINGS_MASK = [
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "....####........................................................",
    "....####........................................................",
    "....####........................................................",
    "....####........................................................",
    "################................................................",
    "################................................................",
    "################................................................",
    "################................................................",
    "################................................................",
    "################................................................",
    "################................................................",
    "##################....####....##########........................",
    "########################################........................",
    "................########################........................",
    "................########################........................",
    "................########################........................",
]

# The 26.3 client jar's own assets/minecraft/textures/entity/equipment/wings/elytra.png (dumped
# 2026-10-09): two authored UV islands (a thin rib strip, a tapering membrane panel) on an otherwise
# empty 64x32 sheet; ElytraModel reuses this one texture mirrored for the left/right wing, so only
# one island needs painting.
WINGS_MASK = [
    "...............................#########........................",
    "................................##..............................",
    "..................................########......................",
    "..................................#########.....................",
    "...................................#########....................",
    "...................................#########....................",
    "...................................#########....................",
    "...................................##########...................",
    "...................................##########...................",
    "...................................##########...................",
    "...................................##########...................",
    "......................#.............##########..................",
    "......................#.............##########..................",
    "......................#.............##########..................",
    "......................#.............##########..................",
    "......................#.............##########..................",
    "......................#..............#########..................",
    "......................#..............#########..................",
    "......................#..............#########..................",
    "......................#...............########..................",
    "......................#...............########..................",
    "......................#................#######..................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
]

ICON_MASKS = {
    "helmet": [
        "................",
        "................",
        "................",
        ".....######.....",
        "....########....",
        "...##########...",
        "...##########...",
        "...##########...",
        "...##########...",
        "...##########...",
        "...##########...",
        "....##....##....",
        "................",
        "................",
        "................",
        "................",
    ],
    "chestplate": [
        "................",
        "................",
        ".#####....#####.",
        ".#####....#####.",
        ".######..######.",
        ".##############.",
        ".##############.",
        ".##############.",
        "...##########...",
        "...##########...",
        "...##########...",
        "...##########...",
        "...##########...",
        "....########....",
        ".....######.....",
        "................",
    ],
    "leggings": [
        "................",
        "................",
        "....########....",
        "...##########...",
        "...##########...",
        "...##########...",
        "...##########...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "................",
        "................",
    ],
    "boots": [
        "................",
        "................",
        "................",
        "...##......##...",
        "...###....###...",
        "...###....###...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "..#####..#####..",
        ".######..######.",
        ".######..######.",
        ".####......####.",
        "................",
        "................",
        "................",
    ],
}

# --- palette: near-black scales, violet-purple rims, magenta ender-eye glints ---

# Five shades, near-black through dark plum to violet: GROOVE is the gap between scale rows and the
# seam between adjacent scales; CORE is each scale's upper two-thirds; RIM is the lighter violet lip
# along a scale's lower edge (the overlap catching light), echoed as EDGE_TRIM on each body part's
# silhouette edge so parts read as separate 3D forms.
GROOVE = (20, 15, 26)
CORE_A = (37, 25, 48)
CORE_B = (48, 32, 61)
RIM_A = (94, 62, 128)
RIM_B = (122, 79, 176)
EDGE_TRIM = (150, 108, 196)
ICON_OUTLINE = (16, 12, 21)
CREST = (10, 8, 13)
CREST_TRIM = (170, 120, 210)
EYE_CORE = (255, 150, 255)
EYE_RING = (214, 70, 214)

SCALE_W, SCALE_H = 3, 2  # each scale is 3px wide, 2px tall; a 1px groove row sits under every row


def _mix(a, b, t):
    return tuple(round(a[i] * (1 - t) + b[i] * t) for i in range(3))


def scale_color(x, y):
    """Overlapping dragon scales: rows of SCALE_W x SCALE_H tiles separated by a near-black groove,
    staggered by half a scale every other row like roof shingles. Each tile has a darker core on top
    and a lighter violet rim on its bottom edge, with the two leftmost/rightmost columns of every
    tile darkened toward the groove colour so adjacent scales read as separate overlapping shapes
    instead of a flat band."""
    band, local_y = divmod(y, SCALE_H + 1)
    if local_y == SCALE_H:
        return GROOVE
    stagger = (band % 2) * (SCALE_W // 2 + 1)
    tile_x = x + stagger
    local_x = tile_x % SCALE_W
    variant = (tile_x // SCALE_W + band) % 2
    base = (CORE_B if variant else CORE_A) if local_y == 0 else (RIM_B if variant else RIM_A)
    if local_x == 0 or local_x == SCALE_W - 1:
        base = _mix(base, GROOVE, 0.45)
    return base


def paint(mask, accents=(), edge="violet", fill=scale_color):
    """Fills every opaque mask pixel with `fill` (the scale pattern by default), trims
    silhouette-edge pixels (a lighter violet rim on worn equipment layers so parts read in 3D, or a
    vanilla-style dark outline on icons), then stamps the accents (glints, crest, pauldron spikes,
    wing claw) on top."""
    h, w = len(mask), len(mask[0])
    edge_color = EDGE_TRIM if edge == "violet" else ICON_OUTLINE

    def opaque(x, y):
        return 0 <= x < w and 0 <= y < h and mask[y][x] == "#"

    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not opaque(x, y):
                continue
            is_edge = not (opaque(x - 1, y) and opaque(x + 1, y) and opaque(x, y - 1) and opaque(x, y + 1))
            px[x, y] = (edge_color if is_edge else fill(x, y)) + (255,)
    for x, y, col in accents:
        if opaque(x, y):
            px[x, y] = col + (255,)
    return img


def membrane_color(x, y):
    """Smooth dark-plum-to-near-black wing membrane (no scale shingles): lighter violet-plum near
    the shoulder joint (low y), darkening toward the tip, the way stretched skin thins with
    distance from the bone."""
    return _mix(CORE_B, GROOVE, min(1.0, y / 21))


def _wing_accents(mask):
    """Violet finger/bone lines fanning down the membrane panel, a scalloped darker notch every
    other row along its tapering (trailing) edge, and a small dark claw hooking off the shoulder
    joint at the panel's first row. Skips the 1px rib strip (x <= 25): paint()'s own edge-detection
    already renders it as a solid violet bone line since every pixel in a 1px-wide strip is an edge."""
    accents = []
    for y, row in enumerate(mask):
        xs = [x for x, c in enumerate(row) if c == "#" and x > 25]
        if len(xs) < 4:
            continue
        lo, hi = min(xs), max(xs)
        span = hi - lo
        for frac in (0.3, 0.7):
            fx = lo + round(span * frac)
            if fx in xs:
                accents.append((fx, y, RIM_B))
        if y % 2 == 0:
            accents.append((lo, y, CREST))
    top_xs = [x for x, c in enumerate(mask[0]) if c == "#"]
    if top_xs:
        mid = top_xs[len(top_xs) // 2]
        accents += [(mid - 1, 0, CREST), (mid, 0, EDGE_TRIM)]
    return accents


WINGS_ACCENTS = _wing_accents(WINGS_MASK)


def _rect(x0, y0, w, h, col):
    return [(x, y, col) for y in range(y0, y0 + h) for x in range(x0, x0 + w)]


# Head top face (8,0,8,8): a ridged crest down the dome's centre, trimmed violet at the front edge.
HUMANOID_ACCENTS = _rect(11, 0, 2, 8, CREST)
HUMANOID_ACCENTS += [(11, 0, CREST_TRIM), (12, 0, CREST_TRIM)]
# Right arm top face (44,16,4,4) plus the upper sleeve wraparound (40-56,20-23 east/front/west, also
# opaque on vanilla's own netherite mask): a bigger, bolder spiked pauldron in the horns' own
# bone-violet (RIM_B) -- Joe asked for shoulder horns/spikes 2026-10-09, but a flat armor layer can't
# give real 3D spikes (HumanoidArmorLayer only draws flat quads on these faces, and a head-item model
# would turn with the head, not the arm), so the top plate is solid bone-violet and three spike
# points taper down the east(outer)/front/west sleeve faces instead, echoing the Dragon Skull helm's
# own horns. Mirrored onto the left arm automatically (vanilla's own netherite model mirrors this one
# region for both arms).
HUMANOID_ACCENTS += _rect(44, 16, 4, 4, RIM_B)
HUMANOID_ACCENTS += [(44, 16, EDGE_TRIM), (47, 16, EDGE_TRIM)]
HUMANOID_ACCENTS += [(41, 20, RIM_B), (42, 20, RIM_B), (41, 21, RIM_B)]                          # outer spike
HUMANOID_ACCENTS += [(45, 20, RIM_B), (46, 20, RIM_B), (45, 21, RIM_B), (46, 21, RIM_B), (45, 22, RIM_A)]  # centre, tallest
HUMANOID_ACCENTS += [(49, 20, RIM_B), (50, 20, RIM_B), (50, 21, RIM_B)]                          # inner spike
HUMANOID_ACCENTS += [(41, 20, EDGE_TRIM), (46, 20, EDGE_TRIM), (50, 20, EDGE_TRIM)]              # violet tips
# Head front face (8,8,8,8): a brow glint, above the helmet's visor cutout.
HUMANOID_ACCENTS += [(11, 8, EYE_RING), (12, 8, EYE_RING), (11, 9, EYE_CORE), (12, 9, EYE_CORE)]
# Body front face (20,20,8,12): a chest-centre glint.
HUMANOID_ACCENTS += [(23, 24, EYE_RING), (24, 24, EYE_RING), (23, 25, EYE_CORE), (24, 25, EYE_CORE)]

ICON_ACCENTS = {
    "helmet": [(7, 4, EYE_RING), (8, 4, EYE_RING), (7, 5, EYE_CORE), (8, 5, EYE_CORE)],
    "chestplate": [(7, 7, EYE_RING), (8, 7, EYE_RING), (7, 8, EYE_CORE), (8, 8, EYE_CORE)],
    "leggings": [],
    "boots": [],
}

# Front-face crop rects (the standard armor UV atlas; validated against the masks above) used only
# for the flat front-projection preview.
HEAD_FRONT = (8, 8, 8, 8)
BODY_FRONT = (20, 20, 8, 12)
RARM_FRONT = (44, 20, 4, 12)
RLEG_FRONT = (4, 20, 4, 12)


def upscale(img, factor=8):
    return img.resize((img.width * factor, img.height * factor), Image.NEAREST)


def front_view(humanoid, leggings, factor=4):
    """A flat 2D front projection (no 3D): head, chestplate-layer torso and arms, then each leg as
    the leggings-layer thigh stacked over the humanoid-layer boot, side by side."""
    head = humanoid.crop((HEAD_FRONT[0], HEAD_FRONT[1], HEAD_FRONT[0] + HEAD_FRONT[2], HEAD_FRONT[1] + HEAD_FRONT[3]))
    body = humanoid.crop((BODY_FRONT[0], BODY_FRONT[1], BODY_FRONT[0] + BODY_FRONT[2], BODY_FRONT[1] + BODY_FRONT[3]))
    arm = humanoid.crop((RARM_FRONT[0], RARM_FRONT[1], RARM_FRONT[0] + RARM_FRONT[2], RARM_FRONT[1] + RARM_FRONT[3]))
    thigh = leggings.crop((RLEG_FRONT[0], RLEG_FRONT[1], RLEG_FRONT[0] + RLEG_FRONT[2], RLEG_FRONT[1] + RLEG_FRONT[3]))
    boot = humanoid.crop((RLEG_FRONT[0], RLEG_FRONT[1], RLEG_FRONT[0] + RLEG_FRONT[2], RLEG_FRONT[1] + RLEG_FRONT[3]))

    leg = Image.new("RGBA", (4, 24), (0, 0, 0, 0))
    leg.alpha_composite(thigh, (0, 0))
    leg.alpha_composite(boot, (0, 12))

    w, h = 4 + 8 + 4, 8 + 12 + 24
    fig = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    fig.alpha_composite(head, (4, 0))
    fig.alpha_composite(arm, (0, 8))
    fig.alpha_composite(body, (4, 8))
    fig.alpha_composite(arm, (12, 8))
    fig.alpha_composite(leg, (2, 20))
    fig.alpha_composite(leg, (10, 20))
    return upscale(fig, factor)


def side_by_side(images, gap=4, factor=8):
    # A mid-grey backdrop (vanilla netherite's own average colour is close to near-black) so both
    # the vanilla and Dragonscale panels read clearly instead of blending into a dark background.
    scaled = [upscale(im, factor) for im in images]
    h = max(im.height for im in scaled)
    w = sum(im.width for im in scaled) + gap * (len(scaled) - 1)
    sheet = Image.new("RGBA", (w, h), (120, 120, 126, 255))
    x = 0
    for im in scaled:
        sheet.alpha_composite(im, (x, (h - im.height) // 2))
        x += im.width + gap
    return sheet


def main():
    EQUIP_HUMANOID.mkdir(parents=True, exist_ok=True)
    EQUIP_LEGGINGS.mkdir(parents=True, exist_ok=True)
    EQUIP_WINGS.mkdir(parents=True, exist_ok=True)
    ICONS.mkdir(parents=True, exist_ok=True)

    humanoid = paint(HUMANOID_MASK, HUMANOID_ACCENTS)
    humanoid.save(EQUIP_HUMANOID / "dragonscale.png")
    leggings = paint(LEGGINGS_MASK)
    leggings.save(EQUIP_LEGGINGS / "dragonscale.png")
    wings = paint(WINGS_MASK, WINGS_ACCENTS, fill=membrane_color)
    wings.save(EQUIP_WINGS / "dragonscale.png")
    print(f"equipment: {EQUIP_HUMANOID / 'dragonscale.png'}, {EQUIP_LEGGINGS / 'dragonscale.png'}, "
          f"{EQUIP_WINGS / 'dragonscale.png'}")

    icons = {}
    for piece, mask in ICON_MASKS.items():
        img = paint(mask, ICON_ACCENTS[piece], edge="dark")
        img.save(ICONS / f"dragonscale_{piece}.png")
        icons[piece] = img
        print(f"icon: {ICONS / f'dragonscale_{piece}.png'}")

    if len(sys.argv) <= 1:
        return
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)

    for name, img in [("equipment_humanoid", humanoid), ("equipment_leggings", leggings),
                       ("equipment_wings", wings)] + list(icons.items()):
        upscale(img, 8).save(out / f"dragonscale_{name}_8x.png")

    front_view(humanoid, leggings).save(out / "dragonscale_front_view.png")

    if len(sys.argv) > 2:
        ref = Path(sys.argv[2])
        pairs = [
            ("equipment_humanoid", Image.open(ref / "humanoid_netherite.png").convert("RGBA"), humanoid),
            ("equipment_leggings", Image.open(ref / "humanoid_leggings_netherite.png").convert("RGBA"), leggings),
        ]
        for piece, img in icons.items():
            pairs.append((piece, Image.open(ref / f"icon_{piece}.png").convert("RGBA"), img))
        side_by_side([im for _, vanilla, dragonscale in pairs for im in (vanilla, dragonscale)]).save(
            out / "dragonscale_vs_vanilla.png"
        )

    print(f"previews written to {out}")


if __name__ == "__main__":
    main()
