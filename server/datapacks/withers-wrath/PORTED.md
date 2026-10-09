# Wither's Wrath - ported to Minecraft 26.3

- **Upstream project**: "Wither's Wrath" by ImHer0 (Modrinth slug `witherswrath`, project id `qqIrLnDt`)
- **Upstream version**: 1.16, version id `Dzo69Lep`, built for Minecraft 26.2 (the newest listed
  game version at port time; no 26.3 build existed on Modrinth as of 2026-10-09)
- **Upstream URL**: https://modrinth.com/datapack/witherswrath
- **Download URL**: https://cdn.modrinth.com/data/qqIrLnDt/versions/Dzo69Lep/%5B26.2%5Dwitherswrath-1.16.zip
- **SHA512** (verified byte-for-byte against the Modrinth API's `files[0].hashes.sha512` for this
  version): `c2d7c3f71693f04594ebc0108cba19d3fcbe19de770525a1ca4f2025f0f79643e7838df9917d37b9f64a64d102d86e5713c89bb7541c0d4bc974ec2842f35cfe`
- **License**: CC-BY-NC-4.0 (Creative Commons Attribution-NonCommercial 4.0 International), per
  the Modrinth project's `license` field, linking to
  https://github.com/ImHer0/witherswrath/blob/main/LICENSE. This license permits redistribution
  and modification (it is not a NoDerivatives license) as long as attribution is given and the use
  is non-commercial - this server is neither selling the pack nor gating it behind payment, so the
  port below is compliant. Attribution: "Wither's Wrath" by ImHer0
  (https://modrinth.com/user/ImHer0, GitHub https://github.com/ImHer0), unmodified license terms
  carried forward with this folder.
- **Author verification**: the Modrinth team for project `qqIrLnDt` has exactly one member, role
  `Owner`, user `ImHer0` (user id `tl4bYm5K`) - confirmed via `GET /v2/team/nrh2rjwK/members` and
  `GET /v2/user/tl4bYm5K`, matching the version's own `author_id`. Not a reupload.
- **Zip contents check**: 199 entries, all `pack.mcmeta`, `pack.png`, `README.txt`, or
  `data/**` (182 `.mcfunction`, 14 `.json`). No `.jar`, `.exe`, script, or anything outside the
  datapack data model.

This folder is the unzipped 1.16 datapack with the JSON changes below applied so it loads under
Paper 26.3 (data pack_format 121) with zero registry errors. No gameplay logic was changed - every
edit is a structural/key-rename fix for the advancement-trigger schema that changed between
pack_format 107 (what 1.16 targeted, per its own `pack.mcmeta`) and 121. Ground truth came from the
real Paper 26.3 server jar (`server/local/versions/26.3/paper-26.3.jar`, confirmed
`data_major: 121, data_minor: 0` in its bundled `version.json`) and from `C:\tmp\bp\dp`, an
already-native-26.3 reference pack used by a prior port in this repo
(`server/datapacks/eclipse-backpacks/PORTED.md`), not from guessing.

## Files changed and why

- `pack.mcmeta`: `pack_format` bumped `107` -> `121`; `min_format`/`max_format` bumped from the
  bare integers `102`/`107` to `[121,0]`/`[121,0]`, matching 26.3's real data pack_format
  (`version.json` inside `paper-26.3.jar`).
- **9 advancement files** under `data/wither/advancement/wither/` (`defeated_skeleton.json`,
  `defeated_wither.json`, `hit_wither.json`, `hit2_wither.json` through `hit6_wither.json`,
  `prehit_wither.json`): every `minecraft:player_killed_entity` / `minecraft:player_hurt_entity`
  trigger's `conditions.entity` field was a bare EntityPredicate sub-predicate map (e.g.
  `{"minecraft:entity_type": "minecraft:wither", "nbt": "{Tags:[\"Wither\"]}"}`). 26.3's
  `ContextAwarePredicate` for that field requires the full predicate-dispatch wrapper. Rewrote each
  as `{"type": "minecraft:entity_properties", "entity": "this", "predicate": {...the original
  sub-predicate map...}}`, confirmed against the vanilla jar's own
  `data/minecraft/advancement/adventure/kill_a_mob.json`, `end/kill_dragon.json` and
  `nether/return_to_sender.json`, which all use exactly this wrapper for the same two triggers.
  `prehit_wither.json`'s sibling `conditions.damage` field (a `DamagePredicate`) keeps its shape,
  `"type":{"tags":[...]}` with no dispatch wrapper, like vanilla's own
  `data/minecraft/advancement/story/deflect_arrow.json` (only the `entity` field is a
  `ContextAwarePredicate`; `damage` and nested fields like `killing_blow.direct_entity` stay bare
  maps, as in `nether/return_to_sender.json`). But each tag entry's `id` must now be a tag
  reference with a leading `#`: `"minecraft:is_projectile"` -> `"#minecraft:is_projectile"`. The
  unprefixed id stopped a 26.3 server from booting at all ("Failed to load datapacks, can't proceed
  with server load ... Not a tag id"); vanilla's `deflect_arrow.json` and `shoot_arrow.json` use
  `"#minecraft:is_projectile"`.
- **Same 9 files**: inside that same `predicate` map, the bare `"nbt"` sub-predicate key was
  renamed to `"minecraft:nbt"` (6 of the 9 files use it: `defeated_skeleton.json` and
  `hit2_wither.json` through `hit6_wither.json`; `defeated_wither.json`, `hit_wither.json` and
  `prehit_wither.json` have no `nbt` key, so only the wrapper applied to those three). Confirmed
  against `C:\tmp\bp\dp`'s
  `data/ulg/advancement/bp/sub/adv_interact_with_tabled_backpack/v26_3.json` and
  `adv_hit_tabled_backpack/v26_3.json`, both already-native-26.3 files using
  `"minecraft:nbt"` (namespaced) inside the same kind of `entity_properties` predicate map for the
  identical "match an entity by its `Tags` NBT" pattern this pack needs.
  **Correction to a prior claim in this repo**: `eclipse-backpacks/PORTED.md` states sub-predicate
  keys including `nbt` are "unchanged" (bare) in that same `C:\tmp\bp\dp` reference - reading the
  actual files in that directory this session shows the opposite (`"minecraft:nbt"`, not `"nbt"`).
  That eclipse-backpacks file wasn't touched (out of scope here), but this port follows the
  evidence read directly from `C:\tmp\bp\dp` this session, not that earlier claim.

No other files needed changes. Checked and ruled out (none present in this pack): `"condition"`
loot/predicate dispatch keys, `recipe_crafted`'s `recipe_id` field, `item_modifier` inline
`"function"` dispatch keys (no `item_modifier/`, `predicate/`, or `recipe/` folders exist in this
pack at all), `generic.*` attribute ids (every `attribute`/entity-NBT `attributes` array already
uses short-form ids like `minecraft:gravity`, `minecraft:max_health` - the two `generic.` hits in
the `.mcfunction` files are sound effect names, `minecraft:entity.generic.explode`, unrelated),
minecart `DisplayState` NBT (no minecarts in this pack), `item replace`/`replaceitem` commands,
item-frame `block_pos` predicates, legacy `clickEvent`/`hoverEvent` text-component keys in any live
(non-comment) command (the pack's two live `tellraw` commands already use the modern
`click_event`/`hover_event` keys; a few `#`-commented JSON blobs used by the author's own
text-component authoring tool still show old-format keys, but those lines are comments, never
parsed by the game). Item NBT (nether star trophy, item/item_display summons) already uses the
1.20.5+ `components:{"minecraft:...": ...}` item-component model throughout, unchanged since.

## Default config: Wither block/destruction damage stays ON

`data/wither/function/initialize_storage.mcfunction` sets `toggledestruction` to `Enabled` by
default (and `toggledestruction_string` to the pack's "on" color, `#13e800`) - this is the pack's
own name for the Wither's block-breaking/explosion destructiveness toggle (read in
`wither/fight_start/init.mcfunction` and `wither/lifecycle/cleanup.mcfunction` to gate
`gamerule mob_griefing`/bedrock-clearing behavior around the fight, and in several phase2
charge/dash functions). **No change was made**: the default already ships ON, satisfying the "keep
Wither block damage ON" requirement as-is. Every other toggle (`toggletrophy`, `togglehoming`,
`togglecharge`, `toggledash`, `toggleboomtp`, `togglespawns`, `toggleweather`) also defaults
`Enabled`; `spawnreq` is the only toggle that defaults `Disabled`; `toggleanimation` defaults to
the string `Default` (its three states are the strings `Default`/`Disabled`/`Bedrock`, not the
0/1/2 the function's own comment describes - the comment is stale, the actual string values are
what every toggle/dialog function reads and sets). All of this matches the unmodified upstream
pack; nothing here was touched.

## Verified

- All 14 JSON files (9 advancement, 1 loot_table, 4 tags) plus `pack.mcmeta` parse cleanly
  (`node -e "JSON.parse(...)"` on every file).
- `grep` sweep of the whole folder for leftover `"condition":` keys, `recipe_id`, `generic.`
  attribute ids in JSON, and `item_modifier` folders: all empty/absent.
- Booted on a Paper 26.3 test server (`:testserver:runServer`, with the worldgen packs) on
  2026-10-09: "Found new data pack file/withers-wrath, loading it automatically" and no registry or
  function errors from the `wither` namespace (the first boot, before the `#` fix above, refused to
  start). A Wither built from soul sand and skulls there got the pack's `theWither` tag and its
  health clamp (98 HP until the fight's first stage), so the tick functions run. The fight itself
  (attacks, phases, the 600 HP stage) needs a player nearby and wasn't played: Mineflayer bots
  can't join 26.3.

## For WitherGuard (the companion anti-cheese plugin)

How this pack recognizes, manipulates, and ticks the Wither, read directly from its functions this
session:

- **Recognition**: pure vanilla entity type (`@e[type=minecraft:wither]`) plus its own scoreboard/
  tag bookkeeping - no custom entity, no scoreboard-based "this is THE boss" marker beyond a
  `witherCount` dummy objective and transient tags (`justSummoned`, `theWither`) added the tick
  after a vanilla Wither is detected (`data/wither/function/tick.mcfunction`,
  `execute as @e[type=minecraft:wither,tag=!theWither,limit=1] at @s run tag @s add justSummoned`
  -> `function wither:wither/fight_once`). It also adds the Wither (and nearby hostile helpers) to
  a scoreboard team `Wither` (`team add Wither` in `fight_start/init.mcfunction`) purely for
  `friendlyFire false`/no-nametag, not for identification.
- **Re-summon vs. modify-in-place**: it never re-summons. It operates on whatever vanilla Wither
  entity already exists and mutates it with `data merge entity @e[type=wither,...] {Health:600f,
  Tags:[...],equipment:{...},attributes:[...]}` (`fight_start/init.mcfunction`) to set its 600 HP,
  phase tags (`Bedrock`,`Charge`,`Wither`,`Dash`,`Swarm` - all added at once, then stripped one at a
  time as the fight progresses through `wither/hit/hit2`-`hit6`), enchanted-but-0%-drop-chance
  armor, and a custom `attributes` array (`follow_range:69`, `knockback_resistance:0.5`,
  `max_health:600`, `movement_speed:0.8`, `step_height:2`,
  `explosion_knockback_resistance:0.9`, `armor:1`, `armor_toughness:0`, `flying_speed:0`/`1`). If a
  **second** Wither ever exists, `wither/lifecycle/check.mcfunction` (run every 5 ticks from
  `5tick.mcfunction`) compares both Withers' tracked `checkHealth` scores and kills whichever is
  lower via `wither/lifecycle/prevent_multiple.mcfunction` (shrinks it to `scale:0.0625`, refunds a
  summon's worth of wither skulls + soul sand as dropped items, then `kill @s`) - so WitherGuard
  should expect **at most one** Wither to survive past a few ticks regardless of how many a player
  manages to summon.
- **Teleports / NoAI / Invulnerable / Pos changes** (every instance found via `grep -rn "run tp \|
  NoAI\|Invulnerable" data/`):
  - `fight_start/init.mcfunction`: relative self-`tp` (`~ ~-8 ~` / `~ ~8 ~`) up to 8 of the Wither's
    own escape checks if it spawned inside bedrock (anti-void-stuck failsafe), then briefly
    `Invulnerable:1b,NoAI:1b,NoGravity:1b` is NOT set here (that's only at `midpoint.mcfunction`).
  - `midpoint.mcfunction` (the ~half-health phase transition): sets `Invulnerable:1b,NoAI:1b,
    NoGravity:1b` plus 255-amplifier resistance for a few ticks (a scripted mid-fight cutscene),
    then `midpointground.mcfunction` clears both back to `0b`.
  - `phase2/charge/chargepre.mcfunction` sets `NoAI:1b` before a charge attack windup;
    `charge2.mcfunction` and `dashes/endloop.mcfunction` clear it back to `0b` after.
  - `phase2/dashes/dash.mcfunction` / `dashto.mcfunction`: the dash attack itself is implemented as
    a scripted `tp` sequence (conditionally, only while no `chargew`/`wStand` armor-stand marker
    exists), not a vanilla AI pathing dash.
  - `phase1/homing/*`, `phase1/shift_away.mcfunction`, `phase2/charge/charge.mcfunction`,
    `phase2/enraged/skulls/*`: more `tp` usage, all on the Wither itself or its spawned
    skull/armor-stand helper entities (never on a player).
  - No file teleports a **player** - every `tp`/`teleport` hit in the grep targets the Wither,
    its proxy creepers, armor-stand markers, or skull entities.
- **Tick/load functions and per-tick cost** (`data/minecraft/tags/function/{load,tick}.json` wire
  `wither:load` + `wither:5tick` to `#minecraft:load`, and `wither:tick` to `#minecraft:tick`):
  - `wither:tick` (every tick, unconditional while the pack is loaded, Wither alive or not): 3
    `execute ... store result score @s checkHealth/Health run data get entity @s Health` calls
    (NBT reads) **only when a Wither exists** (gated by the `@e[type=minecraft:wither,...]`
    selector failing to match otherwise, so the NBT read itself is skipped with no Wither alive) -
    plus 2 `if items entity @p[sort=nearest] weapon.mainhand ...` checks against the nearest
    player every tick, which run **every tick regardless of whether a Wither is alive**, since the
    `execute at @e[type=minecraft:wither,...]` guard those are nested under will simply not match
    and the line is skipped - so the per-tick cost without a Wither alive is near zero (failed
    selector matches only), and with a Wither alive it's 3 NBT entity reads + 2 player
    mainhand-item predicate checks + several more `data merge`/`data modify` lines gated by
    `toggle*` storage flags. This is the same *shape* of per-tick NBT cost flagged as a measured
    lag source elsewhere in this repo (`eclipse-backpacks`' own performance fix, `live-lag-is-
    datapack-nbt` memory) - WitherGuard sharing the server with an active Wither fight should
    expect this pack's own per-tick NBT reads to already be present and scoped to "while a Wither
    exists", not something WitherGuard introduces or needs to work around beyond being aware of it.
  - `wither:5tick` (self-rescheduled every 5 ticks via `schedule function wither:5tick 5t` at its
    own end, so it runs continuously once started by `load.mcfunction`, Wither alive or not): a
    larger batch of `execute at @e[type=minecraft:wither,...] as @e[...]` team-join / particle /
    helper-mob-effect lines, all guarded by the Wither existing; plus one unconditional
    `execute unless entity @e[type=player] run kill @e[...tag=hStand]` cleanup. Heavier than
    `tick` per-invocation but runs 5x less often.
  - `wither:load/wither_check` (self-rescheduled every **1 tick** via
    `schedule function wither:wither_check 1t`, started on datapack load and runs forever until a
    nether-star trophy item exists in the world): only 2 cheap scoreboard comparisons, no NBT
    reads, no entity selectors beyond a `limit=1` item search - negligible.
- **Config function and defaults**: `wither:config` just opens the in-chat menu
  (`function wither:dialog/main`, a clickable `tellraw` UI, not a vanilla "Dialog" screen); the
  actual toggles live in `wither:initialize_storage` (see the "Default config" section above for
  the full list and the destruction-damage default confirmation).
- **Wither HP, armor, explosions, skulls, minions**:
  - HP: vanilla 300 -> 600 (`fight_start/init.mcfunction`, the file list's `100hp.mcfunction`/
    `150hp.mcfunction`/`50hp.mcfunction` under `hit/checks/` are additional scripted HP-threshold
    phase triggers, not new base HP values).
  - Armor: full enchanted (Blast Protection 255) leather/iron armor set with `drop_chances` all `0`
    (never drops), `armor:1`/`armor_toughness:0` attributes.
  - Explosions: the Wither's own explosion handling isn't touched directly; instead the pack
    **proxies** visual/audio explosion effects through disguised, invulnerable, silent creepers
    (`CustomName:"the Wither's Wrath"`, `ExplosionRadius:13b` or a tiny `scale:0.0625` variant in
    `hit/prehit.mcfunction`'s "boom tp" direct-hit punishment) rather than ever calling a real
    explosion command on the Wither entity itself.
  - Skull projectiles: `wither/skull/launch*.mcfunction` and `wither/skull/burst/*.mcfunction`
    implement a homing/burst skull-launch system layered on top of (not replacing) the Wither's own
    vanilla skull attacks; `phase1/homing/*` adds a slow-homing variant.
  - Minions: `wither/midpoint/spawns/blaze1.mcfunction` through `blaze30.mcfunction` (30 near-
    identical files, each `summon`s one `blaze` (60 HP, `scale:0.8`, tag `wArcher`) at the Wither's
    position with a different `Motion` launch vector, carrying a passenger `wither_skeleton`
    archer (55 HP, `scale:1.25`, a named "Soul Bow", a trimmed unbreakable netherite chestplate,
    permanent fire resistance, `drop_chances` all 0) - a 30-minion burst flung outward in different
    directions at the midpoint phase transition) and `wither/passive/spawn.mcfunction` (passive
    wither-skeleton spawns over time, gated by `togglespawns`).
