# client/

Everything a player needs to join the friends server. Hand players `SETUP.txt` (plain text, no
markdown) and, for voice chat, `SirBepy-Friends-Server-26.3.mrpack`.

- `SETUP.txt`: joining, optional voice chat, datapack textures, optional Sodium, Bedrock,
  troubleshooting.
- `SirBepy-Friends-Server-26.3.mrpack`: Modrinth modpack (Modrinth App or Prism Launcher import).
  Contains only `modrinth.index.json`; the launcher downloads the jars from Modrinth's CDN and
  checks their hashes.
- `mrpack/modrinth.index.json`: the pack's source of truth (pinned URLs, sizes, sha1, sha512).
- `mrpack/build.py`: re-downloads every pinned file, verifies size + both hashes, then zips the
  index into the `.mrpack`. Run `python client/mrpack/build.py` after changing a pin.

Ports in `SETUP.txt` (Bedrock 25572, voice UDP 25604) are the Kinetic Hosting panel's
allocations, see `docs/server.md` "Kinetic (current host)".

## Pack contents (pinned 2026-10-03)

| Mod | Version (Modrinth version id) | Channel | Why |
| --- | --- | --- | --- |
| Fabric Loader | 0.19.5 (stable, meta.fabricmc.net for 26.3) | stable | mod loader |
| Fabric API | 0.161.0+26.3 (`bNnaTiuM`) | release | standard Fabric library |
| Simple Voice Chat | fabric-2.6.24+26.3 (`OLnMVWXy`) | beta | matches server plugin `voicechat-bukkit-2.6.24` |
| Sodium | 0.9.2+mc26.3 (`bAZQdGpg`) | release | FPS; chosen over the newer 0.9.3-alpha.1 |
| Reg's More Foods | 1.5.2+26.3+mod (`ebuLruaV`) | release | food textures for the server's RMF datapack |
| Dungeons and Taverns | 6.0.2 fabric (`WilF7nqU`) | release | key textures for the server's D&T datapack |

The last two are the projects' own "+mod" jars: no classes, just `data/` + `assets/` wrapped as a
Fabric mod, so Fabric loads their assets as an always-on built-in resource pack (their `data/` is
ignored on a remote server). Both projects are All Rights Reserved, so the pack only references
their Modrinth CDN files and never bundles the resource pack zips under `overrides/`; the RMF
resource pack itself only exists inside the "UNZIP ME" bundle, which an mrpack file entry can't
point into. RMF jar `assets/` = the bundle's resource pack, all 1709 files byte-identical (sha1
compared 2026-10-03). Version numbers match the server's datapacks (`server/datapacks/README.md`).

## Safety check (2026-10-03)

Checked through the Modrinth API (`/v2/project`, `/v2/team/<id>/members`, `/v2/project/<id>/version`):

- **Simple Voice Chat**: project `9eGKb6K1` (the same project id the server plugin pin in
  `buildSrc/src/main/kotlin/ServerArtifacts.kt` uses), owner `henkelmax`, source
  github.com/henkelmax/simple-voice-chat, ~71M downloads. Every SVC build for 26.3 is tagged
  "beta" on Modrinth, including the server plugin already pinned; 2.6.24 is the newest.
- **Fabric API**: project `P7dR8mSH`, owner `modmuss50` (FabricMC), source github.com/FabricMC/fabric,
  ~267M downloads.
- **Sodium**: project `AANobbMI`, team `jellysquid3` (project lead), `IMS`, `douira`, source
  github.com/CaffeineMC/sodium, ~236M downloads.
- **Reg's More Foods**: project `2jidfU3A`, owner `regfunkid`, source github.com/regfunkid/reg-more-foods,
  the same project the server datapack comes from. Jar has 0 `.class` files.
- **Dungeons and Taverns**: project `tpehi7ww`, owner `NovaWostra`, ~21M downloads, the same project
  as the server's pinned datapack in `ServerArtifacts.kt`. Jar has 0 `.class` files.
- All are the official projects under their canonical slugs (not forks or look-alikes), and each
  has a build whose only game version is `26.3`.
- `build.py` confirmed each CDN file matches the pinned size, sha1 and sha512.
- No advisory database covers Minecraft mods (RustSec/npm/OSV don't index Modrinth), so the check
  is provenance-based: official project, real author, pinned hashes.
