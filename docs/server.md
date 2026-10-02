# The real server folder

`:server:assembleServer` builds the actual friends-server folder (deployed to the Hetzner VPS
later), as opposed to `testserver`, which only runs local dev/e2e servers.

## What it's made of

- **Plugins** (`server/build/*/plugins/`): our three plugins (Names, SafeChests, Tag) plus the
  pinned third-party list plus `realServerExtraPlugins` (Simple Voice Chat, Geyser, Floodgate,
  Chunky). Paper is a Bukkit-API server: it cannot load Fabric or Forge mods, only Bukkit/Spigot/
  Paper plugin jars. "Mods" are never part of this server.
- **Datapacks** (`server/build/*/world/datapacks/`): the Stardust worldgen packs (downloaded from
  Modrinth, same list as testserver) plus everything committed under `server/datapacks/`
  (Vanilla Tweaks packs and the custom `server-rules` datapack, see its own README section there).
  Datapacks change world content/behavior through vanilla's data-driven systems; they are not code.
- **Config** (`server/config/`): full, owner-editable files that always win, copied over the dist
  on every assemble, mirroring the server folder layout (`server/config/server.properties` ->
  `server.properties`, `server/config/config/paper-global.yml` -> `config/paper-global.yml`, etc).
- **Seed** (`server/seed/`): files copied in only if the target doesn't already exist, for data the
  live server owns afterwards (e.g. future LuckPerms seed data). Empty today.
- **Secrets** (`server/secrets.properties`, gitignored): optional `discordsrv.token`. Currently a
  no-op hook in the assemble task since DiscordSRV's config isn't committed yet.

Pinned download URLs (third-party plugins, Stardust datapacks, Paper itself, and the real-server
extras) live in one place: `buildSrc/src/main/kotlin/ServerArtifacts.kt`.

## Assembling

`.\gradlew.bat :server:assembleServer` writes `server/build/dist/`: Paper 26.3 build 143, every
plugin jar, every datapack, config applied, `eula.txt`, and `start.sh`/`start.bat` (Aikar's flags,
`-Xms10G -Xmx10G` for the CX43 VPS). It's idempotent and never deletes an existing `world*/` folder
or plugin data folder; it only ever adds or overwrites files it manages (jars, datapack zips,
config copies). Re-running it is safe and is how a later deploy will sync updates.

## Local smoke boot

`.\gradlew.bat :server:assembleLocalServer` writes `server/build/local/` the same way, then
overrides `server.properties` for local testing only: `online-mode=false`, `white-list=false`,
`enforce-whitelist=false`, `server-ip=127.0.0.1`, RCON on with a random password generated once
(kept across re-assembles, never committed). These overrides never reach `assembleServer`'s output.

Boot it through the supervisor, never a bare background `java`:

```
powershell -File sv.ps1 ensure -Project mc-plugins-tag -Kind ephemeral -Root server\build\local -Cmd "java -Xms2G -Xmx4G -Dcom.mojang.eula.agree=true -jar paper-26.3-143.jar --nogui"
```

Watch with `sv.ps1 logs -Id local:java`, stop with an RCON `stop`, then `sv.ps1 rm -Id local:java`.

## Known issue: Geyser does not currently enable

Geyser-Spigot 2.11.3 build 1247 (the current pinned build) fails to enable on Paper 26.3 build 143
beta: `IllegalArgumentException: Couldn't find asBukkitCopy or asCraftMirror method on
CraftItemStack`, inside its shaded command-parser reflection. This is an upstream Geyser/Paper-beta
compatibility gap, not a config problem here; Bedrock players cannot join until GeyserMC ships a
build that supports 26.3's beta internals. Floodgate loads fine but does nothing without Geyser.

## Deploy (not done yet)

Syncing `server/build/dist/` to the Hetzner VPS, systemd unit, firewall, and secrets provisioning
are a later task.
