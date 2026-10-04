# Backpacks! resource pack, patched for Minecraft 26.3

Upstream: "Backpacks!" by EclipseStudios, resource pack `1.3.5-backpacks_rscp-26.2.zip`
(https://modrinth.com/datapack/vanilla-backpacks), licensed Apache-2.0. Companion to the ported
datapack in `server/datapacks/eclipse-backpacks/`.

Served to players as half of the server's join resource pack, merged with
`client/resourcepack/rmf-26.3-fix/` by `server/resourcepacks/build_server_pack.py` into
`LosPollos-server-pack-26.3.zip`, hosted from the website repo (`sirbepys-minecraft-server`,
`site/downloads/`) because Modrinth only hosts the original. After a change: rerun that script,
re-host the zip, then update `resource-pack-sha1` in `server/config/server.properties` and on the
live server.

## Changes from upstream (2026-10-04)

- `pack.mcmeta`: `max_format` 88 -> `[100, 0]` (`min_format` 84 kept). 26.3 clients are on
  resource format 97 (client jar `version.json` `resource_major: 97`), outside the declared range,
  and players saw purple-black missing textures.
- `assets/minecraft/atlases/blocks.json`: added directory sources for `item/backpacks` and
  `item/gui`. In 26.3 the vanilla blocks atlas no longer stitches `item/` (only `block/`, read from
  the 26.3 client jar's `assets/minecraft/atlases/blocks.json`), while the backpack models' textures
  live under `textures/item/backpacks/`. The items atlas still covers them; adding them to both is
  harmless and covers whichever atlas the 3D models resolve against.

- `assets/minecraft/shaders/core/text.vsh`: rebuilt from the 26.3 client jar's own `text.vsh` with
  upstream's `#7b7b00` title-recolor block appended. Upstream's copy used the pre-26.3 shader syntax
  (`#moj_import`, no `layout(location)`); 26.3 failed to compile it, which made the client drop the
  WHOLE pack whenever it sat above Default (players saw purple backpacks plus a visible menu
  minecart). Below Default the vanilla shader won, so models loaded but Default's opaque
  `minecart.png` made the menu minecart visible.
- `assets/minecraft/shaders/core/text_background.vsh`: removed; 26.3 has no shader by that name.

Confirmed in game 2026-10-04 (CoffiebagSupreme) that the backpack models render with the
format/atlas fixes; the shader fix is pending player confirmation.
