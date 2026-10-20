"""Checks the built join pack: the Pollos gun and katana looks resolve to a model and texture inside
the zip, the gunshot Tweaks plays resolves to an .ogg inside it, the Dragon Radar and dragon models
are wired up and have the expected pixels, phantom sounds carry 32 blocks, the Salamanca cousins'
pistol resolves, and every other crossbow, netherite sword, compass and vanilla sound still uses the
vanilla 26.3 definition (bows are left alone).

Run from server/resourcepacks/: python -m unittest test_build_server_pack
"""

import io
import json
import re
import tempfile
import unittest
import zipfile
from pathlib import Path

import build_server_pack
from PIL import Image

ROOT = build_server_pack.ROOT

# The 26.3 client jar's own assets/minecraft/items/crossbow.json model.
VANILLA_CROSSBOW = {
    "type": "minecraft:select",
    "cases": [
        {"model": {"type": "minecraft:model", "model": "minecraft:item/crossbow_arrow"}, "when": "arrow"},
        {"model": {"type": "minecraft:model", "model": "minecraft:item/crossbow_firework"}, "when": "rocket"},
    ],
    "fallback": {
        "type": "minecraft:condition",
        "on_false": {"type": "minecraft:model", "model": "minecraft:item/crossbow"},
        "on_true": {
            "type": "minecraft:range_dispatch",
            "entries": [
                {"model": {"type": "minecraft:model", "model": "minecraft:item/crossbow_pulling_1"}, "threshold": 0.58},
                {"model": {"type": "minecraft:model", "model": "minecraft:item/crossbow_pulling_2"}, "threshold": 1.0},
            ],
            "fallback": {"type": "minecraft:model", "model": "minecraft:item/crossbow_pulling_0"},
            "property": "minecraft:crossbow/pull",
        },
        "property": "minecraft:using_item",
    },
    "property": "minecraft:charge_type",
}

# The 26.3 client jar's own assets/minecraft/items/netherite_sword.json model.
VANILLA_NETHERITE_SWORD = {"type": "minecraft:model", "model": "minecraft:item/netherite_sword"}


def _vanilla_netherite_armor_trim_select(piece):
    # The 26.3 client jar's own assets/minecraft/items/netherite_<piece>.json model: an 11-way
    # select on trim_material (one case per vanilla trim, quartz..resin) falling back to the plain
    # piece. Dragonscale's wrapper must keep this verbatim as its own fallback.
    materials = ["quartz", "iron", "netherite", "redstone", "copper", "gold", "emerald", "diamond",
                 "lapis", "amethyst", "resin"]
    return {
        "type": "minecraft:select",
        "cases": [
            {
                "model": {"type": "minecraft:model", "model": f"minecraft:item/netherite_{piece}_{m}_trim"},
                "when": f"minecraft:{m}",
            }
            for m in materials
        ],
        "fallback": {"type": "minecraft:model", "model": f"minecraft:item/netherite_{piece}"},
        "property": "minecraft:trim_material",
    }


def _vanilla_compass_branch(target):
    # The 26.3 client jar's own compass.json: 33 entries, compass_16 at both ends (frames
    # 16..31 then 00..16) so the needle loops back around to vanilla's own frame 16.
    order = list(range(16, 32)) + list(range(0, 17))
    entries = [
        {
            "model": {"type": "minecraft:model", "model": f"minecraft:item/compass_{nn:02d}"},
            "threshold": 0.0 if i == 0 else i - 0.5,
        }
        for i, nn in enumerate(order)
    ]
    return {
        "type": "minecraft:range_dispatch",
        "entries": entries,
        "property": "minecraft:compass",
        "scale": 32.0,
        "target": target,
    }


# The 26.3 client jar's own assets/minecraft/items/compass.json model.
VANILLA_COMPASS = {
    "type": "minecraft:condition",
    "component": "minecraft:lodestone_tracker",
    "on_false": _vanilla_compass_branch("spawn"),
    "on_true": _vanilla_compass_branch("lodestone"),
    "property": "minecraft:has_component",
}


def _quat_matrix(q):
    x, y, z, w = q
    return [
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ]


def _mat_vec(m, v):
    return [sum(m[r][c] * v[c] for c in range(3)) for r in range(3)]


def _special_transformation(t):
    # An item definition's "transformation": translation . left_rotation . scale . right_rotation.
    def apply(p):
        p = _mat_vec(_quat_matrix(t["right_rotation"]), p)
        p = [a * s for a, s in zip(p, t["scale"])]
        p = _mat_vec(_quat_matrix(t["left_rotation"]), p)
        return [a + b for a, b in zip(p, t["translation"])]
    return apply


def _worn_head_item(p, local=lambda p: p):
    """Where the 26.3 client draws point p (blocks, item model space) of a worn head item with an
    identity display.head, in the head bone's frame (blocks, y down, -z = face), javap-verified on the
    client jar: ItemStackRenderState$LayerRenderState applies the special model's transformation
    (`local`), then ItemTransform's -0.5 translate, then CustomHeadLayer.translateToHead's
    T(0,-0.25,0) . Ry(180) . S(0.625,-0.625,-0.625)."""
    x, y, z = [a - 0.5 for a in local(p)]
    return [-0.625 * x, -0.25 - 0.625 * y, 0.625 * z]


def asset(ref, kind, ext):
    namespace, path = ref.split(":", 1)
    return f"assets/{namespace}/{kind}/{path}.{ext}"


class PublishedNameTest(unittest.TestCase):
    def test_each_pack_is_published_under_its_own_sha1(self):
        self.assertEqual(build_server_pack.published_name("1490144e0322108fc07f358d1e944992925c0c4e"),
                         "ClaudeSMP-server-pack-26.3-1490144e.zip")


class BuiltPackTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        tmp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(tmp.cleanup)
        out = Path(tmp.name) / "pack.zip"
        build_server_pack.build(out)
        with zipfile.ZipFile(out) as zf:
            cls.entries = {name: zf.read(name) for name in zf.namelist()}

    def json(self, name):
        self.assertIn(name, self.entries)
        return json.loads(self.entries[name])

    def assert_look_resolves(self, item, when):
        definition = self.json(f"assets/minecraft/items/{item}.json")["model"]
        self.assertEqual(definition["property"], "minecraft:custom_model_data")
        case = next(c for c in definition["cases"] if c["when"] == when)
        model = self.json(asset(case["model"]["model"], "models", "json"))
        self.assertIn(asset(model["textures"]["layer0"], "textures", "png"), self.entries)

    def resolved(self, ref):
        """A model's layer0 texture and display contexts, following its parent chain in the zip."""
        texture, display = None, {}
        while ref.startswith("claudesmp:"):
            model = self.json(asset(ref, "models", "json"))
            texture = texture or model.get("textures", {}).get("layer0")
            display = {**model.get("display", {}), **display}
            ref = model["parent"]
        return texture, display

    def test_pollos_gun_has_a_look_per_crossbow_state(self):
        definition = self.json("assets/minecraft/items/crossbow.json")["model"]
        gun = next(c for c in definition["cases"] if c["when"] == "pollos_gun")["model"]
        self.assertEqual(gun["property"], "minecraft:charge_type")
        states = {c["when"]: c["model"]["model"] for c in gun["cases"]}
        self.assertEqual(gun["fallback"]["property"], "minecraft:using_item")
        states["drawing"] = gun["fallback"]["on_true"]["model"]
        self.assertEqual(set(states), {"arrow", "rocket", "drawing"})
        textures, displays = {}, {}
        for state, ref in states.items():
            texture, display = self.resolved(ref)
            self.assertIn(asset(texture, "textures", "png"), self.entries, state)
            for context in ("thirdperson_righthand", "firstperson_righthand"):
                self.assertIn(context, display, state)
            textures[state], displays[state] = texture, display
        # Drawing lies flat like a vanilla crossbow; loaded is held upright.
        self.assertEqual(displays["arrow"], displays["rocket"])
        self.assertNotEqual(displays["drawing"]["thirdperson_righthand"], displays["arrow"]["thirdperson_righthand"])
        # Unloaded is the first version: the original gold pistol on the vanilla crossbow display.
        unloaded = self.json(asset(gun["fallback"]["on_false"]["model"], "models", "json"))
        self.assertEqual(unloaded["parent"], "minecraft:item/crossbow")
        self.assertNotIn("display", unloaded)
        textures["unloaded"] = unloaded["textures"]["layer0"]
        self.assertIn(asset(textures["unloaded"], "textures", "png"), self.entries)
        self.assertEqual(len(set(textures.values())), 4)

    def test_pollos_gunshot_is_the_sound_tweaks_plays(self):
        listener = (ROOT / "tweaks/src/main/java/dev/sirbepy/mc/tweaks/listener/PollosGunListener.java").read_text()
        played = re.search(r'SHOT_SOUND = "([^"]+)"', listener).group(1)
        namespace, event = played.split(":", 1)
        sounds = self.json(f"assets/{namespace}/sounds.json")
        for sound in sounds[event]["sounds"]:
            self.assertIn(asset(sound, "sounds", "ogg"), self.entries)
        subtitles = self.json(f"assets/{namespace}/lang/en_us.json")
        self.assertIn(sounds[event]["subtitle"], subtitles)

    def test_phantom_sounds_carry_32_blocks_and_the_rest_stay_vanilla(self):
        # The Tweaks phantom buff replays the screech for players 16-32 blocks out; these entries
        # are vanilla 26.3's phantom sounds with only the attenuation stretched from 16 to 32.
        listener = (ROOT / "tweaks/src/main/java/dev/sirbepy/mc/tweaks/listener/PhantomBuffListener.java").read_text()
        pack_range = float(re.search(r"PACK_SOUND_RANGE = ([\d.]+);", listener).group(1))
        sounds = self.json("assets/minecraft/sounds.json")
        self.assertEqual(set(sounds), {f"entity.phantom.{e}" for e in ("ambient", "bite", "death", "flap", "hurt", "swoop")})
        for event, entry in sounds.items():
            self.assertTrue(entry["replace"], event)
            self.assertEqual(entry["subtitle"], f"subtitles.{event}")
            for sound in entry["sounds"]:
                self.assertTrue(sound["name"].startswith("mob/phantom/"), event)
                self.assertEqual(sound["attenuation_distance"], pack_range, event)
        self.assertFalse(any(name.startswith("assets/minecraft/sounds/") for name in self.entries))

    def test_other_crossbows_stay_vanilla(self):
        crossbow = self.json("assets/minecraft/items/crossbow.json")["model"]
        self.assertEqual(crossbow["fallback"], VANILLA_CROSSBOW)

    def test_katana_resolves_to_its_texture(self):
        self.assert_look_resolves("netherite_sword", "katana")

    def test_katana_blade_is_longer_at_the_same_pixel_size(self):
        model = self.json("assets/claudesmp/models/item/katana.json")
        png = self.entries["assets/claudesmp/textures/item/katana.png"]
        width = int.from_bytes(png[16:20], "big")
        self.assertEqual(width, 32)
        # Twice vanilla item/handheld's in-hand scale, so a 32 px texel is as big as a 16 px one.
        self.assertEqual(model["display"]["thirdperson_righthand"]["scale"], [1.7, 1.7, 1.7])
        self.assertEqual(model["display"]["firstperson_righthand"]["scale"], [1.36, 1.36, 1.36])

    def test_other_netherite_swords_stay_vanilla(self):
        sword = self.json("assets/minecraft/items/netherite_sword.json")["model"]
        self.assertEqual(sword["fallback"], VANILLA_NETHERITE_SWORD)

    def test_dragonscale_equipment_asset_has_both_layers_at_vanilla_armor_size(self):
        # claudesmp:dragonscale is the Ender Dragon kill reward's worn look (the endfight plugin
        # sets equippable.asset_id to it); both layers must exist at the same size as vanilla's own
        # netherite armor layers so the client's humanoid/humanoid_leggings model fits them.
        equipment = self.json("assets/claudesmp/equipment/dragonscale.json")
        self.assertEqual(set(equipment["layers"]), {"humanoid", "humanoid_leggings", "wings"})
        for layer, ref in (("humanoid", "claudesmp:dragonscale"), ("humanoid_leggings", "claudesmp:dragonscale")):
            textures = [l["texture"] for l in equipment["layers"][layer]]
            self.assertEqual(textures, [ref], layer)
            path = f"assets/{ref.split(':', 1)[0]}/textures/entity/equipment/{layer}/{ref.split(':', 1)[1]}.png"
            self.assertIn(path, self.entries, layer)
            png = Image.open(io.BytesIO(self.entries[path]))
            self.assertEqual(png.size, (64, 32), layer)

    def test_dragonscale_equipment_asset_has_a_wings_layer_sized_like_vanilla_elytra(self):
        # WingsLayer (26.3 client jar, javap-verified 2026-10-09) reads only
        # HumanoidRenderState.chestEquipment, so a "wings" entry on this shared equipment asset can
        # only ever render while Dragonscale is worn as the chestplate; a helmet/leggings/boots
        # wearer never triggers it even though they point at the same asset file.
        equipment = self.json("assets/claudesmp/equipment/dragonscale.json")
        self.assertIn("wings", equipment["layers"])
        textures = [l["texture"] for l in equipment["layers"]["wings"]]
        self.assertEqual(textures, ["claudesmp:dragonscale"])
        path = "assets/claudesmp/textures/entity/equipment/wings/dragonscale.png"
        self.assertIn(path, self.entries)
        png = Image.open(io.BytesIO(self.entries[path]))
        # Vanilla's own assets/minecraft/textures/entity/equipment/wings/elytra.png (26.3 client
        # jar) is 64x32; ElytraModel's cubes carry fixed UVs into this sheet, so a mismatched size
        # would scramble the membrane across the wrong faces.
        self.assertEqual(png.size, (64, 32))

    def test_dragonscale_armor_resolves_on_all_four_pieces_with_vanilla_trims_kept_as_fallback(self):
        # Joe, 2026-10-09: the Ender Dragon kill reward is real netherite armor with no
        # enchantments, reskinned by custom_model_data=["dragonscale"]; every other netherite piece
        # (including any trimmed one) must still render exactly as vanilla's own item model does.
        # The helmet's "dragonscale" case is a display_context select (see the test below) whose
        # fallback is the same flat 2D icon the other three pieces point at directly.
        for piece in ("helmet", "chestplate", "leggings", "boots"):
            definition = self.json(f"assets/minecraft/items/netherite_{piece}.json")["model"]
            self.assertEqual(definition["property"], "minecraft:custom_model_data")
            case = next(c for c in definition["cases"] if c["when"] == "dragonscale")
            icon_ref = case["model"]["fallback"]["model"] if piece == "helmet" else case["model"]["model"]
            model = self.json(asset(icon_ref, "models", "json"))
            self.assertEqual(model["parent"], "minecraft:item/generated")
            self.assertIn(asset(model["textures"]["layer0"], "textures", "png"), self.entries, piece)
            png = Image.open(io.BytesIO(self.entries[asset(model["textures"]["layer0"], "textures", "png")]))
            self.assertEqual(png.size, (16, 16), piece)
            self.assertEqual(definition["fallback"], _vanilla_netherite_armor_trim_select(piece), piece)

    def test_dragonscale_helmet_selects_the_3d_worn_model_only_in_the_head_display_context(self):
        # The Dragon Skull helm (Joe picked it over the horned-helm draft, 2026-10-09) only makes
        # sense worn on an actual head; every other display context (inventory, ground, GUI, hand)
        # keeps the flat 2D icon. "minecraft:display_context" and "head" are the 26.3 client jar's
        # own property id and ItemDisplayContext.HEAD serialized name (javap-verified 2026-10-09).
        definition = self.json("assets/minecraft/items/netherite_helmet.json")["model"]
        case = next(c for c in definition["cases"] if c["when"] == "dragonscale")
        select = case["model"]
        self.assertEqual(select["type"], "minecraft:select")
        self.assertEqual(select["property"], "minecraft:display_context")
        head_case = next(c for c in select["cases"] if c["when"] == "head")
        self.assertEqual(
            head_case["model"], {"type": "minecraft:model", "model": "claudesmp:item/dragonscale_helmet_worn"}
        )
        self.assertEqual(select["fallback"], {"type": "minecraft:model", "model": "claudesmp:item/dragonscale_helmet"})

        worn = self.json("assets/claudesmp/models/item/dragonscale_helmet_worn.json")
        self.assertGreater(len(worn["elements"]), 0)
        # Identity: numerically equivalent to the engine's own NO_TRANSFORM default (see
        # dragonscale_helmet_model.py's derivation), written explicitly so the fit math is on record.
        self.assertEqual(worn["display"]["head"], {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]})
        for el in worn["elements"]:
            for v in el["from"] + el["to"]:
                self.assertGreaterEqual(v, -16, el)
                self.assertLessEqual(v, 32, el)
        tex_path = asset(worn["textures"]["t"], "textures", "png")
        self.assertIn(tex_path, self.entries)
        Image.open(io.BytesIO(self.entries[tex_path])).load()  # decodes without error

    def test_dragonscale_helm_clears_the_skin_hat_layer(self):
        # The hat layer is the 8px head cube inflated by 0.5px (planes x,z = +-4.5px, top y = -8.5px).
        # A helm face lying on one of those planes z-fights with it and the head shows through (Joe
        # on live, 2026-10-10), so no axis-aligned face may sit there, and the shell's outside sits
        # 1px off the head like vanilla's helmet layer.
        worn = self.json("assets/claudesmp/models/item/dragonscale_helmet_worn.json")
        lo, hi = [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
        for el in worn["elements"]:
            if "rotation" in el:
                continue
            corners = [_worn_head_item([v / 16 for v in c]) for c in (el["from"], el["to"])]
            px = [[16 * a for a in c] for c in corners]
            for axis, planes in ((0, (-4.5, 4.5)), (1, (-8.5,)), (2, (-4.5, 4.5))):
                for c in px:
                    for plane in planes:
                        self.assertGreater(abs(c[axis] - plane), 0.01, (el["from"], el["to"]))
            for axis in range(3):
                lo[axis] = min(lo[axis], *(c[axis] for c in px))
                hi[axis] = max(hi[axis], *(c[axis] for c in px))
        self.assertLessEqual(lo[0], -5.0)
        self.assertGreaterEqual(hi[0], 5.0)
        self.assertLessEqual(lo[1], -9.0)
        self.assertGreaterEqual(hi[2], 5.0)

    def test_dragonscale_helm_hides_a_crown_on_the_hat_layer(self):
        # ItsLogiks's skin (live, 2026-10-10) paints a crown on the top two texel rows of the hat
        # layer's four sides (rows are 9/8 px tall from its top at y = -8.5). Every point of that
        # band must have a helm element in front of it, seen straight on.
        worn = self.json("assets/claudesmp/models/item/dragonscale_helmet_worn.json")
        boxes = []
        for el in worn["elements"]:
            if "rotation" in el:
                continue
            a, b = (_worn_head_item([v / 16 for v in c]) for c in (el["from"], el["to"]))
            boxes.append(([16 * min(p, q) for p, q in zip(a, b)], [16 * max(p, q) for p, q in zip(a, b)]))
        crown_bottom = -8.5 + 2 * 9 / 8
        steps = [i / 8 for i in range(9)]
        for normal_axis, sign in ((2, -1), (2, 1), (0, -1), (0, 1)):  # front, back, sides
            across = 0 if normal_axis == 2 else 2
            for s in steps:
                for t in steps:
                    point = [0.0, 0.0, 0.0]
                    point[normal_axis] = 4.5 * sign
                    point[across] = -4.5 + 9 * s
                    point[1] = -8.5 + (crown_bottom + 8.5) * t
                    hidden = any(
                        lo[across] <= point[across] <= hi[across] and lo[1] <= point[1] <= hi[1]
                        and (hi[normal_axis] > 4.5 if sign > 0 else lo[normal_axis] < -4.5)
                        for lo, hi in boxes
                    )
                    self.assertTrue(hidden, point)

    def test_dragonscale_helm_has_no_coplanar_faces_of_different_colours(self):
        # Two elements' faces on one plane, facing the same way, speckle in a real client, which
        # shows when their colours differ (the violet brow band on the dark dome's front did).
        names = {"north": (2, 0), "south": (2, 1), "west": (0, 0), "east": (0, 1), "down": (1, 0), "up": (1, 1)}
        worn = self.json("assets/claudesmp/models/item/dragonscale_helmet_worn.json")
        boxes = [el for el in worn["elements"] if "rotation" not in el]

        def covered(point):  # a face whose outside lies in another box can't be seen
            return any(all(b["from"][k] < point[k] < b["to"][k] for k in range(3)) for b in boxes)

        for i, a in enumerate(boxes):
            for b in boxes[i + 1:]:
                for face, (axis, side) in names.items():
                    if a["faces"][face]["uv"] == b["faces"][face]["uv"]:
                        continue
                    plane_a = (a["from"], a["to"])[side][axis]
                    plane_b = (b["from"], b["to"])[side][axis]
                    if abs(plane_a - plane_b) > 1e-6:
                        continue
                    lo = [max(a["from"][k], b["from"][k]) for k in range(3)]
                    hi = [min(a["to"][k], b["to"][k]) for k in range(3)]
                    if any(hi[k] - lo[k] <= 1e-6 for k in range(3) if k != axis):
                        continue
                    centre = [(lo[k] + hi[k]) / 2 for k in range(3)]
                    centre[axis] = plane_a + (0.01 if side else -0.01)
                    self.assertTrue(covered(centre), (face, a["from"], b["from"]))

    def test_final_blow_helm_draws_the_vanilla_dragon_head_model(self):
        # The final blow's reward (Joe, 2026-10-09) is a netherite helmet that looks exactly like a
        # dragon head, in the inventory and worn. Outside the head context the case is a verbatim
        # copy of the 26.3 client jar's own assets/minecraft/items/dragon_head.json model, so it
        # tracks vanilla's look.
        definition = self.json("assets/minecraft/items/netherite_helmet.json")["model"]
        case = next(c for c in definition["cases"] if c["when"] == "dragon_head")
        select = case["model"]
        self.assertEqual(select["property"], "minecraft:display_context")
        self.assertEqual(select["fallback"], {
            "type": "minecraft:special",
            "base": "minecraft:item/dragon_head",
            "model": {"type": "minecraft:head", "kind": "dragon"},
            "transformation": {
                "left_rotation": [1.0, 0.0, 0.0, -0.0],
                "right_rotation": [0.0, 0.0, 0.0, 1.0],
                "scale": [1.0, 1.0, 1.0],
                "translation": [0.5, 0.0, 0.5],
            },
        })
        self.assertEqual(definition["fallback"], _vanilla_netherite_armor_trim_select("helmet"))

    def test_worn_final_blow_helm_sits_like_a_worn_vanilla_dragon_head(self):
        # A worn vanilla dragon head goes CustomHeadLayer's skull branch: the skull model drawn in
        # the head bone's frame scaled by 1.1875, no offset (Transforms.DEFAULT skullYOffset 0),
        # snout to -z, where the player looks. The helmet goes the item branch instead; vanilla's
        # item transformation there came out turned 180 degrees and 0.625/1.1875 the size (Joe on
        # live, 2026-10-10: "the dragon head is backwards"), so the head context carries its own.
        definition = self.json("assets/minecraft/items/netherite_helmet.json")["model"]
        select = next(c for c in definition["cases"] if c["when"] == "dragon_head")["model"]
        head = next(c for c in select["cases"] if c["when"] == "head")["model"]
        self.assertEqual(
            {k: head[k] for k in ("type", "base", "model")},
            {"type": "minecraft:special", "base": "minecraft:item/dragon_head",
             "model": {"type": "minecraft:head", "kind": "dragon"}},
        )
        local = _special_transformation(head["transformation"])
        # The skull model's snout tip (-z), crown (-y), side (+x) and origin, in blocks.
        for p in ([0, 0, 0], [0, 0, -1.5], [0, -1, 0], [0.5, 0, 0]):
            got = _worn_head_item(p, local)
            for a, b in zip(got, [1.1875 * v for v in p]):
                self.assertAlmostEqual(a, b, places=5, msg=p)

    def test_dragonscale_textures_are_tinted_differently_from_plain_netherite(self):
        # Pixel check that the look actually reads as different from vanilla netherite: Dragonscale
        # carries a violet/magenta tint, vanilla netherite is a near-neutral dark grey.
        def avg_rgb(png_bytes):
            img = Image.open(io.BytesIO(png_bytes)).convert("RGBA")
            px = img.load()
            pixels = [px[x, y][:3] for x in range(img.width) for y in range(img.height) if px[x, y][3] > 0]
            return tuple(sum(c[i] for c in pixels) / len(pixels) for i in range(3))

        r, g, b = avg_rgb(self.entries["assets/claudesmp/textures/item/dragonscale_chestplate.png"])
        self.assertGreater(r, g, "Dragonscale should read warmer/violet, not neutral grey")
        self.assertGreater(b, g)

    def test_the_salamanca_pistol_resolves_to_its_texture(self):
        self.assert_look_resolves("crossbow", "cartel_pistol")

    def test_bows_are_left_to_vanilla(self):
        self.assertNotIn("assets/minecraft/items/bow.json", self.entries)

    def test_dragon_radar_idle_resolves_to_its_texture(self):
        self.assert_look_resolves("compass", "dragon_radar_idle")

    def test_dragon_radar_has_32_frames_with_a_texture_each(self):
        compass = self.json("assets/minecraft/items/compass.json")["model"]
        for when in ("dragon_radar", "dragon_radar_nether", "dragon_radar_end"):
            radar = next(c for c in compass["cases"] if c["when"] == when)["model"]
            self.assertEqual(radar["property"], "minecraft:compass")
            self.assertEqual(radar["target"], "spawn")
            self.assertEqual(radar["scale"], 32.0)
            refs = {e["model"]["model"] for e in radar["entries"]}
            self.assertEqual(len(refs), 32, when)
            for ref in refs:
                model = self.json(asset(ref, "models", "json"))
                self.assertIn(asset(model["textures"]["layer0"], "textures", "png"), self.entries, ref)

    def test_every_radar_distance_band_has_32_frames_with_one_square_dot(self):
        # The plugin picks a band by appending RadarBand's suffix to the set's model string.
        band_java = (ROOT / "dragonballs/src/main/java/dev/sirbepy/mc/dragonballs/radar/RadarBand.java").read_text()
        suffixes = re.findall(r'^\s+[A-Z]+\("(_?[a-z]*)", "', band_java, re.MULTILINE)
        self.assertEqual(suffixes, ["_far", "", "_close", "_here"])
        compass = {c["when"]: c["model"] for c in self.json("assets/minecraft/items/compass.json")["model"]["cases"]}
        for base in ("dragon_radar", "dragon_radar_nether", "dragon_radar_end"):
            dot_distance = []
            for suffix in suffixes:
                radar = compass[base + suffix]
                self.assertEqual(radar["property"], "minecraft:compass", base + suffix)
                refs = [e["model"]["model"] for e in radar["entries"]]
                self.assertEqual(len(set(refs)), 32, base + suffix)
                for ref in refs:
                    model = self.json(asset(ref, "models", "json"))
                    self.assertIn(asset(model["textures"]["layer0"], "textures", "png"), self.entries, ref)
                # Frame 16 (the first entry) has its dot straight up: measure how far out it sits.
                png = Image.open(io.BytesIO(self.entries[asset(refs[0], "textures", "png")])).convert("RGBA")
                dot = [(x, y) for y in range(32) for x in range(32) if png.getpixel((x, y)) == (255, 150, 30, 255)]
                self.assertEqual(len(dot), 4, f"{base + suffix}: one 2x2 dot, nothing else orange")
                dot_distance.append(15.5 - sum(y for _x, y in dot) / 4)
            self.assertEqual(dot_distance, sorted(dot_distance, reverse=True), f"{base}: far sits furthest out")

    def test_nether_and_end_radars_have_their_own_idle_look(self):
        self.assert_look_resolves("compass", "dragon_radar_nether_idle")
        self.assert_look_resolves("compass", "dragon_radar_end_idle")

    def test_nether_radar_casing_is_red_and_end_radar_casing_is_purple(self):
        def casing(name):
            png = Image.open(io.BytesIO(self.entries[f"assets/claudesmp/textures/item/{name}.png"]))
            return png.convert("RGBA").load()[12, 1]
        r, g, b, _a = casing("dragon_radar_nether_idle")
        self.assertGreater(r, 2 * g)
        r, g, b, _a = casing("dragon_radar_end_idle")
        self.assertGreater(b, g)
        self.assertGreater(r, g)

    def test_other_compasses_stay_vanilla(self):
        compass = self.json("assets/minecraft/items/compass.json")["model"]
        self.assertEqual(compass["fallback"], VANILLA_COMPASS)

    def test_a_plain_phantom_membrane_keeps_its_vanilla_texture(self):
        # The More Foods mod jar replaces the minecraft:item/phantom_membrane model itself with the
        # Herbs texture, so the item definition must route a plain membrane around that model id.
        definition = self.json("assets/minecraft/items/phantom_membrane.json")["model"]
        self.assertEqual(definition["component"], "minecraft:custom_data")
        plain = definition["on_false"]["model"]
        self.assertNotEqual(plain, "minecraft:item/phantom_membrane")
        self.assertEqual(self.json(asset(plain, "models", "json"))["textures"]["layer0"], "minecraft:item/phantom_membrane")

    def test_every_dragon_ball_look_resolves_to_a_3d_model(self):
        definition = self.json("assets/minecraft/items/player_head.json")["model"]
        self.assertEqual(definition["property"], "minecraft:custom_model_data")
        looks = {c["when"]: c["model"]["model"] for c in definition["cases"]}
        wanted = {f"dragonball_{s}_{n}" for s in ("overworld", "nether", "end") for n in range(1, 8)}
        wanted.add("dragonball_stone")
        self.assertEqual(set(looks), wanted)
        for ref in looks.values():
            model = self.json(asset(ref, "models", "json"))
            # Same 8x8x8 box and display transforms as a vanilla head, so it sits where one would.
            self.assertEqual(model["parent"], "minecraft:item/template_skull")
            self.assertGreater(len(model["elements"]), 20)
            for el in model["elements"]:
                for lo, hi in zip(el["from"], el["to"]):
                    self.assertTrue(0 <= lo < hi <= 12, el)
            png = Image.open(io.BytesIO(self.entries[asset(model["textures"]["ball"], "textures", "png")]))
            self.assertEqual(png.size, (64, 64))

    def test_other_player_heads_stay_vanilla(self):
        fallback = self.json("assets/minecraft/items/player_head.json")["model"]["fallback"]
        # The 26.3 client jar's own assets/minecraft/items/player_head.json model.
        self.assertEqual(fallback, {
            "type": "minecraft:special",
            "base": "minecraft:item/template_skull",
            "model": {"type": "minecraft:player_head"},
            "transformation": {
                "left_rotation": [1.0, 0.0, 0.0, -0.0],
                "right_rotation": [0.0, 0.0, 0.0, 1.0],
                "scale": [1.0, 1.0, 1.0],
                "translation": [0.5, 0.0, 0.5],
            },
        })

    def test_the_vanilla_ender_dragon_keeps_its_own_look(self):
        # The summon dragons are item-display models now (Joe, 2026-10-08: "the actual normal ender
        # dragon isn't affected by the skin"), so nothing may retexture the Ender Dragon.
        retextured = [n for n in self.entries if n.startswith("assets/minecraft/textures/entity/enderdragon/")]
        self.assertEqual(retextured, [])

    def test_every_dragon_part_resolves_and_takes_the_set_tints(self):
        parts = sorted(n for n in self.entries if n.startswith("assets/claudesmp/items/dragon/"))
        names = [n.rsplit("/", 1)[1][:-len(".json")] for n in parts]
        self.assertEqual(names, [
            "porunga_arm_left", "porunga_arm_right", "porunga_head", "porunga_tail", "porunga_tail_tip",
            "porunga_torso", "shenron_body", "shenron_body_legs", "shenron_head", "shenron_tail",
        ])
        accent = []
        for name in parts:
            model = self.json(name)["model"]
            self.assertEqual([(t["type"], t["index"]) for t in model["tints"]],
                             [("minecraft:custom_model_data", 0), ("minecraft:custom_model_data", 1)], name)
            body = self.json(asset(model["model"], "models", "json"))
            tints = set()
            for el in body["elements"]:
                for v in el["from"] + el["to"]:
                    self.assertTrue(-16 <= v <= 32, f"{name}: {el['from']}..{el['to']} leaves the model range")
                tints |= {f.get("tintindex") for f in el["faces"].values()}
            self.assertIn(0, tints, f"{name} should take the set's body colour")
            if 1 in tints:
                accent.append(name)
            png = Image.open(io.BytesIO(self.entries[asset(body["textures"]["t"], "textures", "png")]))
            self.assertEqual(png.size, (64, 64))
        self.assertTrue(any("shenron" in n for n in accent) and any("porunga" in n for n in accent),
                        "both dragons should show the set's belly/accent colour somewhere")

    def test_dragon_eyes_glow(self):
        for name in ("shenron_head", "porunga_head"):
            body = self.json(f"assets/claudesmp/models/item/dragon/{name}.json")
            self.assertTrue(any(el.get("light_emission") == 15 for el in body["elements"]), name)

    def test_haru_skin_and_cape_are_in_the_pack(self):
        # The haru plugin's config.yml points its Mannequin at claudesmp:entity/haru and haru_cape.
        skin = Image.open(io.BytesIO(self.entries[asset("claudesmp:entity/haru", "textures", "png")]))
        self.assertEqual(skin.size, (64, 64))
        cape = Image.open(io.BytesIO(self.entries[asset("claudesmp:entity/haru_cape", "textures", "png")]))
        self.assertEqual(cape.size, (64, 32))

    def test_guild_staff_skins_are_in_the_pack(self):
        # The guild plugin's Staff enum points each Mannequin at claudesmp:entity/guild/<role>.
        for role in ("receptionist", "clerk", "treasurer", "examiner", "archivist"):
            skin = Image.open(io.BytesIO(self.entries[asset(f"claudesmp:entity/guild/{role}", "textures", "png")]))
            self.assertEqual(skin.size, (64, 64), role)
            # The base head's front face must be fully opaque, or the client draws holes in the face.
            face = skin.convert("RGBA").crop((8, 8, 16, 16))
            self.assertEqual(face.getextrema()[3], (255, 255), role)
        cape = Image.open(io.BytesIO(self.entries[asset("claudesmp:entity/guild/examiner_cape", "textures", "png")]))
        self.assertEqual(cape.size, (64, 32))

    def test_chronicler_skin_is_in_the_pack(self):
        # The minibosses plugin's Thessa NPC points its Mannequin at claudesmp:entity/chronicler.
        skin = Image.open(io.BytesIO(self.entries[asset("claudesmp:entity/chronicler", "textures", "png")]))
        self.assertEqual(skin.size, (64, 64))

    def test_dragonkind_evolved_names_its_dragons(self):
        # The datapack names each dragon with translation keys; without these the boss bar shows them raw.
        lang = self.json("assets/minecraft/lang/en_us.json")
        for key in ("dragon.dke.difficulty.ferocious", "dragon.dke.difficulty.ascended", "dragon.dke.type.zombie"):
            self.assertIn(key, lang)

    def test_dragonkind_evolved_items_resolve_to_their_models(self):
        items = [n for n in self.entries if n.startswith("assets/dke/items/") and n.endswith(".json")]
        self.assertTrue(items)
        for name in items:
            refs = re.findall(r'"model":\s*"([^"]+)"', self.entries[name].decode("utf-8"))
            self.assertTrue(refs, name)
            for ref in refs:
                self.assertIn(asset(ref, "models", "json"), self.entries, f"{name} -> {ref}")


if __name__ == "__main__":
    unittest.main()
