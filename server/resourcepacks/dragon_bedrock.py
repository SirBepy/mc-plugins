"""Generates the Bedrock resource pack that lets Bedrock players see the Dragon Balls summon dragons.

Java players see Shenron and Porunga as item displays drawing dragon_models.py's item models. Geyser
can't translate item displays, so the dragonballs plugin also spawns one text display per part,
shown only to Bedrock players, and has Geyser turn each into a custom Bedrock entity
(claudesmp:dragon_<part>) through its custom entity API (bedrock/GeyserDragons.java). This pack
holds those entities: a client entity, a geometry and one baked texture per set for every part of
dragon_models.py's rig, so the two editions draw the same shapes.

Geometry: Bedrock geometry is stored mirrored in x relative to the Java item model (the same flip
Blockbench and Kas-tle's java2bedrock apply), so a cube's x range and pivot are negated, its x and y
rotations change sign and z keeps it; Bedrock, like Java 26.3's free element rotation, applies x
first, then y, then z. Each part is drawn at its rig size (16 units per block, no MODEL_SCALE
shrink: Bedrock has no -16..32 limit) around its pivot, which sits on the entity's position.

Pose: Geyser forwards the carrier's own position, but the plugin keeps the carrier's yaw and pitch
at 0 and sends the part's yaw, pitch and set as entity properties instead (claudesmp:yaw,
claudesmp:pitch, claudesmp:skin); the pose animation turns the root bone by them, x = pitch
(positive tips the front down, like Minecraft pitch) and y = yaw (Minecraft yaw). Size is Geyser's
own SCALE entity data.

Tints: Bedrock has no custom_model_data tints, so every set gets its own texture with the body and
belly/accent colours multiplied in, matching DragonModel.colors(). Pixels (48, 48) and (49, 48),
outside every material region, hold the plain body and accent colours so a Java test can check the
two tables still agree.

The pack goes into the dragonballs jar (dragonballs/src/main/resources/bedrock/), which registers it
with Geyser. Bedrock clients cache a pack by uuid and version, so the version's last number is a
hash of the content: any change reaches every client.

Run from anywhere: python server/resourcepacks/dragon_bedrock.py
"""

import hashlib
import io
import json
import math
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

import dragon_models as dm

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "dragonballs" / "src" / "main" / "resources" / "bedrock" / "dragonballs-dragons.mcpack"

NAMESPACE = "claudesmp"
PACK_UUID = "6f1d2b6e-3c1a-4c55-9d0e-6b8f0c2a7d41"
MODULE_UUID = "b0e7a1f4-58c2-4e0b-8a3d-2c9e5f6a1b73"
SETS = ("overworld", "nether", "end")  # skin index = BallSet ordinal
# Body and belly/accent colours per dragon and set, as in DragonModel.colors().
COLORS = {
    "shenron": {"overworld": (0x3E9A3A, 0xD9C77A), "nether": (0xB8261C, 0xF2A33A), "end": (0xFFC82E, 0xFFF4C0)},
    "porunga": {"overworld": (0x4FA040, 0xF0DCB0), "nether": (0x7B3FB8, 0xEBD8F7), "end": (0xFFC02A, 0xFFF1CF)},
}
SWATCH = (48, 48)

FACE_SWAP = {"east": "west", "west": "east"}  # the x mirror swaps the +x and -x faces


def identifier(part):
    return f"{NAMESPACE}:dragon_{part}"


def dragon_of(part):
    return part.split("_")[0]


def cube(element, pivot):
    """One Java rig element as a Bedrock cube, relative to the part pivot, mirrored in x."""
    f = [element["from"][k] - pivot[k] for k in range(3)]
    t = [element["to"][k] - pivot[k] for k in range(3)]
    size = [t[k] - f[k] for k in range(3)]
    out = {"origin": [r(-t[0]), r(f[1]), r(f[2])], "size": [r(s) for s in size]}
    if element["rot"]:
        o, (rx, ry, rz) = element["rot"]
        out["pivot"] = [r(-(o[0] - pivot[0])), r(o[1] - pivot[1]), r(o[2] - pivot[2])]
        out["rotation"] = [r(-rx), r(-ry), r(rz)]
    faces = {}
    for i, (face, (a, b)) in enumerate(dm.FACE_DIMS.items()):
        u0, v0, u1, v1 = [x * 4 for x in dm.face_uv(element["mat"], size[a], size[b], element["_seed"] * 6 + i)]
        faces[FACE_SWAP.get(face, face)] = {"uv": [r(u0), r(v0)], "uv_size": [r(u1 - u0), r(v1 - v0)]}
    out["uv"] = faces
    return out


def r(v):
    return round(v, 4)


def geometry(part, elements, pivot):
    cubes = []
    reach = 0.0
    for i, e in enumerate(elements):
        cubes.append(cube(dict(e, _seed=i), pivot))
        for x in (e["from"][0], e["to"][0]):
            for y in (e["from"][1], e["to"][1]):
                for z in (e["from"][2], e["to"][2]):
                    reach = max(reach, math.dist((x, y, z), pivot))
    # The root bone turns about the entity's position, so a box of the farthest corner's radius
    # holds the part at any yaw and pitch; Bedrock scales the bounds with the entity's own SCALE.
    bound = math.ceil(2 * reach / 16 + 1)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.{NAMESPACE}.dragon_{part}",
                "texture_width": 64,
                "texture_height": 64,
                "visible_bounds_width": bound,
                "visible_bounds_height": bound,
                "visible_bounds_offset": [0, 0, 0],
            },
            "bones": [{"name": "root", "pivot": [0, 0, 0], "cubes": cubes}],
        }],
    }


def client_entity(part):
    dragon = dragon_of(part)
    return {
        "format_version": "1.10.0",
        "minecraft:client_entity": {
            "description": {
                "identifier": identifier(part),
                "materials": {"default": "entity"},
                "textures": {s: f"textures/entity/dragon/{dragon}_{s}" for s in SETS},
                "geometry": {"default": f"geometry.{NAMESPACE}.dragon_{part}"},
                "animations": {"pose": f"animation.{NAMESPACE}.dragon.pose"},
                "scripts": {"animate": ["pose"]},
                "render_controllers": [f"controller.render.{NAMESPACE}.dragon"],
            }
        },
    }


def render_controllers():
    return {
        "format_version": "1.8.0",
        "render_controllers": {
            f"controller.render.{NAMESPACE}.dragon": {
                "arrays": {"textures": {"Array.skins": [f"Texture.{s}" for s in SETS]}},
                "geometry": "Geometry.default",
                "materials": [{"*": "Material.default"}],
                "textures": [f"Array.skins[query.property('{NAMESPACE}:skin')]"],
            }
        },
    }


def animations():
    return {
        "format_version": "1.8.0",
        "animations": {
            f"animation.{NAMESPACE}.dragon.pose": {
                "loop": True,
                "bones": {
                    "root": {
                        "rotation": [
                            f"query.property('{NAMESPACE}:pitch')",
                            f"query.property('{NAMESPACE}:yaw')",
                            0,
                        ]
                    }
                },
            }
        },
    }


def skin(base, dragon, set_name):
    """The greyscale atlas with this dragon's set colours multiplied into its tinted materials."""
    img = np.array(base, dtype=np.float64)
    body, accent = COLORS[dragon][set_name]
    for mat, index in dm.TINT.items():
        rgb = body if index == 0 else accent
        x0, y0, x1, y1 = dm.REGIONS[mat]
        img[y0:y1, x0:x1, :3] *= np.array([(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255]) / 255.0
    out = np.clip(np.round(img), 0, 255).astype(np.uint8)
    sx, sy = SWATCH
    for dx, rgb in ((0, body), (1, accent)):
        out[sy, sx + dx] = ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255)
    return Image.fromarray(out, "RGBA")


def png(image):
    buf = io.BytesIO()
    image.save(buf, "PNG", optimize=False)
    return buf.getvalue()


def files():
    """Every pack file but the manifest, path -> bytes."""
    out = {}
    base = dm.texture()
    for dragon in COLORS:
        for s in SETS:
            out[f"textures/entity/dragon/{dragon}_{s}.png"] = png(skin(base, dragon, s))
    for part, (elements, pivot) in dm.PARTS.items():
        out[f"models/entity/dragon_{part}.geo.json"] = dump(geometry(part, elements, pivot))
        out[f"entity/dragon_{part}.entity.json"] = dump(client_entity(part))
    out["render_controllers/dragon.render_controllers.json"] = dump(render_controllers())
    out["animations/dragon.animation.json"] = dump(animations())
    return out


def dump(obj):
    return (json.dumps(obj, indent=1) + "\n").encode()


def manifest(content):
    digest = hashlib.sha256()
    for path in sorted(content):
        digest.update(path.encode())
        digest.update(content[path])
    version = [1, 0, int(digest.hexdigest()[:7], 16)]
    return {
        "format_version": 2,
        "header": {
            "name": "Dragon Balls dragons",
            "description": "Shenron and Porunga for Bedrock players (claudesmp)",
            "uuid": PACK_UUID,
            "version": version,
            "min_engine_version": [1, 21, 0],
        },
        "modules": [{"type": "resources", "uuid": MODULE_UUID, "version": version}],
    }


def build():
    content = files()
    content["manifest.json"] = dump(manifest(content))
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted(content):
            info = zipfile.ZipInfo(path, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, content[path])
    return buf.getvalue()


def main():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    data = build()
    OUT.write_bytes(data)
    print(f"wrote {OUT.relative_to(ROOT)} ({len(data)} bytes, {len(dm.PARTS)} parts)")


if __name__ == "__main__":
    main()
