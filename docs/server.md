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

`.\gradlew.bat :server:assembleServer` writes `server/dist/`: Paper 26.3 build 156, every
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
`enforce-whitelist=false`, `server-ip=127.0.0.1`, `server-port=25566`, RCON on port `25576` with a
random password generated once (kept across re-assembles, never committed). The non-default ports
let this local smoke server run alongside testserver's bot e2e server, which owns 25565/25575. It
also appends `advanced.bedrock.validate-bedrock-login: false` to the assembled copy of Geyser's
config, needed only so the unauthenticated e2e Bedrock test client (`e2e/bedrock.mjs`) can log in;
never applied to the real server. None of these overrides ever reach `assembleServer`'s output.

Boot it through the supervisor, never a bare background `java`:

```
powershell -File sv.ps1 ensure -Project mc-plugins-tag -Kind ephemeral -Root server\local -Cmd "java -Xms2G -Xmx4G -Dcom.mojang.eula.agree=true -jar paper-26.3-156.jar --nogui"
```

Watch with `sv.ps1 logs -Id local:java`, stop with an RCON `stop`, then `sv.ps1 rm -Id local:java`.

## Known issue: Geyser release builds don't enable yet (worked around with an official preview build)

Geyser-Spigot's release build (2.11.3 build 1247) fails to enable on Paper 26.3 build 143 beta:
`IllegalArgumentException: Couldn't find asBukkitCopy or asCraftMirror method on CraftItemStack`,
inside its shaded command-parser reflection (Paper 26.3 removed
`CraftItemStack#asBukkitCopy`/`#asCraftMirror`, and Geyser's shaded `cloud` command library hadn't
picked up the replacement `asBukkitMirror` method yet). This was an upstream Geyser/Paper-beta
compatibility gap, not a config problem here. The fix shipped as GeyserMC/Geyser#6712 ("Update to
Java 26.3"); as of 2026-10-02 it is still open/unmerged, but GeyserMC publishes official preview
builds of open PRs on its own download API under the `geyserpreview` project
(`https://download.geysermc.org/v2/projects/geyserpreview/versions/pr.6712/builds`). `ServerArtifacts.kt`
is pinned to that preview's build 3057 (resolved 2026-10-02) instead of a release build, so Geyser
now enables on server/local. Swap the pin back to a normal `geyser` release build once 26.3 support
ships there (check `https://download.geysermc.org/v2/projects/geyser/versions/latest/builds`
periodically), and once it does, let Geyser generate its own `plugins/Geyser-Spigot/config.yml`
(replacing the reconstructed one in `server/config/`, see that file's header).

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
4. Bedrock players: `fwhitelist add <bedrockname>` per Floodgate player, after confirming Geyser
   bound its Bedrock port (log line `Started Geyser on 0.0.0.0:19132`). Geyser is currently running
   off an official GeyserMC preview build, not a release build, see "Known issue" above.
   Bedrock checks only a real Bedrock client can do (the bots can't render forms or Geyser's GUI
   translation), once, with one of the Bedrock players:
   - `/nick` opens a "Nickname" text form; submitting a name changes chat, tab and the floating label.
   - `/color` opens the color chest GUI and a click applies the color.
   - Opening someone else's chest says to type `/safe-chests-one-time`; the owner (on Bedrock) is
     told to type `/safe-chests-pending`, which opens Allow once / 5 minutes / Always / Deny.
   - As IT, the sheep head shows on the player (Geyser bug #5795 can hide custom heads until
     re-equipped) and the IT prefix shows in chat and above the head.
5. Pre-generate terrain with Chunky (world names are still `world` / `world_nether` /
   `world_the_end` on Paper 26.3 despite the on-disk layout being one `world/` folder with
   `dimensions/minecraft/{overworld,the_nether,the_end}/` inside, confirmed 2026-10-03 against a
   real booted `server/local`):
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

## Deploy

Hetzner CX43 (16 GB/8 vCPU), Ubuntu 24.04, Nuremberg or Falkenstein. RCON stays off on the real
server, so there's no console after first boot: the owner op/whitelist/LuckPerms-admin step below
is done by `:server:seedPlayers` instead of typed commands.

**Owner, once, in the Hetzner panel:** create the CX43 (Ubuntu 24.04, Nuremberg/Falkenstein), add
the SSH key from `~/.ssh/id_ed25519.pub`, enable Hetzner Backups.

**Claude, once (provisioning):**
```
scp -r server/deploy root@<ip>:/root/deploy
ssh root@<ip> 'bash /root/deploy/provision.sh'
```

**Claude, every deploy (first time and every update):**
```
.\gradlew.bat :server:seedPlayers :server:deployBundle --no-daemon --console=plain
scp server/bundle/mc-server-bundle.tar.gz root@<ip>:/root/mc-server-bundle.tar.gz
ssh root@<ip> 'bash /opt/minecraft/bin/apply-bundle.sh /root/mc-server-bundle.tar.gz'
```
Back up first (`ssh root@<ip> '/opt/minecraft/bin/backup.sh pre-update'`), update one plugin's
pinned URL in `ServerArtifacts.kt` at a time, re-run the deploy, confirm it enables before the next.

**First boot:**
1. Edit `server/players.properties` (`admins=`, `players=`, comma-separated Java usernames),
   re-run `seedPlayers` + `deployBundle` + apply-bundle *before* the very first start: it writes
   `server/seed/{ops.json,whitelist.json,plugins/LuckPerms/yaml-storage/users/<uuid>.yml}` (admin
   parent group), which only ever fills in files that don't already exist on the server. Missed
   someone? `op <name>` / `whitelist add <name>` / `lp user <name> parent set admin` still work
   once you're in-game as an existing admin.
2. Bedrock players: `fwhitelist add <bedrockname>` in-game as admin, after confirming Geyser bound
   its Bedrock port (`journalctl -u minecraft -n 50`, look for `Started Geyser on 0.0.0.0:19132`).
3. Pre-generate terrain as admin in-game (see Chunky commands above).
4. Nightly backups run automatically (`mc-backup.timer`, 05:00 Europe/Zagreb, newest 3 kept); check
   `systemctl list-timers mc-backup.timer`.

## Kinetic (current host)

The Hetzner VPS plan above is the eventual target; the server actually runs on a rented Kinetic
Panel slot today (Pterodactyl-compatible, node EU-DE-P59, Germany). Deploy mechanics differ enough
from the Hetzner steps (no root shell, panel API instead) to need their own notes: see
`server/deploy/KINETIC.md` and `server/deploy/kinetic.mjs`.

- **Java**: `sirbepy.mcserver.host` (SRV record to `eu-de-p59-a6-cg.kineticpanel.net:25566`, no port
  needed once DNS has propagated; the bare hostname:25566 still works directly too).
- **Bedrock** (Geyser): `eu-de-p59-a6-cg.kineticpanel.net:25572` (SRV is Java-only; Bedrock ignores
  it, so this stays the plain host:port, no subdomain).
- **Voice chat** (Simple Voice Chat): port `25604`, same host
- **SFTP**: `eu-de-p59-a6-cg.kineticpanel.net:2022`, user `<panel-username>.dd499ae3`, key auth only
- The whitelist is off (Joe's call, 2026-10-03: anyone with the address can join; online-mode stays
  on). SirBepy is op and LuckPerms admin. To lock it down later, from the panel's console tab:
  `whitelist on`, `whitelist add <name>`, Bedrock players `fwhitelist add <gamertag>`.
- The subdomain was created via the panel API: `POST /api/client/servers/{id}/subdomain` with body
  `{"subdomain","domainId"}` (list existing: `GET` same path; available domains: `GET
  /api/client/servers/{id}/domains`). No `kinetic.mjs` subcommand for this yet, added ad hoc.

## First production deploy (2026-10-03)

Bundle built with `:server:deployBundle`, uploaded via SFTP to the server root as
`mc-server-bundle.tar.gz`, then extracted with `kinetic.mjs decompress . mc-server-bundle.tar.gz`
(the panel's decompress endpoint does support .tar.gz; the root argument must be `.`, not `/` - Git
Bash on Windows rewrites a bare `/` to the Git install path before Node ever sees it, which looked
like a dead decompress endpoint until `kinetic.mjs ls .` proved the daemon connection was fine).
`seed/plugins/LuckPerms/yaml-storage` moved up into `plugins/LuckPerms/yaml-storage` with a single
SFTP `rename` (no `plugins/LuckPerms/yaml-storage` existed yet to clobber), then the tarball and the
emptied `seed/` tree were deleted. `SERVER_JARFILE` set to `paper.jar` before first boot.

`server-port` was NOT rewritten by Pterodactyl on first boot - the uploaded `server.properties` still
said `25565` (the committed file's VPS-path value) after decompressing, so it needed a manual
SFTP-side fix to `25566` before `power start`. Confirmed boot: Paper 26.3 build 143, all 18 plugins
enabled (DiscordSRV self-disabled as expected, no bot token configured), Geyser bound UDP 25572,
voice chat bound 25604, all 16 datapacks + vanilla + paper loaded with no errors, world border
10000/2500, `players_sleeping_percentage` 50, `fire_spread_radius_around_player` 0. External pings
(Java via `minecraft-protocol`, Bedrock via `bedrock-protocol`) both succeeded from this PC.
Overworld/Nether pregen (`chunky`, square, center 0 0, radius 5000/1250) started via console
commands; nether finished in 8:40 (25,281 chunks), overworld left running.
