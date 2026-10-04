# Backpacks! resource pack, patched for Minecraft 26.3

Upstream: "Backpacks!" by EclipseStudios, resource pack `1.3.5-backpacks_rscp-26.2.zip`
(https://modrinth.com/datapack/vanilla-backpacks), licensed Apache-2.0. Companion to the ported
datapack in `server/datapacks/eclipse-backpacks/`.

Served to players from the website repo (`sirbepys-minecraft-server`,
`site/downloads/Eclipse-Backpacks-textures-26.3.zip`) because Modrinth only hosts the original.
Rebuild the zip from this folder with every file at the zip root, then update `resource-pack-sha1`
in `server/config/server.properties` and on the live server.

## Changes from upstream (2026-10-04)

- `pack.mcmeta`: `max_format` 88 -> `[100, 0]` (`min_format` 84 kept). 26.3 clients are on
  resource format 97 (client jar `version.json` `resource_major: 97`), outside the declared range,
  and players saw purple-black missing textures.
- `assets/minecraft/atlases/blocks.json`: added directory sources for `item/backpacks` and
  `item/gui`. In 26.3 the vanilla blocks atlas no longer stitches `item/` (only `block/`, read from
  the 26.3 client jar's `assets/minecraft/atlases/blocks.json`), while the backpack models' textures
  live under `textures/item/backpacks/`. The items atlas still covers them; adding them to both is
  harmless and covers whichever atlas the 3D models resolve against.

Unverified on a real client at the time of writing: players were asked to confirm in game.
