# server/datapacks

Datapacks that aren't available as a stable pinned URL (unlike the Stardust worldgen packs, which
`ServerArtifacts.datapacks` downloads from Modrinth into `world/datapacks` at assemble time). These
are committed as files and copied into `world/datapacks` by `:server:assembleServer` /
`:server:assembleLocalServer`.

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

## server-rules/

A custom folder datapack (not from Vanilla Tweaks): sets sleep percentage, disables fire spread,
and sets the overworld/nether world border, once per world. See its own README section in
`server-rules/pack.mcmeta`'s description and `docs/server.md` for the gamerule ids and border
values used, and why.
