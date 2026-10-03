# Kinetic Panel deploy notes (vs. the Hetzner VPS assumptions in docs/server.md)

Panel: https://kineticpanel.net, Pterodactyl-compatible client API, server identifier `dd499ae3`.
Use `node server/deploy/kinetic.mjs <subcommand>` for everything below (reads the key from
`server/secrets.properties`, never prints it).

- **No root shell.** Kinetic exposes only SFTP (port 2022, user `<panel-username>.dd499ae3`) and
  the client API; `server/deploy/apply-bundle.sh` assumes SSH root and cannot run there. Upload
  `mc-server-bundle.tar.gz` via SFTP to the server root, then extract with
  `kinetic.mjs decompress <dir> mc-server-bundle.tar.gz` (Pterodactyl's decompress endpoint
  supports .tar.gz; not yet tested against this specific fork, confirm on the real upload).
- **Jar name.** The bundle ships `paper.jar`, but the startup variable `SERVER_JARFILE` defaults to
  `server.jar` (Kinetic's own pre-installed jar). Set it before first boot:
  `kinetic.mjs var SERVER_JARFILE paper.jar`. Leave the old `server.jar` in place or delete it only
  after confirming the switch booted correctly, so there's a fallback.
- **seed/ merge is manual.** apply-bundle.sh's no-clobber seed/ copy has no Kinetic-side equivalent.
  Decompressing the tar.gz leaves `seed/plugins/LuckPerms/...` as its own subfolder. First deploy
  only (nothing to clobber yet): move its contents up into `plugins/...` by hand, then delete the
  now-empty `seed/` dir. On every later redeploy, skip the seed/ step entirely, it must never
  overwrite a live server's LuckPerms data.
- **server-port.** The committed `server.properties` still says `25565`; the real allocation is
  `25566`. Pterodactyl eggs are generally expected to inject `SERVER_PORT`/`SERVER_IP` env vars for
  the default allocation and rewrite `server.properties` on first boot, this egg advertises a
  `minecraft_firstime_setup` feature consistent with that, but it was not directly verified here.
  Check `server.properties` and the boot log after the very first start; fix manually if it didn't.
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
