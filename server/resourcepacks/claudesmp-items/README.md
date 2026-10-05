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
  It has four looks, picked by the crossbow's state: `pollos_gun` (unloaded), `pollos_gun_loading`
  (while drawing), and `pollos_gun_arrow` / `pollos_gun_rocket` (loaded, an arrowhead or a firework
  tip poking out of the barrel; both inherit `pollos_gun_charged`'s display). Each state puts the
  arm in a different pose (26.3 `AvatarRenderer.getArmPose`: unloaded `ITEM`, drawing
  `CROSSBOW_CHARGE`, loaded `CROSSBOW_HOLD`, and first person adds its own crossbow-only offsets),
  so each has its own display, solved from the 26.3 hand transform chain so the gun stays upright
  with the barrel forward and the grip in the fist in third person, and sits in the same spot on
  screen in first person in every state. Given from the console (Essentials owns `/give`):

  ```
  minecraft:give Archontopouloi crossbow[custom_name={text:"Los Pollos Hermanos Certified",color:"gold",italic:false},custom_model_data={strings:["pollos_gun"]}]
  ```

- `katana` (netherite sword): "Katana", a thin steel blade with a gold guard and red-black wrapped
  hilt. One-off for ImMijo's Japanese-style base, approved by Joe on 2026-10-05. Applied to ImMijo's
  own enchanted netherite sword instead of giving a new one, so it adds no gear to the economy. The
  model inherits `minecraft:item/handheld` like every vanilla sword. Applied from the console
  (`hotbar.3` was the sword's slot, Slot 3b in `data get entity ImMijo Inventory`). It needs the
  `minecraft:` prefix because Essentials owns `/item`, and 26.3 inline modifiers take `type`, not
  `function`:

  ```
  minecraft:item modify entity ImMijo hotbar.3 {type:"minecraft:set_components",components:{"minecraft:custom_name":{text:"Katana",italic:false},"minecraft:custom_model_data":{strings:["katana"]}}}
  ```
