# rmf-26.3-fix

A small resource pack of SirBepy's own files that fixes carrier-item display bugs in
[Reg's More Foods](https://modrinth.com/datapack/reg-more-foods) (by regfunkid) on Minecraft 26.3.

## Why

The server's RMF datapack disguises some foods as vanilla "carrier" items (pottery sherds,
banner patterns, a rabbit foot, a beetroot/beetroot soup/beetroot seeds) using item components
(`custom_data` and, for most foods, `item_model`). The RMF mod jar (`regs-more-foods-*+mod.jar`,
pulled in as a pinned Modrinth download, see `client/mrpack/modrinth.index.json`) ships 26.3-style
`assets/minecraft/items/*.json` item definitions for only 4 of those carriers, and 2 of those
(`burn_pottery_sherd`, `skull_banner_pattern`) apply the RMF look unconditionally, so a REAL vanilla
pottery sherd or banner pattern a player finds/crafts also renders as salt/seasoning. The other
carriers either have no 26.3 item definition at all (so the RMF food just renders as the plain
vanilla item) or a definition that checks a component (`minecraft:custom_name`) the datapack never
actually sets (the datapack sets `minecraft:item_name` instead), so it never fires.

## What this pack does

For each affected carrier item, `assets/minecraft/items/<carrier>.json` gates on whether the item
has an RMF `minecraft:custom_data` component at all (real vanilla items never do):

- has `custom_data` -> show the RMF food's own look
- no `custom_data` -> show the real vanilla item's own look

This fixes 14 carriers: `burn_pottery_sherd` (salt), `skull_banner_pattern` (seasoning),
`rabbit_foot` (milk barrel), `flower_banner_pattern` (water barrel),
`bordure_indented_banner_pattern` (pasta), `field_masoned_banner_pattern` (rice),
`guster_banner_pattern` (vegetable oil), `brewer_pottery_sherd` (cheese),
`danger_pottery_sherd` (vinegar), `poisonous_potato` (tofu), `angler_pottery_sherd`
(cottage cheese), `arms_up_pottery_sherd` (lettuce), `beetroot` (tomato), `beetroot_seeds`
(tomato seeds).

Two carriers are left alone on purpose:

- `beetroot_soup` (tomato sauce): the RMF jar ships no texture or model for "tomato sauce" at all
  (only for the separate "tomato soup"/`tomato_soup`), so there is nothing to point at; it keeps
  rendering as plain vanilla beetroot soup, same as before this pack.
- `mushroom_stew`: RMF's mushroom stew food is cosmetically identical to vanilla mushroom stew
  (no distinguishing texture), so there's nothing to fix.

## What's in here, and what isn't

Every `.json` file in this pack is a file SirBepy wrote from scratch. None of them contain model
geometry, art, or copied text from the RMF mod. Where a carrier's RMF look already has a model the
jar wires up for other foods (e.g. `rmf:item/pasta`, `rmf:item/milk_barrel`), this pack's item
definition just points at that existing model id by name. Where the jar has an unused texture but
no model pointing at it (cheese, vinegar, tofu, cottage cheese, lettuce, tomato, tomato seeds),
`assets/rmf26fix/models/item/<food>.json` is a new, minimal "generated icon" model (the same two
lines every flat-icon item in the game uses) that references that existing texture by id
(`rmf:item/<food>`) - it does not embed or copy the texture itself. Real-vanilla-item fallback
models (`assets/rmf26fix/models/item/vanilla_<carrier>.json`) are the same kind of minimal pointer,
referencing each carrier's own existing vanilla texture (`minecraft:item/<carrier>`).

Reg's More Foods (`github.com/regfunkid/reg-more-foods`) is All Rights Reserved; this pack ships
no art, models, or text from it, only references to assets already present in the mod jar once a
player has it installed (which the modpack pins from the official Modrinth CDN).

## Build

Zipped into the modpack at build time by `client/mrpack/build.py` as
`overrides/resourcepacks/rmf-26.3-fix.zip`, and merged into the server's join resource pack by
`server/resourcepacks/build_server_pack.py`.
