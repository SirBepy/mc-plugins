# server/datapacks

Datapacks that aren't available as a stable pinned URL (unlike the Stardust worldgen packs, which
`ServerArtifacts.datapacks` downloads from Modrinth into `world/datapacks` at assemble time). These
are committed as files and copied into `world/datapacks` by `:server:assembleServer` /
`:server:assembleLocalServer`.

## Owner requests, resolved 2026-10-03 (verified against the Modrinth API for 26.3 `datapack` builds)

- **Stardust Labs worldgen (Terralith, Incendium, Nullscape, Structory, Structory: Towers,
  Amplified Nether)**: all six have 26.3 datapack builds (pinned in `ServerArtifacts.kt`), confirmed
  current as of 2026-10-03. Stardust Labs' full project list (`stardustlabs.miraheze.org`) adds only
  one more worldgen pack, **Continents** (reshapes the world into ocean-separated landmasses) - its
  own wiki states it is explicitly **incompatible with Incendium and Amplified Nether**, both
  already required here, so it's excluded.
- **Structory vs. Dungeons and Taverns**: both ship. No documented conflict between the two structure
  packs, and Dungeons and Taverns' author has publicly confirmed Terralith compatibility; Structory
  is already confirmed compatible with the rest of the Stardust set. Pinned in `ServerArtifacts.kt`
  (`Dungeons_and_Taverns` CDN entry, v6.0.2+26.3). Its zip doubles as an optional client resource
  pack for custom key item textures (not auto-pushed, see below - the textures are cosmetic only,
  keys still work and show a reused vanilla icon without it).
- **MasterCutter** (the link Joe sent for "a datapack for dropping player heads"): it is NOT a head
  drop pack. It's a stonecutter-recipe expansion (500+ recipes: woodcutting, extra stone cuts,
  recycling/un-crafting). Player heads on PvP death are already covered by the committed Vanilla
  Tweaks "Player Head Drops" pack above. Included anyway as a harmless, 26.3-compatible QoL bonus
  (pure data, no textures, no reported conflicts) since it meets the bar of "include if compatible
  and harmless."
- **Reg's More Foods** (Joe: optional/nice-to-have): 26.3 datapack build exists, extracted and
  committed as `reg-more-foods-v1.5.2+26.3.zip` (Modrinth ships it as a bundle zip containing a
  datapack zip + a resource pack zip + a dummy root `pack.mcmeta`, "UNZIP ME" per its own readme;
  only the inner datapack zip is committed here). **Its 200+ new food items need the companion
  resource pack for textures to render** - the upstream author's own instructions say so, and there
  is no separate, stable CDN URL for just that resource pack (it only exists bundled inside the
  "UNZIP ME" download), so it is NOT auto-pushed via `server.properties`. Without it, RMF item icons
  will likely show as missing/placeholder textures. Players who want them working must manually
  install "Reg's More Foods Resourcepack v1.5.2+26.3.zip" from
  https://modrinth.com/datapack/reg-more-foods client-side. No conflicts for this server (RMF's only
  documented issues are with JEI/REI/EMI recipe viewers and the Spice of Life mod, neither of which
  this server runs).
- **Ender dragon fight overhauls** (EDF Remastered, True Ending): **neither has a 26.3 build yet**
  (EDF Remastered tops out at 26.2, published 2026-07-16; True Ending tops out at 26.1.2, published
  2025-10-19) - confirmed against the Modrinth API 2026-10-03. Excluded for now since neither would
  even load; also worth noting EDF Remastered's own FAQ says it has no plans to be compatible with
  other End overhauls like True Ending, so only one could ever ship anyway. Revisit once either
  publishes a 26.3 build.
- **Dragonkind Evolved** by kanokarob (2026-10-09, Joe's pick for the dragon fight, alongside our
  own EndFight plugin): `dragonkind-evolved-v1.15.zip` is Modrinth's `dragonkind-evolved` v1.15,
  the 26.3 datapack (version id `ukamYQGg`, sha512 starting `4b8236362128b165`, matches the
  Modrinth API). Only datapack files inside (functions, tags, loot tables, two structure nbts, no
  jars or scripts). It adds 16 dragon types and difficulty tiers (Fledgeling 250 HP up to
  Ascended), crystal abilities, servants and its own portal loot; EndFight picks the tier for each
  fight (docs/endfight.md). Its license (github.com/kanokarob/Dragonkind-Evolved, license.txt)
  allows running it on a public server but forbids distributing the pack, whole or in part,
  edited or not, without the author's written permission. Its dragon names and custom items need
  its own resource pack (`dragonkind-evolved-RP-by-kanokarob-v1.10.zip`, same Modrinth version);
  Joe got kanokarob's permission on 2026-10-09 to merge that into our join pack, which it now is
  (`server/resourcepacks/dragonkind-evolved/`).

## Vanilla Tweaks (vanillatweaks.net), generated 2026-10-02 for Minecraft 26.3

Built via the site's zip builder (`https://vanillatweaks.net/assets/server/zipdatapacks.php`,
`packs` = category -> pack names, `version` = "26.3"), then unzipped (the download is one zip
containing one zip per pack, `UNZIP_ME`). Filenames below are the per-pack zips with spaces and
parentheses stripped from the site's original names.

| File | Pack | Version | Category |
| --- | --- | --- | --- |
| `armor-statues-v2.8.24.zip` | Armor Statues | 2.8.24 | Decorative/Cosmetic |
| `more-mob-heads-v2.20.1.zip` | More Mob Heads | 2.20.1 | Decorative/Cosmetic |
| `player-head-drops-v1.1.18.zip` | Player Head Drops | 1.1.18 | Decorative/Cosmetic |
| `fast-leaf-decay-v2.0.23.zip` | Fast Leaf Decay | 2.0.23 | Convenience |
| `graves-v4.1.2.zip` | Graves | 4.1.2 | Gameplay Changes |
| `track-statistics-v1.1.20.zip` | Track Statistics | 1.1.20 | Informative |

Each zip is already a valid datapack (pack.mcmeta at its root, `pack_format` 121/0, matching Paper
26.3's own `data_major`/`data_minor` from its bundled `version.json`). Minecraft loads a zipped
datapack directly from `world/datapacks`, no further extraction needed.

## Reg's More Foods, extracted 2026-10-03

| File | Pack | Version | Source |
| --- | --- | --- | --- |
| `reg-more-foods-v1.5.2+26.3.zip` | Reg's More Foods (datapack half only) | 1.5.2+26.3 | https://modrinth.com/datapack/reg-more-foods |

Unlike Vanilla Tweaks, Modrinth's own download for this one is a single "UNZIP ME" bundle
containing a datapack zip, a resource pack zip, a one-line readme, and a dummy root `pack.mcmeta`
(needed only to satisfy Modrinth's upload validator, per the readme inside it). Only the inner
datapack zip is committed here, renamed to match this folder's convention; `pack_format` 121,
matching Paper 26.3. See the owner-requests section above for why the resource pack isn't pushed.

## server-rules/

A custom folder datapack (not from Vanilla Tweaks): sets sleep percentage, disables fire spread
and hides the locator bar, once per world. No world border: the owner removed it (2026-10-03).
See its own README section in `server-rules/pack.mcmeta`'s description and `docs/server.md` for
the gamerule ids used, and why.

## eclipse-backpacks/, ported 2026-10-03

A custom folder datapack: "Backpacks!" by EclipseStudios (Apache-2.0, Modrinth `vanilla-backpacks`,
upstream v1.3.5), which only ships a 26.2 datapack build. Ported by hand to 26.3's data pack_format
121 - the 26.2 build fails Paper 26.3's registry load outright (advancement/predicate/item_modifier
condition dispatch keys were renamed in that format jump). See `eclipse-backpacks/PORTED.md` for
the full list of files changed and why (Apache-2.0 requires stating changes); no gameplay logic was
touched, only JSON key renames/reshapes. Verified booting clean on local Paper 26.3 build 143 with
`eclipse-backpacks` enabled and its load functions runnable over RCON.

Companion resource pack (`1.3.5-backpacks_rscp-26.2.zip`, not committed/hosted here): its
`pack.mcmeta` declares resource format 84-88, while 26.3's actual client resource format is 97
(`version.json` inside `paper-26.3-143.jar`: `resource_major: 97`). A 26.3 client will likely show
an "old format" warning but should still render it correctly - its item textures already use the
post-24w `assets/<ns>/items/*.json` item model definition system that the `item_model` component
needs, unchanged since format 84. Not modified or re-hosted per the task brief; in-client rendering
was not verified.

## mastercutter-fixes/, generated 2026-10-09

Fixes for third-party packs live in their own small folder pack that overrides the broken files
under the same ids, never as edits to a downloaded zip (those stay byte-for-byte what the pinned
URL serves). A pack later in the enabled list wins on a clash: a new folder pack is appended last
on a world that already has the zip, and on a fresh world `file/mastercutter-fixes` sorts after
`file/MasterCutter+...` (verified in server/local's level.dat, 2026-10-09).

MasterCutter v1.8.0 (mc26.3), the newest release, ships all 74 `recipe/wood/dark_oak/` and all 74
`recipe/wood/pale_oak/` recipes as copies of the cherry ones, so dark and pale oak couldn't be cut
(ImMijo's report, 2026-10-09). Every other wood, and every advancement, is right. This pack holds
the 148 corrected recipes, generated, never hand-edited: `python
server/mastercutter-fixes/generate.py` reads the pinned zip (URL from `ServerArtifacts.kt`) and
writes a fixed copy of each wood recipe that names another wood's items. `python -m unittest
discover -s server/mastercutter-fixes` checks every wood recipe players get names only its own
wood, matches its oak twin, and that the committed pack is the generator's output. When MasterCutter
is bumped, rerun the generator; if it writes nothing, delete this pack.

A datapack reload (`minecraft:reload`) would load it without a restart, but on Paper 26.3 that
builds a fresh recipe manager and drops every plugin recipe (Merfolk's Kelp Bed), so on live it
takes effect at the next restart.

## rmf-perf/, generated 2026-10-10

Overrides Reg's More Foods' `data/rmf/function/bg/stonecutter_damage.mcfunction` (the file, not the
zip - same rule as mastercutter-fixes). A live spark profile taken 2026-10-10 (vanilla `perf`, 5
players) showed `rmf:tick` at ~30% of the whole server tick, ~26% of that from this one function's
original 6 lines: each scans every non-player entity (or every non-creative player) and evaluates
the `rmf:on_stonecutter` predicate - a block location check plus an inverted entity-type-tag check
- once per entity, per line, even when the `rmf.stonecutterDamage` config option is off. The
override checks that config score once up front, folds the entity-type-tag exclusion into the `@e`
selector itself instead of a predicate, replaces the predicate's block check with a direct `if
block`, and funnels both groups (non-player entities, non-creative/non-spectator players) through
one shared `rmf:bg/stonecutter_hit` function instead of repeating the same check 3 times for
damage/sound/particle. Same damage (4, `rmf:slicing`), sound and particle as upstream; same immune
types (`#rmf:immune_to_stonecutter`); creative players still take no damage (spectators can't take
damage regardless).

Like mastercutter-fixes, `file/rmf-perf` sorts after `file/reg-more-foods-v1.5.2+26.3`
alphabetically, so a fresh world loads the override after the zip's own function and it wins;
verified on server/local 2026-10-10 via `datapack list enabled`.

Verify with RCON on server/local or `:testserver:runServer`: `datapack list enabled` shows
`file/rmf-perf` after the More Foods pack; a zombie or cow summoned on a stonecutter block takes 4
`rmf:slicing` damage every tick (same as upstream, confirmed by toggling `datapack disable
"file/rmf-perf"` and re-testing against the zip's own unmodified function); an armor stand or a
creative player on a stonecutter takes none; nothing off the block takes damage. For the
performance claim, summon ~2000 entities, run `perf start` (writes `debug/profiling/<ts>.zip`
after 10 s), and compare the `function rmf:tick` line's percentage in `profiling.txt` with
`file/rmf-perf` enabled vs. disabled.

When Reg's More Foods is updated, re-diff its `bg/stonecutter_damage.mcfunction` against this
override before shipping the new zip - a changed upstream damage/sound/particle value or a renamed
predicate/tag needs the override's `stonecutter_hit.mcfunction` updated to match, or the override
silently reverts to the old behavior instead of the new upstream one.

## spawn-market/, generated 2026-10-05

A custom folder datapack holding the spawn square build: a timber-and-tuff taiga market at world
spawn (427, -868) with six themed [Sell] stalls (Farmer, Lumberjack, Fishmonger, Smith, Mob drops,
Rare goods), a Dragon Balls station (radar shop sign, a guide lectern, 7 head pedestals for the
`dragonballs` plugin, coordinates exported to `server/spawn-market/dragonballs-station.json`), a
Guide stall of lecterns holding how-to books, a welcome and rules board, a bell and a well. It does
nothing on load: the build only happens on
`/function spawn_market:build`, which **clears every tree and block above the new ground in a
49x49 area around spawn** and flattens the middle 37x37 to y114. Back the area up before running
it on a world that has anything there. To refresh only the stalls, signs and books on a world
that already has the square, `forceload` the area and run `spawn_market:structure` then
`spawn_market:attach`, which leaves the cleared terrain and anything placed since alone.

The functions are generated, never hand-edited: change `server/spawn-market/generate.mjs` (layout,
palette, each stall's buy pool and prices) or `server/spawn-market/guide.mjs` (the guide books) and
rerun `node server/spawn-market/generate.mjs`;
`node --test server/spawn-market/generate.test.mjs` checks the output. The terrain blend reads
`server/spawn-market/terrain-snapshot.json`, the live ground heightmap taken 2026-10-05, so the
build only fits that spot. Verified on server/local (live spawn region copied in): all 3812 blocks
read back from the region file matched the generator's model.

The datapack places no [Sell] signs. The Market plugin (`market/`) owns them: every in-game day, at
sunrise, it picks 3 items from each selling stall's pool, rolls each price up to 20% either
side of its base, and writes them onto the stall's
three eye-level sign spots, from `market/src/main/resources/stalls.yml`, which the generator writes
too. So a pool or price change needs the generator run and a new Market jar. The signs' first line
is `<dark_blue>[Sell]`, Essentials' `signFormatSuccess`; command- and plugin-placed signs with it
pay out on live (balances rose after players used them, 2026-10-05).

## Wither's Wrath, ported 2026-10-09 for Minecraft 26.3

A custom folder datapack: "Wither's Wrath" by ImHer0 (CC-BY-NC-4.0, Modrinth `witherswrath`,
upstream v1.16), which only ships a 26.2 datapack build. Ported by hand to 26.3's data pack_format
121 - the 9 fight-tracking advancement files use a trigger-condition shape 26.3 no longer accepts
on boot. See `withers-wrath/PORTED.md` for the full file list, the license check, and the
evidence for every change; no gameplay logic was touched, only advancement JSON key renames.

It overhauls the vanilla Wither fight in place (no re-summon): 600 HP, an enchanted never-drop
armor set, scripted charge/dash/homing-skull attack phases, a midpoint 30-blaze minion burst, and
a trophy/nether-star reward sequence, all gated by its own in-chat config toggles. Joe picked it on
2026-10-09 for a harder Wither fight, alongside anti-cheese rules in the tweaks plugin's
WitherGuard. Its block-destruction toggle (`toggledestruction`) already defaults ON, matching the
requirement to keep Wither block damage enabled - nothing was changed there.
