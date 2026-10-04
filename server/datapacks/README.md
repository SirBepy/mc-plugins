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
