# Kinetic Panel deploy notes (vs. the Hetzner VPS assumptions in docs/server.md)

Panel: https://kineticpanel.net, Pterodactyl-compatible client API, server identifier `dd499ae3`.
Use `node server/deploy/kinetic.mjs <subcommand>` for everything below (reads the key from
`server/secrets.properties`, never prints it).

- **No root shell.** Kinetic exposes only SFTP (port 2022, user `<panel-username>.dd499ae3`) and
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
