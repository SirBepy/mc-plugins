# Claude SMP custom item looks

Third source of the join resource pack built by `../build_server_pack.py`. Cosmetic only: each look
is picked by a `custom_model_data` string on an otherwise vanilla item, so stats, enchanting and
repair stay vanilla, and players without the pack (or on Bedrock via Geyser) see the normal item.

`assets/minecraft/items/crossbow.json` replaces the vanilla crossbow definition. Its `fallback` is a
verbatim copy of the 26.3 client jar's own `assets/minecraft/items/crossbow.json` model, so every
other crossbow keeps its loading and charged looks. `assets/minecraft/items/netherite_sword.json`
does the same for netherite swords, and `assets/minecraft/items/compass.json` does the same for
compasses (Dragon Radar, below).

## Looks

- `pollos_gun` (crossbow): "Los Pollos Hermanos Certified", a gold pistol drawn side-on, barrel to
  the right. One-off for Archontopouloi, approved by Joe on 2026-10-05 with vanilla crossbow stats.
  It has three looks, picked by the crossbow's state: `pollos_gun` (unloaded and while drawing) and
  `pollos_gun_arrow` / `pollos_gun_rocket` (loaded, an arrowhead or a firework tip poking out of the
  barrel; both inherit `pollos_gun_charged`'s display). Unloaded it lies flat at the hip like a
  vanilla crossbow: its display is the vanilla crossbow display composed with the in-plane turn that
  lays this side-on art where the first, diagonal Pollos texture sat (Archontopouloi asked for that
  pose back, Joe approved, 2026-10-05). Loaded, the arm is in 26.3's `CROSSBOW_HOLD` pose (and first
  person adds its own crossbow-only offsets), so `pollos_gun_charged`'s display is solved from the
  26.3 hand transform chain to hold the gun upright with the barrel forward and the grip in the fist.
  Given from the console (Essentials owns `/give`):

  ```
  minecraft:give Archontopouloi crossbow[custom_name={text:"Los Pollos Hermanos Certified",color:"gold",italic:false},custom_model_data={strings:["pollos_gun"]}]
  ```

  It fires with a gunshot, `claudesmp:pollos_gun.shot` in `assets/claudesmp/sounds.json`, played by
  the Tweaks plugin (`PollosGunListener`) at volume 3 so it carries 48 blocks; the plugin also cuts
  the vanilla crossbow twang a tick in. Approved by Joe on 2026-10-05. `sounds/pollos_gun/shot.ogg`
  is the fourth of the five shots in "22 Pistol.wav" from "Gunshots" by kurt,
  https://opengameart.org/content/gunshots, licensed CC0
  (http://creativecommons.org/publicdomain/zero/1.0/), cut to 1.37-1.72 s, mono 48 kHz, faded out
  over its last 0.13 s and raised 1.2 dB to peak at -1 dB:

  ```
  ffmpeg -i "22 Pistol.wav" -ac 1 -af "atrim=start=1.37:end=1.72,asetpts=PTS-STARTPTS,afade=t=out:st=0.22:d=0.13,aresample=48000,volume=1.2dB" -c:a libvorbis -q:a 6 shot.ogg
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
