# Backpacks! - ported to Minecraft 26.3

- **Upstream project**: "Backpacks!" by EclipseStudios
- **Upstream version**: 1.3.5, built for Minecraft 26.2 (Modrinth slug `vanilla-backpacks`)
- **Upstream URL**: https://modrinth.com/datapack/vanilla-backpacks
- **License**: Apache-2.0 (per the Modrinth listing; no LICENSE/NOTICE file was bundled in the
  upstream download, so none is carried over here)

This folder is the unzipped 1.3.5 datapack with the JSON changes below applied so it loads under
Paper 26.3 (data pack_format 121) with zero registry/function errors. No gameplay logic was
changed: every edit is a structural/key-rename fix for the vanilla datapack schema that changed
between pack_format 101 (what 1.3.5 targeted) and 121. Ground truth for the new shapes came from
reading `C:\tmp\bp\dp` (the "Backpacks & More" datapack, already native 26.3) and the vanilla
advancement JSON bundled inside `paper-26.3-143.jar` itself (`data/minecraft/advancement/**`),
not from guessing.

## Files changed and why

- `pack.mcmeta`: `pack_format`/`min_format`/`max_format` bumped from `101` / `[101,1]`-`[107,1]`
  to `121` / `[121,0]`-`[121,0]` (26.3's real data pack_format, confirmed from `version.json`
  inside the Paper jar: `data_major: 121, data_minor: 0`; the task brief's guess of 108 was wrong).
- **3 advancement files** (`advancement/holding_bp.json`, `advancement/not_holding_bp.json`,
  `advancement/summon_bp.json`): `conditions.player` was a one-element array of loot-style
  conditions; 26.3's `minecraft:tick` trigger requires `player` to be a single predicate object
  (boot error: `"Not a JSON object"`). Unwrapped the array to its sole element.
- **Same 3 files, plus every predicate file below**: every condition/predicate dispatch object's
  `"condition"` key renamed to `"type"` (e.g. `"condition": "minecraft:entity_properties"` ->
  `"type": "minecraft:entity_properties"`). Sub-predicate field names inside `predicate` objects
  (`equipment`, `flags`, `nbt`, `slots`, `vehicle`, `minecraft:custom_data`) are unchanged -
  confirmed against `C:\tmp\bp\dp`'s native 26.3 predicate files, which use the same bare names.
- `advancement/open.json`, `senti/advancement/interact.json` (32 criteria): the
  `player_interacted_with_entity` trigger's `entity` field was the shorthand
  `{"nbt": "{Tags:[...]}"}`; 26.3 requires the full predicate dispatch wrapper (boot error:
  `"No key type"`). Rewrote as
  `{"type": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:nbt": "..."}}`,
  matching `C:\tmp\bp\dp\data\ulg\advancement\bp\sub\adv_interact_with_tabled_backpack\v26_3.json`.
- **9 `advancement/tree/*` and `advancement/upgrade/*` files**: `recipe_crafted` criteria used
  `"recipe_id": "ns:path"`; 26.3 renamed that field to `"recipes"` (same value, boot error:
  `"No key recipes"`). Confirmed against vanilla's own `adventure/trim_with_any_armor_pattern.json`.
- **22 predicate files** under `predicate/` (both `backpacks` and `senti` namespaces): same
  `"condition"` -> `"type"` rename as above (these files are loot-condition predicates evaluated
  by `/execute if predicate` and other conditions, using the same renamed dispatch key).
- **10 `item_modifier/*` files**: the loot-item-function dispatch key `"function"` renamed to
  `"type"` (boot error: `"No key type"` on e.g. `update_old_models/backpacks/big`). The 3 files
  under `item_modifier/upgrade/` were also unwrapped from a one-element array to a bare object
  (boot error: `"Not a JSON object"` even after the key rename - 26.3's single-function root shape
  must be an object, not an array-of-one). This rename was scoped to files under `item_modifier/`
  only: advancement `rewards.function` (which names a reward mcfunction, a different field with
  the same key) and loot_table `function` entries were deliberately left untouched.

No other files (recipes, item_modifier `big`/`enormous`/`medium`/`small` etc. component maps,
`.mcfunction` files, loot tables, sorter configs, tags) needed changes - their schemas did not
change between pack_format 101 and 121.

## Runtime fix: minecart `DisplayState` NBT format (26.3 renamed `Name`/`Properties` to `id`/`properties`)

The port above fixed everything that broke at *boot*. A separate bug only shows up at *runtime*:
every minecart the pack disguises as a command block (so it renders invisible/custom) used the old
`DisplayState:{Name:"minecraft:command_block",Properties:{conditional:"true",facing:"up"}}` shape.
26.3 logs this every tick as a `Serialization errors` WARN (`net.minecraft.server.commands.data
.EntityDataAccessor`): `Failed to decode value '...' from field 'DisplayState': Failed to parse
either. First: Not a string; Second: No key id in MapLike[...]` and the disguise never applies
(confirmed live: `data get entity <minecart> DisplayState` came back "Found no elements" after the
old-format merge).

**Format found empirically** (summoned real chest/plain minecarts on `server/local`, Paper 26.3
build 143, and tried candidates with `data merge` + `data get`, watching `logs/latest.log` for
`Serialization errors`):
- A bare string (`DisplayState:"minecraft:command_block"` or with the old
  `[conditional=true,facing=up]` bracket syntax) only ever accepts a *plain resource location, no
  properties* - the bracket form is rejected ("Not a valid resource location ... Non [a-z0-9/._-]
  character"). An **empty object `DisplayState:{}`** is also rejected ("No key id in
  MapLike[{}]") even though it only logs once (at summon time) rather than every tick.
- The accepted object form is **`DisplayState:{id:"minecraft:<block>",properties:{<key>:"<value>",...}}`**
  - `id` is mandatory; a map missing it fails the same way the old `Name`/`Properties` shape does.
  - The key is lowercase **`properties`**, not the old capitalized `Properties` (tried `Properties`
    and `state` too - both get silently dropped, no error, but also no visual effect: `data get`
    echoes back only the bare `id` with default block state).
  - Confirmed with `data get` echoing back exactly what was merged
    (`{id: "minecraft:command_block", properties: {facing: "up", conditional: "true"}}`) and zero
    `Serialization errors` on every following tick.
  - Setting only `.id` via `data modify ... DisplayState.id set value "minecraft:chest"` while a
    stale `properties` compound (built for a different block) is still present is **safe** - the
    block-state codec silently drops properties that don't apply to the new block id, logging
    nothing.

**Files changed** (all `DisplayState` merges, renaming `Name`->`id` and `Properties`->`properties`,
values otherwise untouched):
- `function/minecarts/invisible_minecarts.mcfunction` (5 lines, one per minecart type).
- `function/tick.mcfunction` (2 lines, the furnace-minecart texture-change merges).
- `function/cmd/uninstall_invminecarts.mcfunction`: the 6 `data modify ... DisplayState.Name set
  value "..."` lines (chest/hopper/furnace/tnt/command_block/spawner) became `DisplayState.id`.
  The plain-`minecart` revert line was `data remove entity @s DisplayState.Name` - renaming only
  the leaf to `.id` would leave a stale `properties` compound with **no** `id` key behind, which
  (unlike the reverse order above) *does* fail to decode and would reintroduce the exact
  per-tick warning this fix removes. Changed to **`data remove entity @s DisplayState`** (the
  whole field) instead, which is also what the original code was clearly trying to do (fully
  revert to "no disguise").
- `function/bp/summon/summon.mcfunction`: the menu minecart was summoned with `DisplayState:{}`
  (presumably a no-op placeholder - the minecart is already pushed off-screen via
  `DisplayOffset:-999999999` regardless of any disguise). 26.3 logs a one-time `Serialization
  errors` WARN for this on every summon; removed the now-always-invalid key entirely rather than
  giving it a fake valid id, since the intent was always "no disguise block".

Verified on `server/local` (Paper 26.3 build 143): summoned fresh `chest_minecart` and `minecart`
entities after the fix, `data get entity ... DisplayState` echoes the correct `id`/`properties`
object, and `logs/latest.log` shows zero `Serialization errors` lines for any minecart across a
14+ minute idle run (well past the 3-minute bar) after the reboot that picked up this fix - the
only 4 such lines in that log are from *before* the fix (the first boot, with the unmodified
pack), confirming the fix (not mere non-reproduction) is what stopped them.

### Companion resource pack (not modified; confirmed compatible)

The RP's `assets/minecraft/blockstates/command_block.json` keys its model-override variants purely
on the block-state string (`conditional=false,facing=down`, `conditional=true,facing=up`, etc.) -
the same facing/conditional values this port's `DisplayState` merges have always used, unchanged
by this fix (only the NBT *encoding* of those same values changed, `Name`/`Properties` ->
`id`/`properties`; the actual resolved block state sent to the client is identical either way).
So the RP needs no changes and still disguises minecarts exactly as before, for whichever
client - Java or Bedrock - has it installed.

### Known limitation: could not run the full bot-driven gameplay test on this server

`e2e/eclipse-backpacks.mjs` (new) covers crafting/giving, holding -> menu auto-summon, opening the
window, depositing/withdrawing items, dupe probes (drop + hand-swap with the menu open), death
drops and upgrade-recipe registration - but a Mineflayer bot (protocol 26.1, the newest Mineflayer
4.39 speaks) gets kicked with `multiplayer.disconnect.invalid_player_movement` within a second of
reaching the `play` state on `server/local` (Paper 26.3), after first needing a manual
`resource_pack_receive` ack during configuration that `e2e/lib.mjs`'s `createBot` doesn't send. A
raw packet capture showed `declare_recipes` (huge on this heavily-modded server) desyncing
node-minecraft-protocol's parser right before the kick. This is the same class of issue already
on record in `.claude/todos/10-handoff-real-server-setup.md` (ViaBackwards mangling older clients'
movement packets on 26.3), which is why the bot e2e suite runs against a separate Paper 26.2
`testserver` instead - that workaround doesn't apply here since this fix is 26.3-specific. Filed
as a follow-up todo for the orchestrating session. Verified instead via RCON-only checks (above):
the `DisplayState` fix itself, and an idle multi-minute error-free run.

## Performance fix: no per-tick NBT reads (2026-10-05)

A spark profile of the live server (6 players) showed this pack's per-tick work at ~27% of the
main thread: every `nbt=` / `"nbt"` check serializes the whole entity first, and a player carrying
backpacks has very large NBT. Behavior is unchanged; only how the same conditions are tested:

- `advancement/holding_bp.json`, `not_holding_bp.json`, `summon_bp.json`: the
  `{Tags:[backpacks.backpack_menu_summoned]}` and `{Tags:[backpacks.give_back_timer]}` nbt
  predicates became `minecraft:entity_scores` checks on two new dummy objectives,
  `backpacks.summoned` and `backpacks.give_back` (1 = tag present). A missing score fails the
  check, which matches "tag absent".
- `function/tick.mcfunction`: mirrors both tags into those scores for every player at the start of
  each tick (functions run before players tick, so the advancements see the current tags), and the
  four dropped-item scans (`menu_backpack` items and the three thrown-upgrade rules) use
  `if items entity @s contents <item>[custom_data~{...}]` instead of `@e[type=item,nbt=...]`.
  `custom_data~` is the same partial NBT match the selector did.
- `bp/summon/main`, `bp/control/kill_continue`, `bp/join/main`, `bp/control/check_inside/cancel`,
  `bp/control/crouch_timer`: set the matching score right where the tag is added/removed, so a
  later check in the same tick sees the change too.
- `load.mcfunction` / `cmd/uninstall_confirmed.mcfunction`: create / remove the two objectives.

Guarded by `e2e/server-check.mjs` (no tick-trigger advancement with an nbt predicate, no
`nbt=` item selector in this tick function, thrown-upgrade still works).

## Repair: page 1 saved with a later page's layout (2026-10-10)

ImMijo's netherite backpack on live (40 slots, 2 pages) had page 2's layout saved over page 1:
both stored pages held a "go back" arrow at slot 18 and 12 locked panes, and neither had a "next
page" arrow (read on live with `data get`). Opening it always resets to page 1, so it showed what
looked like page 2, Back flipped between the two copies, and the real page 1 was unreachable. How
the bad save happened is not known yet: on a private server every clean path (fresh and upgraded
netherite, Next/Back, deposit/withdraw, close on page 2 and reopen, backpack leaving the hand with
the GUI open) saved correctly.

- `bp/container/update/main.mcfunction`: after loading page 1 (page score 0), a "go back" arrow
  in the menu runs `bp/container/update/repair_first_page`, which removes the arrow and the locked
  panes (page 1 of a multi-page backpack has none) and puts "next page" back at 26 if that slot is
  free. The next save (a click, closing the GUI, opening it again) writes the fixed page back, so
  an affected backpack heals the first time it is opened. Items on that page are kept.

Guarded by `e2e/backpack-pages.mjs` (Bedrock client on server/local): it saves page 2's display
over page 1 the same way, checks the stored data matches live's, and expects a proper page 1 on
the next open.

## Verified

- `:server:assembleLocalServer` copies this folder into `world/datapacks/eclipse-backpacks`
  (Gradle's `copyTree` walks the whole directory tree, not just top-level entries - folder packs
  deploy the same way as the committed zip packs).
- Local Paper 26.3 build 143 boot: reached `Done (20.644s)!`, zero log lines matching
  `backpacks:`/`senti:` and `ERROR`/`Failed to`. (Two unrelated `ERROR: Failed to load function
  ulg:bp/backpacks/plugin_socket/...` lines come from the pre-existing, separately-installed
  "Backpacks & More" pack (`ulg` namespace, happens to have a `backpacks` path segment) and are
  not part of this port.)
- `datapack list enabled` includes `file/eclipse-backpacks`.
- `function backpacks:load` and `function senti:start` both ran via RCON with no error.
