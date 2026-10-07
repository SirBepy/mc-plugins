# Claude SMP custom item looks

Third source of the join resource pack built by `../build_server_pack.py`. Cosmetic only: each look
is picked by a `custom_model_data` string on an otherwise vanilla item, so stats, enchanting and
repair stay vanilla, and players without the pack (or on Bedrock via Geyser) see the normal item.

`assets/minecraft/items/crossbow.json` replaces the vanilla crossbow definition. Its `fallback` is a
verbatim copy of the 26.3 client jar's own `assets/minecraft/items/crossbow.json` model, so every
other crossbow keeps its loading and charged looks. `assets/minecraft/items/netherite_sword.json`
does the same for netherite swords, and `assets/minecraft/items/compass.json` does the same for
compasses (Dragon Radar, below), `assets/minecraft/items/clock.json` for clocks (cartel
phone, below), and `assets/minecraft/items/bow.json` for bows (Marco's pistol, below).

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
  nearest awake ball the way a lodestone compass points at its lodestone); distance is never shown,
  only direction. `assets/minecraft/items/compass.json`'s `fallback` is a verbatim copy of the 26.3
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

- Shenron (Ender Dragon retexture): recolours vanilla's own `assets/minecraft/textures/entity/
  enderdragon/dragon.png` and `dragon_eyes.png` to Shenron's look for the Dragon Balls feature's
  wish-granting dragon, a harmless scaled-up Ender Dragon the plugin spawns. Approved by Joe on
  2026-10-06; this replaces **every** Ender Dragon's texture pack-wide (the End is currently closed,
  so there's no other dragon it could clash with -- accepted trade-off, Joe 2026-10-06). Body, neck,
  tail and head go a bright jade/emerald green: vanilla's own body luminance sits in a very narrow
  near-black band, so a straight luminance-to-value recolour stayed dark no matter the gain (Joe
  flagged this 2026-10-06); the fix percentile-stretches the body's own luminance range to 0..1 first,
  then maps it across an explicit dark/mid/light gradient (`#1F6B2A` scale lines, `#3FAE49` mid-tone,
  `#7FD86A` highlights) so the scale pattern stays visible but the body reads clearly bright. The
  belly (body's and each neck/tail segment's underside UV) gets the same stretch-and-gradient
  treatment over a narrower, paler warm yellow-green range centred on `#C8D86A`. The back spikes (the
  neck/tail spike reused down the whole spine, plus the three body spikes) go gold/orange. Eyes go
  bright glowing red: both `dragon_eyes.png` (the glow overlay) and the handful of non-gray pixels
  `dragon.png` itself bakes in at the same 4 clusters/colours (found by a saturation check, confirmed
  identical to `dragon_eyes.png`'s own opaque pixels) are re-hued red, keeping their original shading,
  so the base skin doesn't show vanilla's purple under the red overlay. The wings are fully
  transparent (alpha 0, both the membrane and the bone struts) so Shenron reads as a wingless serpent,
  per the source material. UV regions for every part were read from the 26.3 jar's own
  `net.minecraft.client.model.monster.dragon.EnderDragonModel#createBodyLayer` bytecode (`javap -c -p`
  on the cube's `addBox` calls, Mojang's standard box-UV unwrap), not guessed, and cross-checked
  against the actual `dragon.png` alpha channel to keep clear of the front leg cubes sharing the same
  texture quadrant. `dragon_exploding.png` (the death-animation dissolve mask `EnderDragonRenderer`
  blends with `dragon.png` via `RenderTypes.entityCutoutDissolve`) is left untouched on purpose: it's
  a noise mask, not a second skin, so the dissolve automatically shows Shenron's own colours
  dissolving. The render path (`EntityModel`'s default `RenderTypes.entityCutout(texture, true)`) is
  a cutout type, confirmed from the jar, so the wing alpha is discarded rather than blended/ghosted.
  Generated by the scratch script at `.for_bepy/dragonballs-art/shenron.py` (not committed).

- `cartel_phone` (clock): the "Cartel Flip Phone" of the Cartel plugin, Archontopouloi's Shenron
  wish, approved by Joe on 2026-10-06 (`docs/cartel.md`). A 32x32 black flip phone, open, with a
  green LCD, silver hinge and keypad, on `item/generated`. `clock.json`'s `fallback` is the 26.3
  jar's own clock model verbatim.

- `cartel_pistol` (bow): Marco Salamanca's pistol, the bow the Cartel plugin hands him in
  Assassinate Mode once a target has climbed out of melee reach (the dragon's council, 2026-10-07,
  `docs/cartel.md`). The texture is `pollos_gun.png`'s side-on pistol recoloured to black gunmetal
  with a wooden grip, so it can't be mistaken for Archontopouloi's gold gun. Only zombies hold it,
  so only the third-person display is set: solved with `.for_bepy/item-display-solver/pose.py` for
  26.3's aggressive zombie arm (`AnimationUtils.animateZombieArms`: xRot -pi/1.5, yRot -0.1), barrel
  along the raised arm and grip in the fist. Left hand repeats the right-hand numbers (the client
  mirrors them), for the few left-handed zombies. `bow.json`'s `fallback` is the 26.3 jar's own bow
  model verbatim. Not yet seen in a real 26.3 client, only in the solver's offline render.

- `claudesmp:salamanca` (equipment asset, `assets/claudesmp/equipment/salamanca.json`): the look of
  Leonel and Marco Salamanca, the cartel phone's hitmen. The plugin spawns them as zombies in iron
  armor whose `equippable` component's asset id is `claudesmp:salamanca`, so the armor keeps iron's
  stats but draws `textures/entity/equipment/humanoid/salamanca.png` (helmet: bald head,
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

## Phantom sounds

`assets/minecraft/sounds.json` replaces the six `entity.phantom.*` events with vanilla 26.3's own
entries (same files, volumes and subtitles, copied from the 26.3 asset index's `minecraft/sounds.json`)
plus `"attenuation_distance": 32`, so phantoms are heard from 32 blocks instead of 16. Part of
CoffiebagSupreme's Shenron wish (2026-10-07, "twice as loud"). Wing flaps are played by the client
itself, so the pack alone covers them; the server only sends a volume-1 sound to players within 16
blocks, so the Tweaks plugin's `PhantomBuffListener` replays the screech for players 16-32 blocks out.
Without the pack (or on Bedrock) phantoms sound vanilla.
