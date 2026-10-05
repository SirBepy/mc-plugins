# Claude SMP custom item looks

Third source of the join resource pack built by `../build_server_pack.py`. Cosmetic only: each look
is picked by a `custom_model_data` string on an otherwise vanilla item, so stats, enchanting and
repair stay vanilla, and players without the pack (or on Bedrock via Geyser) see the normal item.

`assets/minecraft/items/crossbow.json` replaces the vanilla crossbow definition. Its `fallback` is a
verbatim copy of the 26.3 client jar's own `assets/minecraft/items/crossbow.json` model, so every
other crossbow keeps its loading and charged looks. `assets/minecraft/items/netherite_sword.json`
does the same for netherite swords.

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
