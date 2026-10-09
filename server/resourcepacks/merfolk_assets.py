"""Generates every resource pack asset for ItsLogiks' Merfolk wish (Shenron wish, granted
2026-10-09, Nuova Shenron): the merfolk plugin (being built in parallel, module merfolk/) turns some
villagers into underwater Merfolk by (1) setting the villager's VillagerType to SWAMP, (2) putting a
custom item (base minecraft:kelp, item_model claudesmp:merfolk_head) in the villager's HEAD
equipment slot, and (3) placing "Kelp Beds" shown as an item display (claudesmp:kelp_bed_placed,
item_display_transform NONE). This script owns every pixel/JSON of that look:

  1. assets/minecraft/textures/entity/villager/type/swamp.png and .../baby/swamp.png -- the
     repainted SWAMP villager type, which the client draws as a cutout over the shared base
     villager.png (javap'd below; untouched, still shared by every other type).
  2. assets/claudesmp/items/merfolk_head.json + models/item/merfolk_head.json + its texture -- the
     3D head piece (fin "ears", a dorsal crest, trailing kelp strands) worn in the villager's
     CustomHeadLayer.
  3. assets/claudesmp/items/kelp_bed.json (flat icon) and kelp_bed_placed.json (the 3D placed bed,
     item_display_transform NONE) + their textures.

----------------------------------------------------------------------------------------------------
PART 1 MATH -- villager box-UV (javap'd 2026-10-09 against C:\\tmp\\client-26.3.jar; confirmed against
the ACTUAL pixel footprint of the 26.3 jar's own type/swamp.png, dumped this session):

VillagerModel.createBodyModel() (net.minecraft.client.model.npc.VillagerModel, javap'd): every part
is `CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d)`, which lays UV out with
Minecraft's standard box-UV algorithm (the same one guild_skins.py's face_rects() already uses for
Mannequin skins):

    top    = (u + d,         v,         w, d)
    bottom = (u + d + w,     v,         w, d)
    right  = (u,             v + d,     d, h)
    front  = (u + d,         v + d,     w, h)
    left   = (u + d + w,     v + d,     d, h)
    back   = (u + d + w + d, v + d,     w, h)

javap'd (u, v, w, h, d) per part, VillagerModel/BabyVillagerModel.createBodyModel():
    adult head=(0,0,8,10,8) hat=(32,0,8,10,8) hat_rim=(30,47,16,16,1) nose=(24,0,2,4,2)
          body=(16,20,8,12,6) jacket=(0,38,8,20,6) arms=(44,22,4,8,4) arms_hold=(40,38,8,4,4)
          leg=(0,22,4,12,4) [right_leg and left_leg share this UV, mirrored geometry only]
    baby  head=(0,0,8,8,7) hat=(0,30,8,8,7) hat_rim=(0,45,14,1,12) nose=(23,0,2,2,1)
          body=(0,15,4,5,3) bb_main=(16,21,4,6,3) right_hand=(36,15,2,4,2) middlearm=(24,17,4,2,2)

Base villager.png (shared, untouched) paints its head-front-face eyes at exactly y=14, x in
{9,10,13,14} (adult) / y=12, x in {8,9,13,14} (baby) -- confirmed by sampling the jar's own
villager.png / villager_baby.png this session (green pupil + white highlight pixels). Every type
texture, including vanilla's own type/swamp.png, leaves those pixels (and the rest of the lower
face) transparent so the SAME eyes show on every villager regardless of type; this is also why the
vanilla swamp hood has a big teardrop opening, not narrow eye slits. Confirmed vanilla's type/swamp.png
has no .mcmeta (no hat blink/flap animation): this session checked the jar directly, no such entry
exists for either villager/type/swamp.png or villager/baby/swamp.png.

DESIGN DECISION (documented per the task brief, since this deviates from "paint only inside vanilla's
existing opaque footprint" like dragonscale_armor.py does): Merfolk need scaly skin on the WHOLE head,
face and nose, which vanilla's swamp hood leaves transparent (tan base skin shows there). So this
script fills every part's full box-UV footprint with merfolk colours and punches OUT only the 4
exact eye pixels per age (same coordinates found above), rather than reusing vanilla's bigger
transparent face-hole. The "hat" + "hat_rim" parts (vanilla's floppy witch hood) are kept -- same
behaviour as vanilla swamp, Claude did not delete the hood geometry's opacity -- but repainted as a
kelp mantle/hood instead of felt, which reads better on an aquatic villager than a cloth point; the
baby swamp type ships NO opaque hood pixels at all in vanilla (checked this session: every row of
baby/swamp.png's head/hat band is fully transparent), so painting the baby's hood here is a deviation
from vanilla's OWN baby look, done so baby merfolk read as merfolk too, not as plain tan baby
villagers in a green jacket.

PART 2 MATH -- CustomHeadLayer's villager-specific transform (javap'd 2026-10-09):
VillagerRenderer.CUSTOM_HEAD_TRANSFORMS = new Transforms(-0.1171875f, -0.07421875f, 1.0f) (javap'd its
static initializer): yOffset=-0.1171875, skullYOffset=-0.07421875, horizontalScale=verticalScale=1.0
(same as CustomHeadLayer$Transforms.DEFAULT's scale, only yOffset/skullYOffset differ from the
humanoid default of 0/0 that dragonscale_helmet_model.py solved against). CustomHeadLayer.submit()
(javap'd): pose.scale(h, v, h) [[1,1,1, no-op]]; getParentModel().root().translateAndRotate [[identity:
VillagerModel/BabyVillagerModel's root PartDefinition carries PartPose.ZERO]]; then
HeadedModel.translateToHead default method (javap'd: `getHead().translateAndRotate(poseStack)`) --
VillagerModel's own "head" ModelPart also carries PartPose.ZERO (javap'd), so this step is identity
too, for BOTH ages (the baby's head PartPose offset of (0,16,0) and the outer 0.5x baby scale in
AgeableMobRenderer only move the whole already-shaped head item to the right world position; neither
is a rotation or a non-uniform scale, so neither changes the LINEAR map between this model's element
coordinates and the final rendered shape -- the same model file is correct unscaled for both ages,
same as a real player head item worn by a baby zombie). Then, since this item carries no
wornHeadType (it's a plain item, not a SkullBlock$Type), CustomHeadLayer.translateToHead(poseStack,
transforms) (the static method, javap'd) runs: `pose.translate(0, -0.25 + transforms.yOffset(), 0);
pose.rotateDegrees(Axis.YP, 180); pose.scale(0.625, -0.625, -0.625);`. Finally ItemStackRenderState
.submit() applies this model's own `display.head` via ItemTransform.apply() (javap'd, same code path
dragonscale_helmet_model.py already cites): with `display.head` written as identity, that reduces to
the SAME no-op `NO_TRANSFORM` shortcut (just the trailing `pose.translate(-0.5,-0.5,-0.5)`).

So M_outer = T(0, ty, 0) . Ry(180deg) . S(0.625, -0.625, -0.625), ty = -0.25 + yOffset = -0.3671875
(vs humanoid's ty = -0.25 for the Dragon Skull helm). Composing exactly like
dragonscale_helmet_model.py's derivation (same Ry(180)/S(...) terms, so x and z invert identically;
only the y leg carries a translate before the scale-and-flip) gives, for a point P = M_outer . (v/16
- (0.5,0.5,0.5)) that must equal (ox,oy,oz)/16 in the same HumanoidModel-style head-pivot frame
(y-down, pivot at the neck, z<0 = front/face) dragonscale_helmet_model.py authors in:

    vx = 8 - 1.6*ox             vy = 25.6*ty + 8 - 1.6*oy = -1.4 - 1.6*oy            vz = 8 + 1.6*oz

(dragonscale_helmet_model.py's vy = 1.6 - 1.6*oy is the same formula at ty=-0.25: 25.6*-0.25+8 = 1.6).
Rotation maps the same way as that script found: {x, y, z} degrees -> {-x, -y, z} (the diag(-0.625,
-0.625, 0.625) linear part is identical, only the villager's extra y-translate differs, and a
translate doesn't change how a rotation's own axes conjugate).

The villager head box itself (javap'd above) is 8 wide x 10 tall x 8 deep (from (-4,-10,-4) to
(4,0,4) in that head-pivot frame) -- 2 units taller than a player head -- with a nose protruding to
z=-6 (javap'd nose addBox(-1,-1,-6,2,4,2)). Ears/crest/kelp strands below are placed outside that box
(sides, above the top, behind the back), so they can never cover the eyes, which sit on the FRONT
face (z=-4) only.

Run from anywhere: python server/resourcepacks/merfolk_assets.py
"""

import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

from dragon_models import FACE_DIMS, mirror as mirror_x

ROOT = Path(__file__).resolve().parent
PACK = ROOT / "claudesmp-items" / "assets" / "claudesmp"

# --- Part 1: villager type textures --------------------------------------------------------------

TYPE_DIR = PACK.parent / "minecraft" / "textures" / "entity" / "villager"
ADULT_SWAMP = TYPE_DIR / "type" / "swamp.png"
BABY_SWAMP = TYPE_DIR / "baby" / "swamp.png"

# Merfolk palette.
SCALE_LIGHT = (108, 196, 164)
SCALE_DARK = (32, 94, 80)
GILL = (18, 42, 36)
ROBE_LIGHT = (84, 128, 74)
ROBE_DARK = (42, 70, 40)
HOOD_LIGHT = (66, 104, 60)
HOOD_DARK = (34, 58, 36)
ACCENT = (232, 224, 196)

# (u, v, w, h, d, category). category picks which procedural pattern paints the part.
ADULT_PARTS = {
    "head": (0, 0, 8, 10, 8, "skin"),
    "hat": (32, 0, 8, 10, 8, "hood"),
    "hat_rim": (30, 47, 16, 16, 1, "hood"),
    "nose": (24, 0, 2, 4, 2, "skin"),
    "body": (16, 20, 8, 12, 6, "robe"),
    "jacket": (0, 38, 8, 20, 6, "robe"),
    "arms": (44, 22, 4, 8, 4, "skin"),
    "arms_hold": (40, 38, 8, 4, 4, "skin"),
    "leg": (0, 22, 4, 12, 4, "robe"),
}
BABY_PARTS = {
    "head": (0, 0, 8, 8, 7, "skin"),
    "hat": (0, 30, 8, 8, 7, "hood"),
    "hat_rim": (0, 45, 14, 1, 12, "hood"),
    "nose": (23, 0, 2, 2, 1, "skin"),
    "body": (0, 15, 4, 5, 3, "robe"),
    "bb_main": (16, 21, 4, 6, 3, "robe"),
    "right_hand": (36, 15, 2, 4, 2, "skin"),
    "middlearm": (24, 17, 4, 2, 2, "skin"),
}

EYE_HOLES_ADULT = [(9, 14), (10, 14), (13, 14), (14, 14)]
EYE_HOLES_BABY = [(8, 12), (9, 12), (13, 12), (14, 12)]

# Gill slits: a couple of dark pixels on the head's "right"/"left" cheek faces.
GILLS_ADULT = [(1, 15), (2, 15), (3, 15), (18, 15), (19, 15), (20, 15)]
GILLS_BABY = [(1, 12), (2, 12), (17, 12), (18, 12)]

# Shell/pearl accents on the robe.
ACCENTS_ADULT = [(9, 50), (9, 58), (36, 50)]
ACCENTS_BABY = [(21, 26)]


def face_rects(u, v, w, h, d):
    """Minecraft's standard box-UV layout (javap'd via CubeListBuilder.addBox; same algorithm
    guild_skins.py's face_rects() already uses). Returns {face: (x, y, w, h)}."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def scale_pattern(size, seed, light, dark):
    """Overlapping-scale pattern (same arc technique as dragon_models.py's "scales" texture,
    recoloured teal for merfolk skin)."""
    rng = np.random.default_rng(seed)
    ys, xs = np.mgrid[0:size, 0:size]
    best = np.full((size, size), 9.0)
    for row in range(-1, size // 4 + 2):
        for col in range(-1, size // 4 + 2):
            cx = col * 4 + (2 if row % 2 else 0)
            cy = row * 4
            d = np.hypot(xs - cx, (ys - cy) * 1.1) / 3.2
            best = np.where((ys >= cy) & (d < best), d, best)
    t = np.clip(np.where(best > 8, 1.0, best), 0, 1) ** 2
    img = np.zeros((size, size, 3))
    for c in range(3):
        img[:, :, c] = light[c] * (1 - t) + dark[c] * t
    img += rng.normal(0, 4, (size, size, 1))
    return np.clip(img, 0, 255).astype(np.uint8)


def frond_pattern(size, seed, light, dark):
    """Vertical kelp-frond stripes (robe/hood cloth)."""
    rng = np.random.default_rng(seed)
    ys, xs = np.mgrid[0:size, 0:size]
    wave = np.sin(xs * 0.5 + ys * 0.15) * 1.5
    stripe = ((xs + wave) % 4) < 2
    img = np.zeros((size, size, 3))
    for c in range(3):
        img[:, :, c] = np.where(stripe, light[c], dark[c])
    img += rng.normal(0, 5, (size, size, 1))
    return np.clip(img, 0, 255).astype(np.uint8)


def villager_type_texture(parts, eye_holes, gills, accents, size=64):
    skin = scale_pattern(size, 11, SCALE_LIGHT, SCALE_DARK)
    robe = frond_pattern(size, 12, ROBE_LIGHT, ROBE_DARK)
    hood = frond_pattern(size, 13, HOOD_LIGHT, HOOD_DARK)
    sources = {"skin": skin, "robe": robe, "hood": hood}

    img = np.zeros((size, size, 4), np.uint8)
    for u, v, w, h, d, cat in parts.values():
        src = sources[cat]
        for x, y, fw, fh in face_rects(u, v, w, h, d).values():
            x0, y0, x1, y1 = x, y, x + fw, y + fh
            if x1 <= 0 or y1 <= 0 or x0 >= size or y0 >= size:
                continue
            x0c, y0c, x1c, y1c = max(0, x0), max(0, y0), min(size, x1), min(size, y1)
            img[y0c:y1c, x0c:x1c, :3] = src[y0c:y1c, x0c:x1c]
            img[y0c:y1c, x0c:x1c, 3] = 255
    for x, y in gills:
        img[y, x, :3] = GILL
        img[y, x, 3] = 255
    for x, y in accents:
        img[y, x, :3] = ACCENT
        img[y, x, 3] = 255
    for x, y in eye_holes:
        img[y, x] = (0, 0, 0, 0)
    return Image.fromarray(img, "RGBA")


# --- Part 2: merfolk_head item model --------------------------------------------------------------

HEAD_MODEL_PATH = PACK / "models" / "item" / "merfolk_head.json"
HEAD_TEX_PATH = PACK / "textures" / "item" / "merfolk_head.png"
HEAD_TEX_REF = "claudesmp:item/merfolk_head"
HEAD_ITEM_PATH = PACK / "items" / "merfolk_head.json"

HEAD_TEX_SIZE = 32
HEAD_REGIONS = {
    "fin_base": (0, 0, 16, 16),
    "fin_tip": (16, 0, 24, 8),
    "fin": (24, 0, 32, 8),
    "kelp": (0, 16, 16, 24),
    "kelp_dark": (16, 16, 24, 24),
}


def head_texture():
    img = Image.new("RGBA", (HEAD_TEX_SIZE, HEAD_TEX_SIZE), (0, 0, 0, 0))
    px = img.load()

    def fill(region, color):
        x0, y0, x1, y1 = region
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[x, y] = color + (255,)

    fill(HEAD_REGIONS["fin_base"], (90, 176, 148))
    fill(HEAD_REGIONS["fin_tip"], (36, 100, 86))
    fill(HEAD_REGIONS["fin"], (52, 128, 110))
    fill(HEAD_REGIONS["kelp"], ROBE_LIGHT)
    fill(HEAD_REGIONS["kelp_dark"], ROBE_DARK)
    return img


def hbox(f, t, mat, rot=None):
    return {"from": list(f), "to": list(t), "mat": mat, "rot": rot}


def head_face_uv(mat):
    x0, y0, x1, y1 = HEAD_REGIONS[mat]
    s = HEAD_TEX_SIZE / 16
    return [x0 / s, y0 / s, x1 / s, y1 / s]


def to_item_space_villager(elements):
    """Converts elements from the HumanoidModel-style head-pivot frame (y-down, pivot at the neck,
    z<0 = front/face -- the same frame dragonscale_helmet_model.py authors in) into 26.3 item-model
    element space, using the villager-specific CUSTOM_HEAD_TRANSFORMS derivation above (ty =
    -0.3671875 instead of the humanoid helm's -0.25, so only the y formula's constant differs)."""
    def pt(p):
        x, y, z = p
        return (8 - 1.6 * x, -1.4 - 1.6 * y, 8 + 1.6 * z)

    out = []
    for e in elements:
        f2, t2 = pt(e["from"]), pt(e["to"])
        new_from = [min(f2[i], t2[i]) for i in range(3)]
        new_to = [max(f2[i], t2[i]) for i in range(3)]
        rot = None
        if e["rot"]:
            o, (rx, ry, rz) = e["rot"]
            rot = (pt(o), (-rx, -ry, rz))
        out.append(hbox(new_from, new_to, e["mat"], rot))
    return out


def build_head(elements):
    out = []
    for i, e in enumerate(elements):
        f, t = e["from"], e["to"]
        for v in list(f) + list(t):
            if v < -16 or v > 32:
                raise SystemExit(f"merfolk_head element {i} {f}..{t} leaves the -16..32 item-model range")
        faces = {face: {"uv": head_face_uv(e["mat"]), "texture": "#t"} for face in FACE_DIMS}
        el = {"from": f, "to": t, "faces": faces}
        if e["rot"]:
            o, (rx, ry, rz) = e["rot"]
            el["rotation"] = {"origin": list(o), "x": rx, "y": ry, "z": rz}
        out.append(el)
    return {
        "textures": {"t": HEAD_TEX_REF, "particle": HEAD_TEX_REF},
        "elements": out,
        # Identity: numerically equivalent to NO_TRANSFORM, written explicitly so the fit math
        # above is on record in the file that uses it (same reasoning as dragonscale_helmet_model.py).
        "display": {"head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}},
    }


def ear(mirror=False):
    boxes = [
        hbox((4.0, -7.2, -0.8), (5.6, -4.4, 2.0), "fin_base"),
        hbox((5.0, -7.8, -0.3), (7.2, -5.2, 1.2), "fin_tip", ((5.0, -6.5, 0.4), (0, 0, -12))),
    ]
    return mirror_x(boxes) if mirror else boxes


def crest():
    segs = []
    for i, oz in enumerate((-2.0, 0.5, 3.0)):
        y_top = -11.2 + i * 0.3
        segs.append(hbox((-0.6, y_top, oz - 1.1), (0.6, -9.6, oz + 1.1), "fin",
                          ((0, -10, oz), (-20 - i * 5, 0, 0))))
    return segs


def kelp_strands():
    out = []
    for ox in (-1.6, 0.0, 1.6):
        out.append(hbox((ox - 0.4, -2.0, 3.0), (ox + 0.4, 5.5, 4.2), "kelp",
                         ((ox, -2.0, 3.6), (14, 0, 0))))
    return out


def merfolk_head_elements():
    return ear(False) + ear(True) + crest() + kelp_strands()


def item_definition(model_ref):
    return {"model": {"type": "minecraft:model", "model": model_ref}}


# --- Part 3: Kelp Bed ------------------------------------------------------------------------------

BED_ICON_MODEL_PATH = PACK / "models" / "item" / "kelp_bed.json"
BED_ICON_TEX_PATH = PACK / "textures" / "item" / "kelp_bed.png"
BED_ICON_ITEM_PATH = PACK / "items" / "kelp_bed.json"

BED_MODEL_PATH = PACK / "models" / "item" / "kelp_bed_placed.json"
BED_TEX_PATH = PACK / "textures" / "item" / "kelp_bed_placed.png"
BED_TEX_REF = "claudesmp:item/kelp_bed_placed"
BED_ITEM_PATH = PACK / "items" / "kelp_bed_placed.json"

BED_TEX_SIZE = 32
BED_REGIONS = {
    "mat": (0, 0, 16, 16),
    "mat_dark": (16, 0, 24, 8),
    "pillow": (0, 16, 16, 24),
    "fringe": (16, 16, 24, 24),
    "fringe_dark": (24, 16, 32, 24),
}


def bed_texture():
    img = Image.new("RGBA", (BED_TEX_SIZE, BED_TEX_SIZE), (0, 0, 0, 0))
    px = img.load()

    def fill(region, color):
        x0, y0, x1, y1 = region
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[x, y] = color + (255,)

    fill(BED_REGIONS["mat"], (72, 62, 34))
    fill(BED_REGIONS["mat_dark"], (46, 40, 22))
    fill(BED_REGIONS["pillow"], (86, 122, 64))
    fill(BED_REGIONS["fringe"], (70, 138, 66))
    fill(BED_REGIONS["fringe_dark"], (40, 88, 42))
    return img


def bed_face_uv(mat):
    x0, y0, x1, y1 = BED_REGIONS[mat]
    s = BED_TEX_SIZE / 16
    return [x0 / s, y0 / s, x1 / s, y1 / s]


def bbox(f, t, mat):
    return {"from": list(f), "to": list(t), "mat": mat}


# Geometry contract (the merfolk plugin relies on this exactly): x 0..16 (1 block wide), z -8..24
# (2 blocks long, head end at the -Z half z -8..0, foot end z 16..24), y starts at 0 (the floor) up
# to about 9 (vanilla bed mattress height). item_display_transform NONE means these coordinates ARE
# the world-block-local coordinates once the engine's own -0.5 recentring runs (ItemDisplayRenderer:
# translate(-0.5,-0.5,-0.5) then the entity's own yaw/position), so no display block is written here.
def kelp_bed_elements():
    els = [
        bbox((0, 0, -8), (16, 6, 24), "mat"),
        bbox((2, 6, -8), (14, 9, -1), "pillow"),
        bbox((1, 6, 21), (15, 7, 24), "mat_dark"),
    ]
    for i, z in enumerate((-6, -2, 2, 6, 10, 14, 18, 22)):
        top = 5 if i % 2 else 6
        mat = "fringe" if i % 2 else "fringe_dark"
        # Fringe stays inside x 0..16 (the geometry contract's own bound) instead of overhanging
        # the sides, so the plugin's bounding-box assumption holds for every element, not just the
        # main slab.
        els.append(bbox((0, 0, z - 1.5), (1, top, z + 1.5), mat))
        els.append(bbox((15, 0, z - 1.5), (16, top, z + 1.5), mat))
    return els


def build_bed(elements, tex_ref, face_uv):
    out = []
    for i, e in enumerate(elements):
        f, t = e["from"], e["to"]
        for v in list(f) + list(t):
            if v < -16 or v > 32:
                raise SystemExit(f"kelp_bed_placed element {i} {f}..{t} leaves the -16..32 range")
        faces = {face: {"uv": face_uv(e["mat"]), "texture": "#t"} for face in FACE_DIMS}
        out.append({"from": f, "to": t, "faces": faces})
    return {"textures": {"t": tex_ref, "particle": tex_ref}, "elements": out}


def bed_icon_texture():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = (72, 62, 34, 255)
    for y in range(1, 6):
        for x in range(2, 14):
            px[x, y] = (86, 122, 64, 255)
    for x in (1, 14):
        for y in range(1, 15):
            px[x, y] = (70, 138, 66, 255)
    for y in (1, 14):
        for x in range(1, 15):
            px[x, y] = (70, 138, 66, 255)
    for y in range(7, 15):
        shade = (60, 52, 28, 255) if y % 2 else (72, 62, 34, 255)
        for x in range(2, 14):
            px[x, y] = shade
    return img


def main():
    for d in (ADULT_SWAMP.parent, BABY_SWAMP.parent, HEAD_MODEL_PATH.parent, HEAD_TEX_PATH.parent,
              HEAD_ITEM_PATH.parent, BED_MODEL_PATH.parent, BED_TEX_PATH.parent, BED_ITEM_PATH.parent):
        d.mkdir(parents=True, exist_ok=True)

    villager_type_texture(ADULT_PARTS, EYE_HOLES_ADULT, GILLS_ADULT, ACCENTS_ADULT).save(ADULT_SWAMP)
    villager_type_texture(BABY_PARTS, EYE_HOLES_BABY, GILLS_BABY, ACCENTS_BABY).save(BABY_SWAMP)
    print(f"wrote {ADULT_SWAMP}")
    print(f"wrote {BABY_SWAMP}")

    head_texture().save(HEAD_TEX_PATH)
    HEAD_MODEL_PATH.write_text(json.dumps(
        build_head(to_item_space_villager(merfolk_head_elements())), indent=2) + "\n")
    HEAD_ITEM_PATH.write_text(json.dumps(item_definition("claudesmp:item/merfolk_head"), indent=2) + "\n")
    print(f"wrote {HEAD_MODEL_PATH}")
    print(f"wrote {HEAD_TEX_PATH}")

    bed_icon_texture().save(BED_ICON_TEX_PATH)
    BED_ICON_MODEL_PATH.write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": "claudesmp:item/kelp_bed"}},
        indent=2) + "\n")
    BED_ICON_ITEM_PATH.write_text(json.dumps(item_definition("claudesmp:item/kelp_bed"), indent=2) + "\n")
    print(f"wrote {BED_ICON_MODEL_PATH}")

    bed_texture().save(BED_TEX_PATH)
    BED_MODEL_PATH.write_text(json.dumps(
        build_bed(kelp_bed_elements(), BED_TEX_REF, bed_face_uv), indent=2) + "\n")
    BED_ITEM_PATH.write_text(json.dumps(item_definition("claudesmp:item/kelp_bed_placed"), indent=2) + "\n")
    print(f"wrote {BED_MODEL_PATH}")


if __name__ == "__main__":
    main()
