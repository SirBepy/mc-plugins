"""Generates every resource pack asset for ItsLogiks' Merfolk wish (Shenron wish, granted
2026-10-09, Nuova Shenron): the merfolk plugin (module merfolk/) turns some villagers into underwater
Merfolk by (1) setting the villager's VillagerType to its variant's type (SWAMP for Kelp, JUNGLE for
Coral, picked from the villager's UUID), (2) putting a custom item (base minecraft:kelp, item_model
claudesmp:merfolk_head or claudesmp:merfolk_head_coral) in the villager's HEAD equipment slot, and
(3) placing "Kelp Beds" shown as an item display (claudesmp:kelp_bed_placed, item_display_transform
NONE). This script owns every pixel/JSON of that look:

  1. assets/minecraft/textures/entity/villager/type/{swamp,jungle}.png and .../baby/{swamp,jungle}.png
     -- the merfolk bodies. The adult textures are Joe's own skins (merfolk_skins/kelp.png and
     coral.png, 2026-10-09, drawn on the adult villager box-UV layout below) copied unchanged; the
     baby textures are remapped from them part by part, since BabyVillagerModel has its own layout.
  2. assets/claudesmp/items/merfolk_head{,_coral}.json + models/item/merfolk_head{,_coral}.json + their
     textures -- two small 3D fins per side of the head, worn in the villager's CustomHeadLayer and
     coloured to match each skin (the skins paint the face, gills and hair themselves).
  3. assets/claudesmp/items/kelp_bed.json (flat icon) and kelp_bed_placed.json (the 3D placed bed,
     item_display_transform NONE) + their textures.

----------------------------------------------------------------------------------------------------
PART 1 MATH -- villager box-UV (javap'd 2026-10-09 against C:\\tmp\\client-26.3.jar):

VillagerModel.createBodyModel() / BabyVillagerModel.createBodyModel() (javap'd): every part is
`CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d)`, which lays UV out with Minecraft's
standard box-UV algorithm (the same one guild_skins.py's face_rects() uses for Mannequin skins):

    top    = (u + d,         v,         w, d)
    bottom = (u + d + w,     v,         w, d)
    right  = (u,             v + d,     d, h)
    front  = (u + d,         v + d,     w, h)
    left   = (u + d + w,     v + d,     d, h)
    back   = (u + d + w + d, v + d,     w, h)

javap'd (u, v, w, h, d) per part:
    adult head=(0,0,8,10,8) hat=(32,0,8,10,8) hat_rim=(30,47,16,16,1) nose=(24,0,2,4,2)
          body=(16,20,8,12,6) jacket=(0,38,8,20,6) arms=(44,22,4,8,4) [both arms share it]
          arms_hold=(40,38,8,4,4) leg=(0,22,4,12,4) [both legs share it]
    baby  head=(0,0,8,8,7) hat=(0,30,8,8,7) hat_rim=(0,45,14,1,12) nose=(23,0,2,2,1)
          body=(0,15,4,5,3) bb_main=(16,21,4,6,3) [the robe, inflated 0.2 over body]
          right_hand=(36,15,2,4,2) and (16,15,2,4,2) [one part, two hands] middlearm=(24,17,4,2,2)
          right_leg=(8,23,2,3,2) left_leg=(0,23,2,3,2)

The client draws the shared base villager.png / villager_baby.png first, then the type texture as a
cutout over the same model, so opaque type pixels replace the base skin. Joe's skins are opaque over
every part's whole footprint (head, nose, body, jacket, arms, legs) and transparent only on the hat
and hat_rim, which stay free for the profession layer's hats: the face, eyes and gills come from the
skin, not the base. Vanilla's type textures carry no .mcmeta (checked in the jar), so none ship here.

Baby remap: each baby face is resampled nearest-neighbour from the matching adult part's same face
(pixel i of n takes source pixel floor((i + 0.5) * N / n)). For the 10-tall adult head front that
keeps rows 0,1,3,4,5,6,8,9, so the eye row (6) lands on the baby's row 5. The baby legs take the
adult legs' bottom rows instead, since only the feet show under the robe on both models.

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
z=-6 (javap'd nose addBox(-1,-1,-6,2,4,2)). The fins below root on the sides of that box behind
z=0 and sweep backwards, so they can never cover the eyes, which sit on the FRONT face (z=-4) only.

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
SKINS = ROOT / "merfolk_skins"

ADULT_PARTS = {  # (u, v, w, h, d), javap'd, see the module docstring
    "head": (0, 0, 8, 10, 8),
    "nose": (24, 0, 2, 4, 2),
    "body": (16, 20, 8, 12, 6),
    "jacket": (0, 38, 8, 20, 6),
    "arms": (44, 22, 4, 8, 4),
    "arms_hold": (40, 38, 8, 4, 4),
    "leg": (0, 22, 4, 12, 4),
}
# Each baby part's (u, v, w, h, d), the adult part it is resampled from, and how: "scale" resamples
# every face, "feet" keeps the adult side faces' bottom rows (only the feet show under either robe).
BABY_PARTS = [
    ((0, 0, 8, 8, 7), "head", "scale"),
    ((23, 0, 2, 2, 1), "nose", "scale"),
    ((0, 15, 4, 5, 3), "body", "scale"),
    ((16, 21, 4, 6, 3), "jacket", "scale"),
    ((36, 15, 2, 4, 2), "arms", "scale"),
    ((16, 15, 2, 4, 2), "arms", "scale"),
    ((24, 17, 4, 2, 2), "arms_hold", "scale"),
    ((8, 23, 2, 3, 2), "leg", "feet"),
    ((0, 23, 2, 3, 2), "leg", "feet"),
]

# Fin colours are sampled from each skin's own head so the 3D fins read as part of it: Kelp's teal
# scales, Coral's painted coral fin.
VARIANTS = {
    "kelp": {"type": "swamp", "head": "merfolk_head",
             "membrane": (70, 120, 114), "membrane_dark": (44, 86, 86), "spine": (118, 172, 160)},
    "coral": {"type": "jungle", "head": "merfolk_head_coral",
              "membrane": (240, 118, 92), "membrane_dark": (186, 74, 66), "spine": (255, 160, 128)},
}


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


def pick(n, size):
    """Nearest-neighbour source indices for shrinking `size` pixels to `n`."""
    return [int((i + 0.5) * size / n) for i in range(n)]


def baby_texture(adult):
    src = np.asarray(adult.convert("RGBA"))
    out = np.zeros((64, 64, 4), np.uint8)
    for (u, v, w, h, d), part, mode in BABY_PARTS:
        from_rects = face_rects(*ADULT_PARTS[part])
        for face, (x, y, fw, fh) in face_rects(u, v, w, h, d).items():
            sx, sy, sw, sh = from_rects[face]
            cols = [sx + c for c in pick(fw, sw)]
            if mode == "feet" and face not in ("top", "bottom"):
                rows = [sy + sh - fh + r for r in range(fh)]
            else:
                rows = [sy + r for r in pick(fh, sh)]
            out[y:y + fh, x:x + fw] = src[np.ix_(rows, cols)]
    return Image.fromarray(out, "RGBA")


def skin(name):
    return Image.open(SKINS / f"{name}.png").convert("RGBA")


# --- Part 2: merfolk_head item models (side fins) -------------------------------------------------

HEAD_TEX_SIZE = 16
HEAD_REGIONS = {
    "fin_base": (0, 0, 8, 8),
    "fin_tip": (8, 0, 16, 8),
}


def head_texture(variant):
    """Membrane with darker fin-ray streaks on the left half, plain spine colour on the right."""
    c = VARIANTS[variant]
    img = Image.new("RGBA", (HEAD_TEX_SIZE, HEAD_TEX_SIZE), (0, 0, 0, 0))
    px = img.load()
    for y in range(8):
        for x in range(8):
            px[x, y] = (c["membrane_dark"] if x % 3 == 2 else c["membrane"]) + (255,)
        for x in range(8, 16):
            px[x, y] = c["spine"] + (255,)
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


def build_head(elements, tex_ref):
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
        "textures": {"t": tex_ref, "particle": tex_ref},
        "elements": out,
        # Identity: numerically equivalent to NO_TRANSFORM, written explicitly so the fit math
        # above is on record in the file that uses it (same reasoning as dragonscale_helmet_model.py).
        "display": {"head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}},
    }


# Where the fin's root meets the head: just above eye height (eyes on row 6 of 10, y -4..-3) and
# behind the cheek, so the fin stays clear of the face.
FIN_ROOT = (4.0, -5.5, 1.0)
FIN_SWEEP = -35  # degrees about y; negative swings the tip toward +z, the back of the head


def fin(mirror=False):
    """Three spines fanning up and out from the side of the head, joined by membrane, the whole fin
    swept back about its root."""
    rot = (FIN_ROOT, (0, FIN_SWEEP, 0))
    boxes = [
        hbox((4.0, -6.6, 0.7), (6.9, -3.8, 1.3), "fin_base", rot),
        hbox((4.0, -8.2, 0.6), (4.7, -3.6, 1.4), "fin_tip", rot),
        hbox((5.1, -7.7, 0.6), (5.8, -3.8, 1.4), "fin_tip", rot),
        hbox((6.2, -7.0, 0.6), (6.9, -4.0, 1.4), "fin_tip", rot),
    ]
    return mirror_x(boxes) if mirror else boxes


def merfolk_head_elements():
    return fin(False) + fin(True)


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
    for d in (TYPE_DIR / "type", TYPE_DIR / "baby", PACK / "models" / "item", PACK / "textures" / "item",
              PACK / "items", BED_MODEL_PATH.parent, BED_TEX_PATH.parent, BED_ITEM_PATH.parent):
        d.mkdir(parents=True, exist_ok=True)

    full_model_ref = None
    for name, c in VARIANTS.items():
        adult = skin(name)
        adult.save(TYPE_DIR / "type" / f"{c['type']}.png")
        baby_texture(adult).save(TYPE_DIR / "baby" / f"{c['type']}.png")
        print(f"wrote type/{c['type']}.png and baby/{c['type']}.png from {name}")

        model_ref = f"claudesmp:item/{c['head']}"
        head_texture(name).save(PACK / "textures" / "item" / f"{c['head']}.png")
        if full_model_ref is None:
            model = build_head(to_item_space_villager(merfolk_head_elements()), model_ref)
            full_model_ref = model_ref
        else:
            # Same fins, own colours: inherit the elements and display, swap the texture.
            model = {"parent": full_model_ref, "textures": {"t": model_ref, "particle": model_ref}}
        (PACK / "models" / "item" / f"{c['head']}.json").write_text(json.dumps(model, indent=2) + "\n")
        (PACK / "items" / f"{c['head']}.json").write_text(json.dumps(item_definition(model_ref), indent=2) + "\n")
        print(f"wrote {c['head']} model, texture and item")

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
