# Kinetic Panel deploy notes (vs. the Hetzner VPS assumptions in docs/server.md)

Panel: https://kineticpanel.net, Pterodactyl-compatible client API, server identifier looked up at runtime by `serverIdentifier()` (first server on the API key).
Use `node server/deploy/kinetic.mjs <subcommand>` for everything below (reads the key from
`server/secrets.properties`, never prints it).

## claude-reply.mjs

`node server/deploy/claude-reply.mjs <player> <message...>` sends one ClaudeBridge reply into
chat, as a single `tellraw` built with `JSON.stringify` only (no click events or formatting from
the input). It imports `cmdConsole` from `kinetic.mjs` (exported for this) rather than
reimplementing the panel console call, so it goes through the same auth/error handling as
`kinetic.mjs cmd`. `<player>` must match `/^\.?[A-Za-z0-9_]{1,17}$/` (a Floodgate/Bedrock name
starts with `.`); `<message...>` is 1-400 characters. `--all` targets `@a` instead and drops the
`<player>` argument entirely (`claude-reply.mjs --all <message...>`). `--dry-run` prints the exact
console command instead of sending it - useful since it needs no live server or even a valid API
key reachable, other than importing `kinetic.mjs` itself (which still reads
`server/secrets.properties` at import time; a `--dry-run` run still needs that file to exist).
`kinetic.mjs`'s own CLI dispatch is guarded to only run on a direct `node kinetic.mjs ...`
invocation, not on this import, so importing it doesn't also run its own argv parsing against
claude-reply.mjs's arguments.

- **No root shell.** Kinetic exposes only SFTP (port 2022, user `<panel-username>.<server-id>`) and
  the client API; `server/deploy/apply-bundle.sh` assumes SSH root and cannot run there. Upload
  `mc-server-bundle.tar.gz` via SFTP to the server root, then extract with
  `kinetic.mjs decompress . mc-server-bundle.tar.gz` - confirmed working on the real upload
  (2026-10-03). Use `.` for the root argument, not `/`: under Git Bash on Windows, a bare `/` gets
  rewritten to the Git install path before Node sees it, which presents as a `DaemonConnectionException`
  500 that looks like a broken decompress endpoint. Run `kinetic.mjs ls .` first if that happens, to
  confirm the daemon connection itself is fine before suspecting the endpoint.
- **Jar name.** The bundle ships `paper.jar`, but the startup variable `SERVER_JARFILE` defaults to
  `server.jar` (Kinetic's own pre-installed jar). Set it before first boot:
  `kinetic.mjs var SERVER_JARFILE paper.jar`. Leave the old `server.jar` in place or delete it only
  after confirming the switch booted correctly, so there's a fallback.
- **seed/ merge is manual.** apply-bundle.sh's no-clobber seed/ copy has no Kinetic-side equivalent.
  Decompressing the tar.gz leaves `seed/plugins/LuckPerms/...` as its own subfolder. First deploy
  only (nothing to clobber yet): move its contents up into `plugins/...` by hand, then delete the
  now-empty `seed/` dir. On every later redeploy, skip the seed/ step entirely, it must never
  overwrite a live server's LuckPerms data. Confirmed working (2026-10-03): since
  `plugins/LuckPerms/yaml-storage` didn't exist yet, a single SFTP `rename seed/plugins/LuckPerms/yaml-storage
  plugins/LuckPerms/yaml-storage` moved the whole tree in one step, then `rmdir` the emptied
  `seed/plugins/LuckPerms`, `seed/plugins`, `seed` in that order, and `rm` the uploaded tarball.
- **server-port.** The committed `server.properties` still says `25565`; the real allocation is
  `25566`. Pterodactyl eggs are generally expected to inject `SERVER_PORT`/`SERVER_IP` env vars for
  the default allocation and rewrite `server.properties` on first boot, this egg advertises a
  `minecraft_firstime_setup` feature consistent with that, but **confirmed NOT the case on this
  panel (2026-10-03)**: after decompressing, `server.properties` still had `server-port=25565`.
  Fixed manually: SFTP-get the file, edit `server-port=25566` locally, SFTP-put it back, before
  `power start`. Do this every fresh deploy (a decompress always re-lays the committed file).
- **`prop` subcommand** replaces that manual SFTP round trip for single-key edits to the live
  `/server.properties`: `kinetic.mjs prop <key>` prints the current `key=value` line as-is;
  `kinetic.mjs prop <key> <value>` replaces only that line's value, failing loudly if the key
  doesn't exist (never appends, never touches any other line, preserves the file's exact line
  endings and trailing newline). Most properties are read once at startup, so a value change needs
  `kinetic.mjs power restart` to take effect. Java's `Properties` writer escapes a literal colon in
  a value as `\:`; `prop` prints that raw escaped line unchanged and writes a new value exactly as
  given (unescaped), since Paper accepts both forms on read.
- **`replace` subcommand** edits any other live file in place: `kinetic.mjs replace <file> <old> <new>`
  swaps one exact piece of text and refuses unless it occurs exactly once. From Git Bash, prefix
  `MSYS_NO_PATHCONV=1` so a `/plugins/...` path isn't rewritten to the Git install folder. First
  used 2026-10-06 for Towny's `town_mob_removal_ignored_spawn_causes` (cartel phone).
- **`restart` subcommand** does the whole live-restart dance in one call, under the restart-ASAP
  policy (2026-10-17): whenever a jar/config/datapack change is staged and ready, restart as soon
  as it's staged - don't hold off for an empty server or ask Joe about timing, that older pattern no
  longer applies. `kinetic.mjs restart "<reason>"` sends `restartcountdown start <token> <reason>`
  to the Tweaks plugin's console-only command, which runs a 120s countdown broadcast to players
  (warnings at 120/60/30/10/5/1s; the 120s and 60s warnings mention `/dont-restart`, which any
  online player can run to push the restart back up to an hour or until they disconnect - see
  `tweaks/src/main/java/dev/sirbepy/mc/tweaks/restart/`). kinetic.mjs then polls `/logs/latest.log`
  every 10 seconds, with no hard timeout while the plugin reports an active delay, for that
  countdown's `RESTART-GO <token>` marker (the plugin runs `save-all flush` itself once the
  countdown reaches zero with no delays active). Once that marker appears, kinetic.mjs sends
  `power restart`, then polls `/logs/latest.log` every 5 seconds (5-minute timeout) until it sees a
  fresh boot that reached the ready line, printing which plugins enabled and any ERROR lines from
  that boot.
  - **Fallback**: if `restartcountdown start` comes back as an unrecognised console command (Tweaks
    not loaded on the server), kinetic.mjs falls back to the old hand-rolled 60/30/10s `tellraw`
    countdown and its own `save-all flush`, so a restart is never blocked on the plugin existing.
    Folds in four sessions' hand-rolled countdown scripts from 2026-10-06
    (`.for_bepy/restart-countdown.mjs` and siblings), so nobody needs to write a new one-off
    `wait()`-chain script again.
  - **Fresh-boot detection**: Paper rotates `logs/latest.log` to a timestamped `.log.gz` on every
    boot and starts a new file, so the subcommand captures the pre-restart log's *first line*
    before sending `power restart`; any later poll whose first line differs is a fresh file. This
    is cheaper than diffing the whole file and doesn't get confused by log lines the still-running
    server appends between poll ticks during a slow shutdown.
  - **"Done" detection**: matches `]: Done (` specifically (the main thread's ready line has
    nothing between `]: ` and `Done (`), not `Done (` anywhere - a plugin can print its own early
    done line first (Geyser's `[Geyser-Spigot] Done (1.373s)! Run /geyser help for help!` showed up
    in a live sample before the main `Done (16.220s)! For help, type "help"`, though in practice
    both come after every plugin has already logged its `Enabling`).
  - **Plugin/error lines**: `Enabling <name>` is matched as `Enabling (\S+)` anywhere in the line
    (plugin lines are prefixed `[PluginName] Enabling PluginName vX.Y.Z`, so anchoring to `]: `
    directly would miss them); ERROR lines are matched on `/ERROR]:` (the level sits right after the
    thread name's `/`, e.g. `[Server thread/ERROR]:` or `[DiscordSRV - Initialization/ERROR]:`).
  - The parsing logic (`isFreshBootLog`, `parseBootLog`) is exported from `kinetic.mjs` so it can be
    exercised offline against real and synthetic log text without touching the live server.
  - **The Player Claude heads-up stays a manual step.** This subcommand only talks to the Kinetic
    panel API; it cannot post into Conductor, so whoever runs a restart still posts the
    heads-up there first, same as before. **Restarting itself no longer needs Joe's go**: the
    120s warned countdown plus `/dont-restart` is the safety net, so run `restart "<reason>"` as
    soon as a staged change is ready.
- **`upload` subcommand** puts one local file into a server directory through the panel's signed
  upload URL, no SFTP key needed: `kinetic.mjs upload server/local/plugins/carry-0.1.0.jar plugins`
  (overwrites a same-named file). Take plugin jars from `server/local/plugins/` after
  `:server:assembleLocalServer`, since `build/` is unreadable here. A brand-new plugin goes straight
  into `plugins/` (Bukkit's `plugins/update/` only replaces jars that already exist) and loads on
  the next restart; first used for Carry on 2026-10-05.
- **Bedrock + voice chat ports already fixed in git**, no Kinetic-side edit needed:
  - `server/config/plugins/Geyser-Spigot/config.yml` `bedrock.port` -> `25572` (allocation [42267],
    noted "geyser-bedrock"). `java.port`/`java.address` stay as-is, Geyser auto-follows Bukkit's port.
  - `server/config/plugins/voicechat/voicechat-server.properties` `port` -> `25604` (allocation
    [49220], noted "simple-voice-chat").
- **Stale plugin jar cleanup** (apply-bundle.sh moves jars no longer in the bundle aside) has no
  Kinetic equivalent yet. Not an issue for this first deploy (empty server); revisit before the
  first update that removes a plugin.

## Allocations on this server

| id    | port  | note               |
|-------|-------|---------------------|
| 42261 | 25566 | (default, Java)     |
| 42267 | 25572 | geyser-bedrock       |
| 49220 | 25604 | simple-voice-chat    |

## Subdomain

`sirbepy.mcserver.host` is an SRV record to `eu-de-p59-a6-cg.kineticpanel.net:25566`, created via
the panel API (2026-10-03): `POST /api/client/servers/{id}/subdomain` with body
`{"subdomain","domainId"}`. List existing subdomains with `GET` on the same path; list available
domains with `GET /api/client/servers/{id}/domains`. No `kinetic.mjs` subcommand for this yet - it
was a one-off `fetch` call, not added to the script since it's unlikely to be needed again. SRV
records are Java-only: Bedrock clients ignore them, so Bedrock/Geyser keeps using the plain
host:port (`eu-de-p59-a6-cg.kineticpanel.net:25572`). Verify propagation with
`nslookup -type=SRV _minecraft._tcp.sirbepy.mcserver.host 1.1.1.1`.
