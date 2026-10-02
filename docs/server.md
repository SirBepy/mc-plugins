# The real server folder

`:server:assembleServer` builds the actual friends-server folder (deployed to the Hetzner VPS
later), as opposed to `testserver`, which only runs local dev/e2e servers.

## What it's made of

- **Plugins** (`server/{dist,local}/plugins/`): our three plugins (Names, SafeChests, Tag) plus the
  pinned third-party list plus `realServerExtraPlugins` (Simple Voice Chat, Geyser, Floodgate,
  Chunky). Paper is a Bukkit-API server: it cannot load Fabric or Forge mods, only Bukkit/Spigot/
  Paper plugin jars. "Mods" are never part of this server.
- **Datapacks** (`server/{dist,local}/world/datapacks/`): the Stardust worldgen packs (downloaded from
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

`.\gradlew.bat :server:assembleServer` writes `server/dist/`: Paper 26.3 build 143, every
plugin jar, every datapack, config applied, `eula.txt`, and `start.sh`/`start.bat` (Aikar's flags,
`-Xms10G -Xmx10G` for the CX43 VPS). It's idempotent and never deletes an existing `world*/` folder
or plugin data folder; it only ever adds or overwrites files it manages (jars, datapack zips,
config copies). Re-running it is safe and is how a later deploy will sync updates.

Both assembled folders live under `server/` itself, not under `server/build/`: `gradlew clean`
wipes `build/`, which would otherwise delete a locally booted world or Floodgate's `key.pem`.
Both are gitignored (`server/dist/`, `server/local/`).

## Local smoke boot

`.\gradlew.bat :server:assembleLocalServer` writes `server/local/` the same way, then
overrides `server.properties` for local testing only: `online-mode=false`, `white-list=false`,
`enforce-whitelist=false`, `server-ip=127.0.0.1`, RCON on with a random password generated once
(kept across re-assembles, never committed). These overrides never reach `assembleServer`'s output.

Boot it through the supervisor, never a bare background `java`:

```
powershell -File sv.ps1 ensure -Project mc-plugins-tag -Kind ephemeral -Root server\local -Cmd "java -Xms2G -Xmx4G -Dcom.mojang.eula.agree=true -jar paper-26.3-143.jar --nogui"
```

Watch with `sv.ps1 logs -Id local:java`, stop with an RCON `stop`, then `sv.ps1 rm -Id local:java`.

## Known issue: Geyser does not currently enable

Geyser-Spigot 2.11.3 build 1247 (the current pinned build) fails to enable on Paper 26.3 build 143
beta: `IllegalArgumentException: Couldn't find asBukkitCopy or asCraftMirror method on
CraftItemStack`, inside its shaded command-parser reflection (Paper 26.3 removed
`CraftItemStack#asBukkitCopy`/`#asCraftMirror`, and Geyser's shaded `cloud` command library hasn't
picked up the replacement `asBukkitMirror` method yet). This is an upstream Geyser/Paper-beta
compatibility gap, not a config problem here; Bedrock players cannot join until GeyserMC ships a
build that supports 26.3's beta internals. Floodgate loads fine but does nothing without Geyser.

Re-checked 2026-10-02: `https://download.geysermc.org/v2/projects/geyser/versions/latest/builds`
still reports build 1247 as latest (same sha256 as our pinned jar), so there is no newer build to
pin yet. Tracked upstream in GeyserMC/Geyser#6716 and #6730 (both closed without a shipped fix);
the actual fix, GeyserMC/Geyser#6712 ("Update to Java 26.3"), was still open and unmerged as of
this check. Re-run the latest-builds check periodically; once a build enables here, pin it in
`ServerArtifacts.kt` and let Geyser generate its own `plugins/Geyser-Spigot/config.yml` (replacing
the reconstructed one in `server/config/`, see that file's header).

## Secrets

`server/secrets.properties` (gitignored; copy `server/secrets.properties.example` to start) can
hold `discordsrv.token`. `injectSecrets` in `server/build.gradle.kts` writes it into the
**assembled** `plugins/DiscordSRV/config.yml` only (never into `server/config/`), so the real token
never reaches git. Without a token, DiscordSRV logs an error and stays disabled; everything else
works normally.

## First boot on the real server

1. Run `start.sh`/`start.bat` once, let every plugin finish enabling, `op <you>` in console.
2. Owner account: `lp user <you> parent set admin` (seeded groups: default/mod/admin, see
   `server/seed/plugins/LuckPerms/yaml-storage/groups/`), then `op <you>` too (LuckPerms grants
   `*` but some vanilla checks still look at op status).
3. Whitelist Java players: `whitelist add <name>` per player, then `whitelist reload`.
4. Bedrock players (once Geyser can enable, see "Known issue" above): `fwhitelist add
   <bedrockname>` per Floodgate player, after confirming Geyser bound its Bedrock port (log line
   `Started Geyser on 0.0.0.0:19132`).
5. Pre-generate terrain with Chunky (world names are still `world` / `world_nether` /
   `world_the_end` on Paper 26.3 despite the 26.1+ on-disk `world/dimensions/...` restructuring;
   not independently re-confirmed this session, see docs note below):
   ```
   chunky world world
   chunky radius 5000
   chunky start
   chunky world world_nether
   chunky radius 1250
   chunky start
   ```
6. Voice chat (optional, proximity chat): players install "Simple Voice Chat" for their client
   mod loader (Fabric/NeoForge) matching game version 26.3; the server plugin needs no client
   action to work for players without the mod, they simply can't hear/be heard.
7. Before any plugin update: back up the whole server folder. Update one plugin at a time, boot,
   confirm it enables, then move to the next.

## Deploy (not done yet)

Syncing `server/dist/` to the Hetzner VPS, systemd unit, firewall, and secrets provisioning
are a later task.
