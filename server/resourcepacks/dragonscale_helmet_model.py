"""Generates the Dragon Skull helm: the Dragonscale helmet's 3D worn look in the player's HEAD
display context (Joe picked this over the "horned helm" draft, 2026-10-09; both were previewed
offline in .for_bepy/dragonscale-3d, never wired into the pack until now). Writes
assets/claudesmp/models/item/dragonscale_helmet_worn.json (free-rotation cuboids, 26.3 item-model
format) and its texture into claudesmp-items/. The flat 2D icon (dragonscale_helmet.json/.png) is
untouched and stays the model for every OTHER display context (inventory, ground, GUI, hand, etc) --
see assets/minecraft/items/netherite_helmet.json's "dragonscale" case, now a
`minecraft:display_context` select between the two.

----------------------------------------------------------------------------------------------------
EXACT PLACEMENT MATH (javap'd 2026-10-09 against C:\\tmp\\client-26.3.jar; see the dispatch report for
full bytecode). The design below is authored directly in the same head-pivot frame
.for_bepy/dragonscale-3d/render_preview.py uses (vanilla HumanoidModel's own head ModelPart: pivot
(0,0,0) at the neck, head box (-4,-8,-4) to (4,0,4), y DOWN, z<0 = front/face) -- the frame Joe
actually approved the look in. `to_item_space()` below converts those same coordinates into 26.3
item-model element space (y UP, the CuboidModelElement/BlockModel convention), by inverting the exact
transform chain a real client applies when it renders this model on a player's head:

1. `AvatarRenderer` (26.3's renamed PlayerRenderer; confirmed in its own bytecode) constructs
   `CustomHeadLayer` with the 3-arg constructor, which forwards to the 4-arg one with
   `Transforms.DEFAULT` -- javap'd its static initializer: `(yOffset=0, skullYOffset=0,
   horizontalScale=1, verticalScale=1)`. So the extra `pose.scale(horizontalScale, verticalScale,
   horizontalScale)` CustomHeadLayer.submit() does first is a no-op, and
   `getParentModel().root().translateAndRotate(poseStack)` is treated as identity too (the
   Avatar/Humanoid model's root bone has no pivot/rotation of its own in a neutral pose -- individual
   parts like head/body/arms animate themselves, not assumed to be mid-walk-cycle here).
2. `CustomHeadLayer.translateToHead(poseStack, DEFAULT)` (javap'd): `pose.translate(0, -0.25, 0)`,
   then `pose.rotateDegrees(Axis.YP, 180)`, then `pose.scale(0.625, -0.625, -0.625)`. PoseStack calls
   right-multiply the running matrix in call order, so as a single outer matrix applied to a point:
       M_outer = T(0,-0.25,0) . Ry(180deg) . S(0.625,-0.625,-0.625)
3. `ItemStackRenderState.submit()` then draws the headItem through `ItemTransform.apply()` (javap'd
   `net.minecraft.client.resources.model.cuboid.ItemTransform`): when the transform instance isn't
   literally the `NO_TRANSFORM` singleton it takes the general branch -- translate, rotate, scale,
   then ALWAYS `pose.translate(-0.5,-0.5,-0.5)` at the end. This model's `display.head` is written
   explicitly as identity (rotation [0,0,0], translation [0,0,0], scale [1,1,1]): every term in that
   branch besides the final `-0.5` translate is then a no-op, so it's numerically identical to
   `NO_TRANSFORM` (confirmed from `apply()`'s own bytecode: `NO_TRANSFORM`'s shortcut is *also* just
   `pose.translate(-0.5,-0.5,-0.5)`). This is the same "a block-space model needs no extra head
   transform" fact the first (preview-only) pass found by reading `block/block.json`'s missing "head"
   entry -- now confirmed from the apply() code path itself instead of inferred from a sibling asset.
4. A mesh vertex's raw element coordinate (what goes in this file's "from"/"to", call it `v`, in the
   usual 0-16ish pixel convention) becomes `p_model = v/16` (blocks), and the full chain renders it at
       P = M_outer . (p_model - (0.5,0.5,0.5))                      relative to the head pivot.

We want P to equal the SAME point our approved preview used: `(ox,oy,oz)/16`, where (ox,oy,oz) is a
build_helms.py-style coordinate in the HumanoidModel head-pivot frame. Solving for v (full algebra in
the 2026-10-09 dispatch report; the short version: `Ry(180deg)` flips x and z, `S(...)`'s signs on y
and z partially cancel that, and since |Y scale| == |Z scale| == 0.625 the whole linear part composes
to a clean per-axis map) gives:

    vx = 8 - 1.6*ox         vy = 1.6 - 1.6*oy         vz = 8 + 1.6*oz

and because that linear map is diag(-1.6, -1.6, 1.6) (x and y flip sign, z doesn't, and it's the same
magnitude on every axis so a rotation by angle "a" around X or Y comes out as angle "-a" in item
space, while a rotation around Z is unchanged -- verified by conjugating each axis's rotation matrix
by that diagonal map): an element's own `rotation.origin` transforms the same way as any point, and
`{x, y, z}` becomes `{-x, -y, z}`.

This was validated, not just derived: `check_ingame_placement.py` re-implements this SAME forward
chain (not the inverse formulas above) with real 4x4 matrices against the actual written JSON, and
re-renders it through render_preview.py's full-figure rig; the result is pixel-for-pixel the same
head position the hand-placed preview used (see helm_skull_ingame_*.png vs helm_skull_*.png).
----------------------------------------------------------------------------------------------------

Run: python server/resourcepacks/dragonscale_helmet_model.py
"""
import json
import math
from pathlib import Path

from PIL import Image

import dragonscale_armor as da  # reuses the Dragonscale palette, not redefining it

ROOT = Path(__file__).resolve().parent
PACK = ROOT / "claudesmp-items" / "assets" / "claudesmp"
MODEL_PATH = PACK / "models" / "item" / "dragonscale_helmet_worn.json"
TEX_PATH = PACK / "textures" / "item" / "dragonscale_helmet_worn.png"
TEX_REF = "claudesmp:item/dragonscale_helmet_worn"

TEX_SIZE = 32
REGIONS = {
    "shell": (0, 0, 16, 16),       # main plate: the dragonscale shingle pattern's dark core tone
    "trim": (16, 0, 24, 8),        # violet brow/eye-ridge trim
    "horn": (24, 0, 32, 8),        # lighter bone-violet horn material, for contrast against the shell
    "horn_tip": (24, 8, 32, 16),   # darker horn tip
    "dark": (16, 8, 24, 16),       # near-black openings, teeth gaps, nostril interior
    "spike": (8, 16, 16, 24),      # back-spike colour
    "teeth": (0, 24, 8, 32),       # pale teeth
}


def texture():
    img = Image.new("RGBA", (TEX_SIZE, TEX_SIZE), (0, 0, 0, 0))
    px = img.load()

    def fill(region, color):
        x0, y0, x1, y1 = region
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[x, y] = color + (255,)

    fill(REGIONS["shell"], da.CORE_B)
    fill(REGIONS["trim"], da.EDGE_TRIM)
    fill(REGIONS["horn"], da.RIM_B)
    fill(REGIONS["horn_tip"], da._mix(da.RIM_A, da.GROOVE, 0.4))
    fill(REGIONS["dark"], da.GROOVE)
    fill(REGIONS["spike"], da.CREST)
    fill(REGIONS["teeth"], da._mix(da.RIM_B, (255, 255, 255), 0.35))
    for (x0, y0, x1, y1), hi in (
        (REGIONS["shell"], da.RIM_B), (REGIONS["horn"], da.EDGE_TRIM), (REGIONS["spike"], da.CREST_TRIM),
    ):
        for x in range(x0, x1):
            px[x, y0] = hi + (255,)
    return img


# --- element authoring helpers (same shape as dragon_models.py's box()/part(); kept local since the
# head-pivot frame and the -16..32 bounds check here are specific to this model) ------------------

def box(f, t, mat, rot=None):
    return {"from": list(f), "to": list(t), "mat": mat, "rot": rot}


def mirror_x(elements):
    """Mirrors across the head's own centre plane x=0 (left/right horn symmetry)."""
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


FACE_DIMS = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}


def face_uv(mat):
    x0, y0, x1, y1 = REGIONS[mat]
    s = TEX_SIZE / 16
    return [x0 / s, y0 / s, x1 / s, y1 / s]


def build(elements):
    out = []
    for i, e in enumerate(elements):
        f, t = e["from"], e["to"]
        for v in list(f) + list(t):
            if v < -16 or v > 32:
                raise SystemExit(f"element {i} {f}..{t} leaves the -16..32 item-model range")
        faces = {face: {"uv": face_uv(e["mat"]), "texture": "#t"} for face in FACE_DIMS}
        el = {"from": f, "to": t, "faces": faces}
        if e["rot"]:
            o, (rx, ry, rz) = e["rot"]
            el["rotation"] = {"origin": list(o), "x": rx, "y": ry, "z": rz}
        out.append(el)
    return {
        "textures": {"t": TEX_REF, "particle": TEX_REF},
        "elements": out,
        # Identity: numerically equivalent to NO_TRANSFORM (see the derivation above, step 3), written
        # explicitly so the fit math documented here is on record in the file that uses it.
        "display": {"head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}},
    }


def to_item_space(elements):
    """Converts elements from the HumanoidModel head-pivot frame (y-down; the frame Joe approved the
    look in) into 26.3 item-model element space (y-up). See the module docstring for the derivation."""
    def pt(p):
        x, y, z = p
        return (8 - 1.6 * x, 1.6 - 1.6 * y, 8 + 1.6 * z)

    out = []
    for e in elements:
        f2, t2 = pt(e["from"]), pt(e["to"])
        new_from = [min(f2[i], t2[i]) for i in range(3)]
        new_to = [max(f2[i], t2[i]) for i in range(3)]
        rot = None
        if e["rot"]:
            o, (rx, ry, rz) = e["rot"]
            rot = (pt(o), (-rx, -ry, rz))
        out.append(box(new_from, new_to, e["mat"], rot))
    return out


# --- shell (0.5px clearance over the 8x8x8 head, open face below the brow) + the dragon skull's own
# features, all in the HumanoidModel head-pivot frame, ported verbatim from the approved
# .for_bepy/dragonscale-3d/build_helms.py draft (Joe approved this exact shape 2026-10-09) ---------

def hugging_shell():
    return [
        box((-4.5, -8.5, -4.5), (4.5, -6.5, 4.5), "shell"),          # main dome, close over the skull
        box((-3.5, -9, -3.5), (3.5, -8.5, 3.5), "shell"),            # stepped-in top cap (1px smaller)
        box((-4.5, -6.5, 0.5), (4.5, 0.5, 4.5), "shell"),            # back plate, down past the jaw
        box((-4.5, -6.5, -4.5), (-3.5, 0.5, 4.5), "shell"),          # left cheek guard, full depth
        box((3.5, -6.5, -4.5), (4.5, 0.5, 4.5), "shell"),            # right cheek guard, full depth
        box((-4.5, -7, -4.5), (4.5, -6.5, -2.5), "trim"),            # brow band over the open face
    ]


def horn_chain(root, segs, mirror=False):
    """segs: list of (direction (dx,dy,dz), length, width). 4-ish tapering segments rising from
    `root`, each rotated (x,y only) to point along `direction`, chained end-to-end."""
    out = []
    pos = list(root)
    n = len(segs)
    for i, (d, length, w) in enumerate(segs):
        dx, dy, dz = d
        norm = math.sqrt(dx * dx + dy * dy + dz * dz)
        dx, dy, dz = dx / norm, dy / norm, dz / norm
        rx = -math.degrees(math.asin(max(-1.0, min(1.0, dy))))
        ry = math.degrees(math.atan2(dx, dz))
        hw = w / 2
        f = (pos[0] - hw, pos[1] - hw, pos[2])
        t = (pos[0] + hw, pos[1] + hw, pos[2] + length)
        mat = "horn_tip" if i == n - 1 else "horn"
        out.append(box(f, t, mat, (tuple(pos), (rx, ry, 0))))
        pos = [pos[0] + dx * length, pos[1] + dy * length, pos[2] + dz * length]
    return mirror_x(out) if mirror else out


HORN_SEGS = [
    ((-0.35, -0.85, 0.25), 2.6, 2.0),
    ((-0.15, -0.55, 0.75), 2.6, 1.6),
    ((0.05, -0.25, 0.95), 2.2, 1.2),
    ((0.35, -0.55, 0.65), 1.8, 0.9),
]
SHORT_HORN_SEGS = [(d, length * 0.65, w * 0.85) for d, length, w in HORN_SEGS]


def skull_helm():
    shell = hugging_shell()
    snout = [
        box((-3, -7, -8.5), (3, -5, -4.5), "shell"),                 # upper jaw/snout, 4px forward, 6px wide
        box((-0.9, -6.3, -8.5), (-0.3, -5.7, -7.9), "dark"),         # left nostril
        box((0.3, -6.3, -8.5), (0.9, -5.7, -7.9), "dark"),           # right nostril
    ]
    teeth = [
        box((x, -5, -8.3), (x + 0.6, -4.5, -8.0), "teeth")
        for x in (-2.4, -1.4, -0.4, 0.6, 1.6)
    ]  # hang from the snout's lower edge at its FRONT tip
    brow = [box((-4, -7.3, -4.5), (4, -7, -2.8), "trim")]
    spikes = [
        box((-0.6, -8 - 0.4 * (3 - i), 0.6 + i * 1.0), (0.6, -6.5 - 0.4 * (3 - i), 1.2 + i * 1.0), "spike")
        for i in range(4)
    ]  # a descending row down the back of the skull
    horns = horn_chain((-4.3, -6.7, -1), SHORT_HORN_SEGS) + horn_chain((-4.3, -6.7, -1), SHORT_HORN_SEGS, mirror=True)
    return shell + snout + teeth + brow + spikes + horns


def main():
    MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    TEX_PATH.parent.mkdir(parents=True, exist_ok=True)
    texture().save(TEX_PATH)
    model = build(to_item_space(skull_helm()))
    MODEL_PATH.write_text(json.dumps(model, indent=2))
    print(f"wrote {MODEL_PATH}")
    print(f"wrote {TEX_PATH}")


if __name__ == "__main__":
    main()
