# Claude SMP custom item looks

Third source of the join resource pack built by `../build_server_pack.py`. Cosmetic only: each look
is picked by a `custom_model_data` string on an otherwise vanilla item, so stats, enchanting and
repair stay vanilla, and players without the pack (or on Bedrock via Geyser) see the normal item.

`assets/minecraft/items/crossbow.json` replaces the vanilla crossbow definition. Its `fallback` is a
verbatim copy of the 26.3 client jar's own `assets/minecraft/items/crossbow.json` model, so every
other crossbow keeps its loading and charged looks.

## Looks

- `pollos_gun` (crossbow): "Los Pollos Hermanos Certified", a gold pistol. One-off for
  Archontopouloi, approved by Joe on 2026-10-05 with vanilla crossbow stats. The barrel points where
  the bolt points in `crossbow_arrow.png` and the model inherits `minecraft:item/crossbow`'s display,
  so it aims like a crossbow. It has one frame, so it looks the same while loading and when charged.
  Given from the console:

  ```
  give Archontopouloi crossbow[custom_name={text:"Los Pollos Hermanos Certified",color:"gold",italic:false},custom_model_data={strings:["pollos_gun"]}]
  ```
