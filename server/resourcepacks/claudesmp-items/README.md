# Claude SMP custom item looks

Third source of the join resource pack built by `../build_server_pack.py`. Cosmetic only: each look
is picked by a `custom_model_data` string on an otherwise vanilla item, so stats, enchanting and
repair stay vanilla, and players without the pack (or on Bedrock via Geyser) see the normal item.

`assets/minecraft/items/crossbow.json` replaces the vanilla crossbow definition. Its `fallback` is a
verbatim copy of the 26.3 client jar's own `assets/minecraft/items/crossbow.json` model, so every
other crossbow keeps its loading and charged looks. `assets/minecraft/items/netherite_sword.json`
does the same for netherite swords, and `assets/minecraft/items/compass.json` does the same for
compasses (Dragon Radar, below), and `assets/minecraft/items/clock.json` for clocks (cartel
phone, below).

## Looks

- `pollos_gun` (crossbow): "Los Pollos Hermanos Certified", a gold pistol drawn side-on, barrel to
  the right. One-off for Archontopouloi, approved by Joe on 2026-10-05 with vanilla crossbow stats.
  It has four looks, picked by the crossbow's state: `pollos_gun_unloaded` (not loaded and not being
  drawn), `pollos_gun` (while drawing) and `pollos_gun_arrow` / `pollos_gun_rocket` (loaded, an
  arrowhead or a firework tip poking out of the barrel; both inherit `pollos_gun_charged`'s display).
  Unloaded it is the first version exactly: the original diagonal gold pistol texture (restored from
  commit c560eea) on the vanilla crossbow model, so it lies flat at the hip (Archontopouloi asked for
  the old texture there, Joe approved, 2026-10-06). While drawing, the side-on art also lies flat:
  its display is the vanilla crossbow display composed with the in-plane turn that lays the side-on
  art where the diagonal texture sits (2026-10-05). Loaded, the arm is in 26.3's `CROSSBOW_HOLD` pose (and first
  person adds its own crossbow-only offsets), so `pollos_gun_charged`'s display is solved from the
  26.3 hand transform chain to hold the gun upright with the barrel forward and the grip in the fist.
  Given from the console (Essentials owns `/give`):

  ```
  minecraft:give Archontopouloi crossbow[custom_name={text:"Los Pollos Hermanos Certified",color:"gold",italic:false},custom_model_data={strings:["pollos_gun"]}]
  ```

  It fires with a gunshot, `claudesmp:pollos_gun.shot` in `assets/claudesmp/sounds.json`, played by
  the Tweaks plugin (`PollosGunListener`) at volume 3 so it carries 48 blocks; the plugin also cuts
  the vanilla crossbow twang (Paper 26.3 sends the twang before `EntityShootBowEvent`, so the stop
  goes out in the same tick, right behind it). Approved by Joe on 2026-10-05. `sounds/pollos_gun/shot.ogg`
  is the shot at 3.67 s in "Unkown.wav" (sic) from "Gunshots" by kurt,
  https://opengameart.org/content/gunshots, licensed CC0
  (http://creativecommons.org/publicdomain/zero/1.0/): left channel, cut to 3.67-4.50 s, mono
  48 kHz, raised 12 dB into a limiter at 0.8 and faded out over its last 0.43 s. It replaced a 0.35 s
  .22 pistol pop that the twang drowned out (Archontopouloi heard only a crossbow, 2026-10-06); this
  one is 0.83 s and averages -16.6 dB over its first 0.3 s, against -17.9 dB for vanilla's
  `crossbow/shoot1.ogg` and -27.5 dB for the old pop, peaking at -1 dB:

  ```
  ffmpeg -i Unkown.wav -af "atrim=start=3.67:end=4.50,asetpts=PTS-STARTPTS,pan=mono|c0=c0,aresample=48000,volume=12dB,alimiter=limit=0.8:attack=1:release=60:level=false,afade=t=out:st=0.40:d=0.43" -c:a libvorbis -q:a 6 shot.ogg
  ```

- `katana` (netherite sword): "Katana", a thin steel blade with a gold guard and red-black wrapped
  hilt. One-off for ImMijo's Japanese-style base, approved by Joe on 2026-10-05. Applied to ImMijo's
  own enchanted netherite sword instead of giving a new one, so it adds no gear to the economy. The
  model inherits `minecraft:item/handheld` like every vanilla sword. The texture is 32x32 so the
  blade could run longer (ImMijo asked, Joe approved, 2026-10-05) while the hilt keeps its first
  16x16 pixels: handheld's in-hand scales are doubled so a pixel stays the size it was, the
  translations keep the grip in the fist, and `gui`/`fixed` scale the sword's 22 px bounding box up
  to fill the slot. Applied from the console
  (`hotbar.3` was the sword's slot, Slot 3b in `data get entity ImMijo Inventory`). It needs the
  `minecraft:` prefix because Essentials owns `/item`, and 26.3 inline modifiers take `type`, not
  `function`:

  ```
  minecraft:item modify entity ImMijo hotbar.3 {type:"minecraft:set_components",components:{"minecraft:custom_name":{text:"Katana",italic:false},"minecraft:custom_model_data":{strings:["katana"]}}}
  ```

- `dragon_radar` / `dragon_radar_idle` (compass): the Dragon Balls feature's Dragon Radar, given by the
  plugin as a compass with `custom_model_data` strings `["dragon_radar"]` while a ball is awake, or
  `["dragon_radar_idle"]` while none are. Approved by Joe on 2026-10-06. The needle direction comes
  from vanilla compass-target semantics (the plugin points the player's compass `target` at the
  ball its holder tracks the way a lodestone compass points at its lodestone); distance shows only
  as one of 4 bands (see "distance bands" below). `assets/minecraft/items/compass.json`'s `fallback` is a verbatim copy of the 26.3
  jar's own compass model, so every other compass (including lodestone compasses) is unaffected.
  `dragon_radar`'s 32 frames reuse vanilla's own `minecraft:compass` `range_dispatch` structure
  (`scale: 32.0`, `target: "spawn"`, the same 33 thresholds vanilla uses for its own needle, which
  double up frame 16 at both ends to close the loop) against 32 new `claudesmp:item/dragon_radar_NN`
  models instead of vanilla's `compass_NN`. The frame numbering matches vanilla's own: vanilla's
  `compass_00.png..compass_31.png` were sampled this session and rotate clockwise, needle near straight
  down at frame 0 and straight up at frame 16, so frame NN's blip sits at `(180 + NN*11.25) mod 360`
  degrees clockwise from up, same as vanilla's needle at that frame number. The art is a round
  handheld unit (light grey casing, a push button on top, a dark-green CRT-style screen with a
  cross + two concentric rings) with a fixed-radius orange/yellow blip rotating to the target
  direction; the blip blinks via a `.png.mcmeta` animation (2 stacked frames, 10 ticks each) baked
  into every `dragon_radar_NN.png`, independent of the frame-select mechanism. `dragon_radar_idle` is
  the same device with the screen dimmed and a little static, no blip. Textures are 32x32 (not
  vanilla's 16x16) for the extra screen/grid detail, rendered the same flat way `item/generated`
  always has, no transform changes needed (same precedent as `katana`'s 32x32 texture). Generated by
  the scratch script at `.for_bepy/dragonballs-art/radar.py` (not committed).

- `dragon_radar_nether(_idle)` / `dragon_radar_end(_idle)` (compass): the Nether and End sets' radars
  (one radar per Dragon Ball set, Joe, 2026-10-07), wired exactly like `dragon_radar` above, with their
  own 33 textures `claudesmp:item/dragon_radar_nether_NN` / `dragon_radar_end_NN` (+ `_idle`). Each is
  the Overworld radar's frame with only the 3 casing shades swapped: red for the Nether (196,60,48 /
  150,38,32 / 96,22,22), purple for the End (176,120,214 / 132,82,170 / 86,48,116); screen, blip and
  blink animation are unchanged.

- Radar distance bands (compass, Joe, 2026-10-08): every set's radar has 4 screens, one per distance
  band the plugin's `RadarBand` picks: `<set>_far`, `<set>` (Getting warm, the original model string,
  so a radar from before the bands still renders), `<set>_close` and `<set>_here`, each with its own
  32 frames `claudesmp:item/<set>[_band]_NN` wired exactly like `dragon_radar` above. The blip is one
  2x2 orange dot (Joe: "only 1 dot, it doesnt have to be circle"), 10 / 6.6 / 3.3 / 1.6 pixels out
  from the screen's centre, between the grid rings; the "here" dot blinks every 4 ticks instead of
  10. Generated (with the radar cases of `compass.json`) by `../radar_bands.py`, which paints the dot
  onto each set's own blank screen, so re-running it is safe; this replaced the round blip of
  `radar.py`'s original frames.

- Summon dragons (item models, Joe, 2026-10-08): `assets/claudesmp/items/dragon/*.json` and their
  models in `models/item/dragon/`, one per part: Shenron is a head, a body segment (plain and with
  clawed legs) and a tail tip; Porunga is a torso (pale chest, black shoulder spikes, back crest), a
  head, two arms, a tail segment and a tail tip, after the anime's serpent-tailed Porunga (Joe's
  screenshot). The Dragon Balls plugin spawns one item display per part (`DragonModel.java`, posed by
  `DragonPose.java`) with `minecraft:item_model` set to the part, so no vanilla item changes look.
  Every definition tints from the item's `custom_model_data` colours, index 0 the body and index 1
  the belly/accent, which is how one model serves every set: green in the Overworld, Nuova Shenron
  red and Porunga purple in the Nether, gold in the End. Eyes, horns, spikes, teeth and claws are
  untinted; eyes glow (`light_emission` 15). All parts share the 64x64
  `textures/item/dragon/parts.png` (greyscale where tinted). The models are drawn 1.5x smaller than
  their rigs to fit the item model's -16..32 range, and the plugin scales them back up. Generated by
  `../dragon_models.py` (run it after any edit; it refuses an element outside the model range). The
  vanilla Ender Dragon is no longer retextured: Bedrock players, who can't see item displays, get a
  plain Ender Dragon in the summon dragon's place.

- Dragon Balls (player_head, Joe, 2026-10-08): `assets/minecraft/items/player_head.json` selects on
  `custom_model_data` `dragonball_<overworld|nether|end>_<1-7>` and `dragonball_stone` (stamped on
  every ball item by the Dragon Balls plugin) and draws a rounded 3D ball instead of a head; every
  other head falls back to the 26.3 jar's own player_head definition verbatim, and so does a ball
  for a client without the pack (its 8x8 skin). Each model is a 16-voxel sphere merged into about
  100 boxes, filling exactly the vanilla head's 8x8x8 box (x/z 4-12, y 0-8 in model units) with
  `item/template_skull` as parent, so it sits where a head would in every display context; the
  floating placed ball is an item display in the `fixed` context scaled x1.5 (0.75 blocks). Each
  box face samples one of six 16x16 direction tiles in a 64x64 texture, using the 26.3 jar's own
  auto-UV layout per face (`FaceBakery#defaultFaceUV`, javap-checked), so a voxel's colour lands on
  whichever of its faces shows. Shading is baked top-lit so a spinning ball doesn't drag a
  highlight around, and the stars (the live 8x8 skins' own layouts) sit on the north-west side the
  inventory angle shows. Styles Joe picked from mockups: Overworld "Classic" (orange glass, red
  stars), Nether "Crimson glass" (crimson gem, dark stars), End "Void amethyst" (purple glass, dark
  stars); stone is plain grey. Generated by the scratch script
  `.for_bepy/dragonball-skins/make_pack_balls.py` (not committed).

- `cartel_phone` (clock): the "Cartel Flip Phone" of the Cartel plugin, Archontopouloi's Shenron
  wish, approved by Joe on 2026-10-06 (`docs/cartel.md`). A 32x32 black flip phone, open, with a
  green LCD, silver hinge and keypad, on `item/generated`. `clock.json`'s `fallback` is the 26.3
  jar's own clock model verbatim.

- `cartel_pistol` (crossbow): Leonel and Marco Salamanca's pistol, the enchanted crossbow the Cartel
  plugin gives both cousins in both modes (Joe, 2026-10-07, `docs/cartel.md`; a bow until then). The
  plugin fires their bolts itself, so the crossbow is never loaded or drawn and one look covers it: a
  zombie's arm pose ignores what it holds (26.3's `HumanoidMobRenderer.getArmPose` only knows spears),
  so the display solved for the bow needed no change. The texture is `pollos_gun.png`'s side-on
  pistol recoloured to black gunmetal with a wooden grip, so it can't be mistaken for Archontopouloi's
  gold gun. Only zombies hold it,
  so only the third-person display is set: solved with `.for_bepy/item-display-solver/pose.py` for
  26.3's aggressive zombie arm (`AnimationUtils.animateZombieArms`: xRot -pi/1.5, yRot -0.1), barrel
  along the raised arm and grip in the fist. Left hand repeats the right-hand numbers (the client
  mirrors them), for the few left-handed zombies. Not yet seen in a real 26.3 client, only in the
  solver's offline render.

- `claudesmp:salamanca` (equipment asset, `assets/claudesmp/equipment/salamanca.json`): the look of
  Leonel and Marco Salamanca, the cartel phone's hitmen. The plugin spawns them as zombies wearing a
  chainmail chestplate and leggings plus a paper item on the head and feet, all four with an
  `equippable` component whose asset id is `claudesmp:salamanca`, so only the chainmail counts as
  armor but every piece draws `textures/entity/equipment/humanoid/salamanca.png` (helmet: bald head,
  wraparound sunglasses, goatee; chestplate: grey suit over a black shirt, hands at the sleeve
  ends; boots: dark boots with silver toes) and `humanoid_leggings/salamanca.png` (grey trousers,
  belt). Every face of the zombie is covered, so none of its green skin shows. Format checked
  against the 26.3 jar's `assets/minecraft/equipment/iron.json` (same `humanoid` /
  `humanoid_leggings` layer keys). Without the pack the asset is missing and the client draws no
  armor at all (the 26.3 jar's `EquipmentAssetManager.get` falls back to `MISSING`, which has no
layers), so those players see plain zombies.

Both are generated by the scratch script `.for_bepy/cartel-art/cartel.py` (not committed).

## Haru's skin

- `textures/entity/haru.png` (64x64, wide arms) and `textures/entity/haru_cape.png` (64x32): the Java-side
  look of Haru, ImMijo's Shenron-wish NPC (`haru` plugin). Haru is a 26.x Mannequin whose profile skin
  patch names `claudesmp:entity/haru` and `claudesmp:entity/haru_cape` (`skin.pack-texture` and
  `skin.pack-cape` in the plugin's config.yml); the client resolves those to `textures/<id>.png`, read
  from the 26.3 jar's `ClientAsset$ResourceTexture`. An elderly shrine keeper: white hair with a
  topknot and blossom hairpin, white beard, pink haori with petal dots over a white kimono, brown obi
  with a gold charm, plum hakama, tabi and geta; the cape fades pink with a cherry branch and petals.
  Approved by Joe on 2026-10-06. The plugin hides the Mannequin from players whose pack declined or failed;
  everyone else (and Bedrock) sees a cleric villager. Drawn by the scratch script
  `.for_bepy/haru-skin/make_skin.py` (not committed).

## The Guild staff's skins

- `textures/entity/guild/<role>.png` (64x64) for `receptionist`, `clerk`, `treasurer`, `examiner` and
  `archivist`, plus `textures/entity/guild/examiner_cape.png` (64x32): the Java-side look of the
  Adventurer's Guild staff (`guild` plugin, `Staff.java`), Mannequins whose skin patch names
  `claudesmp:entity/guild/<role>` the same way Haru's does. Lyra (receptionist) and Maren (examiner)
  use the slim 3-pixel arm layout. Players whose pack declined or failed, and Bedrock players, see each
  one's villager instead. Drawn by `server/resourcepacks/guild_skins.py`, which also renders 3D
  previews (`python guild_skins.py <out dir>`).

## Phantom sounds

`assets/minecraft/sounds.json` replaces the six `entity.phantom.*` events with vanilla 26.3's own
entries (same files, volumes and subtitles, copied from the 26.3 asset index's `minecraft/sounds.json`)
plus `"attenuation_distance": 32`, so phantoms are heard from 32 blocks instead of 16. Part of
CoffiebagSupreme's Shenron wish (2026-10-07, "twice as loud"). Wing flaps are played by the client
itself, so the pack alone covers them; the server only sends a volume-1 sound to players within 16
blocks, so the Tweaks plugin's `PhantomBuffListener` replays the screech for players 16-32 blocks out.
Without the pack (or on Bedrock) phantoms sound vanilla.

## Dragonscale armor

"Dragonscale": the Ender Dragon kill reward (Joe, 2026-10-09). Real netherite helmet, chestplate,
leggings and boots with no enchantments, given by the endfight plugin, built elsewhere; this pack
only owns the look. The worn look is `claudesmp:dragonscale`
(`assets/claudesmp/equipment/dragonscale.json`, `humanoid` and `humanoid_leggings` layers both
pointing at `textures/entity/equipment/{humanoid,humanoid_leggings}/dragonscale.png`, same 64x32
size as vanilla's own netherite layers), set by the item's own `equippable.asset_id`. The inventory
icon is picked the same way every other look in this pack is: `custom_model_data` string
`"dragonscale"` on an otherwise vanilla item. `assets/minecraft/items/netherite_{helmet,chestplate,
leggings,boots}.json` each add a `dragonscale` case ahead of a `fallback` that is a verbatim copy of
the 26.3 client jar's own 11-way trim-material select for that piece, so any other netherite piece
(trimmed or not) still renders exactly as vanilla does. Art is near-black overlapping scales with
violet-purple edge highlights, two magenta "ender eye" glints (helmet brow, chest centre) and a
ridged crest down the helmet's dome plus a spiked pauldron on the right shoulder (mirrored
client-side onto the left, the same way vanilla's own armor layers only paint one arm). Generated by
`dragonscale_armor.py`, which paints inside the exact alpha silhouette of vanilla's own netherite
equipment layers and item icons (dumped from the 26.3 client jar), so every piece keeps vanilla's
shape and fit. The shoulder pauldron is deliberately bold: a solid bone-violet (`RIM_B`, the same
colour as the 3D helm's horns below) top plate with three spike points tapering down the arm's
east/front/west sleeve faces, since a flat armor layer can't give real 3D spikes there (Joe asked for
shoulder horns/spikes 2026-10-09; `HumanoidArmorLayer` only draws flat quads on these faces, and a
head-item model would turn with the head, not the arm).

**The helmet also has a real 3D worn look**, the "Dragon Skull helm" (Joe picked it over a "horned
helm" draft, both previewed offline first in `.for_bepy/dragonscale-3d/`, never committed): a shell
hugging the head with an open face, a forward snout/visor with nostrils and teeth, a brow ridge, two
back-swept horns and a row of back spikes. `assets/minecraft/items/netherite_helmet.json`'s
`dragonscale` case is a `minecraft:select` on `minecraft:display_context`: `"head"` resolves to
`assets/claudesmp/models/item/dragonscale_helmet_worn.json` (free-rotation cuboids, its own 32x32
texture), every other context (inventory, ground, GUI, hand) falls back to the same flat 2D icon the
other three pieces use. Generated by `dragonscale_helmet_model.py`, whose own module docstring
derives (and `javap` cites) the exact `CustomHeadLayer` + `ItemTransform` chain a 26.3 client applies
when rendering a head-slot item, so the model's element coordinates land the shell on the actual head
exactly where the offline preview put it; `.for_bepy/dragonscale-3d/check_ingame_placement.py`
re-simulates that same chain against the real written JSON as a check (0.000000-block error across
all 27 elements, 2026-10-09).

```
python server/resourcepacks/dragonscale_helmet_model.py     # writes the 3D worn model + its texture
```

The same equipment asset also carries a `wings` layer (`textures/entity/equipment/wings/
dragonscale.png`, same 64x32 size and UV layout as vanilla's `textures/entity/equipment/wings/
elytra.png`): a dark-plum-to-near-black membrane, violet bone/finger lines, a scalloped darker
trailing edge and a small claw at the shoulder joint, painted inside vanilla elytra's own alpha
silhouette the same way the armor layers are. `WingsLayer` (26.3 client jar, javap-verified
2026-10-09) only ever reads `HumanoidRenderState.chestEquipment`, so this layer renders exclusively
while Dragonscale is worn as the chestplate; wearing the helmet, leggings or boots alone (they share
this same asset file) can never trigger it:

```
python server/resourcepacks/dragonscale_armor.py            # writes the pack textures
python server/resourcepacks/dragonscale_armor.py <out dir>  # also writes previews there
```

Console test (verified against the 26.3 client jar's own item component names and the netherite
armor item models above; `minecraft:` is needed because Essentials owns `/give`):

```
minecraft:give <player> netherite_chestplate[custom_name={text:"Dragonscale Chestplate",color:"dark_purple",italic:false},custom_model_data={strings:["dragonscale"]},equippable={slot:"chest",asset_id:"claudesmp:dragonscale"}]
```

Caveats, stated honestly:

- **Not testable in a real 26.3 client.** This session has no client to log in with (see
  `porting-packs-to-26-3.md`'s bot-testing dead end); the look was only checked by resolving the
  built pack's JSON/PNG chain in tests and by eye in upscaled previews, never rendered on an actual
  player model in-game. The 3D worn helmet's exact head placement is `javap`-derived and
  numerically self-consistent (see above) but, like the rest of this section, has never been
  confirmed against a real client either.
- **A pack-less Java client can't happen**: `server/config/server.properties` sets
  `require-resource-pack=true`, so every Java player has `claudesmp:dragonscale` loaded before they
  can see a Dragonscale wearer at all.
- **Bedrock players see plain vanilla netherite on a Dragonscale wearer, never the wings or a head
  model**, not the `MISSING` look a pack-less Java client would get: Geyser never reads
  `equippable.asset_id` in the first place (its `JavaEquippable` model only looks at `slot`, and
  `LivingEntity.updateArmor` translates each worn armor item by item type), so it has no concept of
  our custom asset to resolve or fail to resolve. The inventory icon is unaffected either way: a
  pack-less or Bedrock client never sees our `custom_model_data` item override, so it just draws
  vanilla's own netherite icon (with trims, if any) as normal.
- **Trims are untested in combination with Dragonscale.** The equipment asset has no
  `trim_overrides` (same as `salamanca`), so a Dragonscale piece that also carried a
  `trim_material` component would still show the Dragonscale icon (the custom_model_data case is
  checked before the trim-select fallback) but the worn trim overlay's behaviour against our custom
  layer texture was not rendered or verified. The endfight plugin isn't expected to set a trim on
  this armor, so this should not come up in practice.

## Final-blow dragon helm

Whoever lands the Ender Dragon's final blow gets a netherite helmet that looks exactly like a dragon
head (Joe, 2026-10-09; given by the endfight plugin on every final blow). The
`custom_model_data` string `"dragon_head"` in `assets/minecraft/items/netherite_helmet.json` picks a
verbatim copy of the 26.3 client jar's own `assets/minecraft/items/dragon_head.json` model, so the
inventory icon is vanilla's dragon head and no new art is involved. Worn, the helmet must carry an
`equippable` with slot head and no `asset_id`: the client then draws the item model on the head
(the same path as the Dragonscale helm), using the dragon head's own `head` display. Stats,
enchanting, repairs and fire resistance stay netherite's; Bedrock sees a netherite helmet. Untested
in a real 26.3 client.

```
minecraft:give <player> netherite_helmet[custom_model_data={strings:["dragon_head"]},equippable={slot:"head"}]
```
