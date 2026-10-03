# client/

Everything a player needs to join the friends server. Hand players `SETUP.txt` (plain text, no
markdown) and, for voice chat, `Los-Pollos-MineHermanos-26.3.mrpack`.

- `SETUP.txt`: joining, optional voice chat, datapack textures, optional shaders, dynamic
  lights, optional Sodium, Bedrock, troubleshooting.
- `Los-Pollos-MineHermanos-26.3.mrpack`: Modrinth modpack (Modrinth App or Prism Launcher import).
  Contains `modrinth.index.json` plus an `overrides/` folder (currently just
  `config/iris.properties`); the launcher downloads the jars/shaderpack from Modrinth's CDN,
  checks their hashes, and copies `overrides/` straight into the instance folder.
- `mrpack/modrinth.index.json`: the pack's source of truth (pinned URLs, sizes, sha1, sha512).
- `mrpack/overrides/`: files copied verbatim into the instance folder on import, same relative
  path. Currently `config/iris.properties`, see "Shaders ship off by default" below.
- `mrpack/build.py`: re-downloads every pinned file, verifies size + both hashes, zips the index
  and `overrides/` into the `.mrpack`. Run `python client/mrpack/build.py` after changing a pin.

Ports in `SETUP.txt` (Bedrock 25572, voice UDP 25604) are the Kinetic Hosting panel's
allocations, see `docs/server.md` "Kinetic (current host)". `sirbepy.mcserver.host` is Kinetic's
subdomain with an SRV record to `eu-de-p59-a6-cg.kineticpanel.net:25566` (checked with nslookup
2026-10-03); Bedrock ignores SRV, so it keeps the raw host + port.

## Pack contents (pinned 2026-10-03)

| Mod | Version (Modrinth version id) | Channel | Why |
| --- | --- | --- | --- |
| Fabric Loader | 0.19.5 (stable, meta.fabricmc.net for 26.3) | stable | mod loader |
| Fabric API | 0.161.0+26.3 (`bNnaTiuM`) | release | standard Fabric library |
| Simple Voice Chat | fabric-2.6.24+26.3 (`OLnMVWXy`) | beta | matches server plugin `voicechat-bukkit-2.6.24` |
| Sodium | 0.9.2+mc26.3 (`bAZQdGpg`) | release | FPS; also the exact version Iris 1.11.7 pins as its required Sodium |
| Reg's More Foods | 1.5.2+26.3+mod (`ebuLruaV`) | release | food textures for the server's RMF datapack |
| Dungeons and Taverns | 6.0.2 fabric (`WilF7nqU`) | release | key textures for the server's D&T datapack |
| Iris Shaders | 1.11.7+26.3-fabric (`vTN4NRGW`) | release | shader loader; ships OFF by default, see below |
| Complementary Reimagined | r5.9.3 (`Bqen1mJX`) | release | the shader pack Iris preselects (still OFF by default) |
| LambDynamicLights | 4.13.0+26.3 (`rylKnJ4M`) | release | held/dropped light-emitting items light up the area |
| Entity Culling | 1.11.2 (`F4loCvYt`) | release | skips rendering entities fully hidden behind blocks; FPS |
| ImmediatelyFast | 1.17.1+26.3 (`3MP9UR23`) | release | faster item/text rendering; author benchmarks show it still helps with Iris+Sodium active |
| FerriteCore | 9.0.0-fabric (`d5ddUdiB`) | release | reduces client RAM usage |
| Lithium | 0.26.2+mc26.3 (`xS0Q8LSi`) | release | general game-logic optimizations; same org (CaffeineMC) as Sodium |
| Continuity | 3.0.1+26.3 (`Jt2sCWTQ`) | release | lets resource packs use connected textures (glass, bookshelves, etc.) with Sodium |
| Text Placeholder API | 3.2.0+26.3 (`lXytLqWj`) | release | required dependency of Mod Menu |
| Mod Menu | 21.0.0 (`kyy7dbrZ`) | release | in-game screen to see/configure installed mods |
| AppleSkin | 3.0.10+mc26.3 (`PHjDtQay`) | release | shows food/saturation/exhaustion values in the HUD |
| Mouse Tweaks | 2.31 (`LeMuMhLv`) | release | drag-to-move/drag-to-split items across inventory slots |
| Jade | 26.3.5 (`71CTWqdE`) | release | hover overlay with block/entity info |

The RMF/D&T pair are the projects' own "+mod" jars: no classes, just `data/` + `assets/` wrapped
as a Fabric mod, so Fabric loads their assets as an always-on built-in resource pack (their
`data/` is ignored on a remote server). Both projects are All Rights Reserved, so the pack only
references their Modrinth CDN files and never bundles the resource pack zips under `overrides/`;
the RMF resource pack itself only exists inside the "UNZIP ME" bundle, which an mrpack file entry
can't point into. RMF jar `assets/` = the bundle's resource pack, all 1709 files byte-identical
(sha1 compared 2026-10-03). Version numbers match the server's datapacks
(`server/datapacks/README.md`).

### Shaders ship off by default

Iris requires exactly Sodium `bAZQdGpg` (0.9.2+mc26.3) per its version dependency list, so no
Sodium bump was needed. `mrpack/overrides/config/iris.properties` ships with:

```
enableShaders=false
shaderPack=ComplementaryReimagined_r5.9.3.zip
```

Key names and defaults confirmed from Iris source, `26.3` branch of github.com/IrisShaders/Iris:
- `enableShaders` / `shaderPack`: `common/.../iris/config/IrisConfig.java` (`load()`/`save()`).
  `enableShaders` only defaults to true if the key is *missing*; explicit `false` is required to
  ship shaders off.
- Properties file path (`config/iris.properties`, hence the override path): `Iris.java` line 827,
  `getConfigDir().resolve("iris.properties")`.
- Default keybinds, `Iris.java` lines 811-814: `I` opens the shader pack selection screen, `K`
  toggles shaders on/off instantly. Both rebindable in Options > Controls > Key Binds.

Complementary Reimagined's license (`Complementary License Agreement 1.7`, section 1.2) explicitly
permits adding it to a modpack through "Modrinth or CurseForge's existing systems" (i.e. an mrpack
file entry pointing at the CDN, not a direct-upload bundle) provided it isn't modified and, if
enabled by default, is credited in the modpack's description. It ships OFF by default here, so
that credit condition doesn't apply, but it's credited above anyway.

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
- **Iris Shaders**: project `YL57xq9U` (slug `iris`), team owner `coderbot`, lead dev `IMS`,
  source github.com/IrisShaders/Iris, license LGPL-3.0-only, ~183M downloads.
- **Complementary Reimagined**: project `HVnmMxH1` (slug `complementary-reimagined`), owner
  `EminGT`, source github.com/ComplementaryDevelopment/ComplementaryReimagined, ~68M downloads.
  Shader pack (zip of shader source, no `.class`/`.jar`); loader is `iris`, not `fabric`.
- **LambDynamicLights**: project `yBW8D80W` (slug `lambdynamiclights`), owner `LambdAurora`,
  source github.com/LambdAurora/LambDynamicLights, ~60M downloads.
- **Entity Culling**: project `NNAgCjsB` (slug `entityculling`), source
  github.com/tr7zw/EntityCulling, ~174M downloads. No declared incompatibilities on Modrinth; no
  mention of LambDynamicLights in its description either (checked 2026-10-03).
- **ImmediatelyFast**: project `5ZwdcRci` (slug `immediatelyfast`), source
  github.com/RaphiMC/ImmediatelyFast, ~127M downloads. Its own description benchmarks it running
  with "Iris and Sodium" active (60 -> 76 FPS), so it's tested against that combo already in the
  pack.
- **FerriteCore**: project `uXXizFIs` (slug `ferrite-core`), source
  github.com/malte0811/FerriteCore, ~155M downloads. No declared dependencies or incompatibilities.
- **Lithium**: project `gvQqBUqZ` (slug `lithium`), source github.com/CaffeineMC/lithium-fabric,
  ~131M downloads. Same org (CaffeineMC) as Sodium, so the two are maintained together.
- **Continuity**: project `1IjD5062` (slug `continuity`), source github.com/PepperCode1/Continuity,
  ~75M downloads. Client-side only, built to supply connected textures alongside Sodium (the
  Sodium team recommends it for that purpose); requires Fabric API (already in the pack).
- **Text Placeholder API**: project `eXts2L7r` (slug `placeholder-api`), source
  github.com/Patbox/TextPlaceholderAPI, ~67M downloads. Pulled in only because Mod Menu 21.0.0
  lists it as a required dependency.
- **Mod Menu**: project `mOgUt4GM` (slug `modmenu`), source github.com/TerraformersMC/ModMenu,
  ~149M downloads. Required deps: Fabric API (already in pack) and Text Placeholder API (added
  above).
- **AppleSkin**: project `EsAfCjCV` (slug `appleskin`), source github.com/squeek502/AppleSkin,
  ~92M downloads. Requires Fabric API (already in the pack).
- **Mouse Tweaks**: project `aC3cM3Vq` (slug `mouse-tweaks`), source
  github.com/YaLTeR/MouseTweaks, ~60M downloads. No declared dependencies or incompatibilities.
- **Jade**: project `nvQzSEkH` (slug `jade`), source github.com/Snownee/Jade, ~69M downloads.
  Requires Fabric API (already in the pack); JEI is only an optional dependency, not pulled in.
- All are the official projects under their canonical slugs (not forks or look-alikes), and each
  has a build whose only game version is `26.3`.
- `build.py` confirmed each CDN file matches the pinned size, sha1 and sha512.
- No advisory database covers Minecraft mods (RustSec/npm/OSV don't index Modrinth), so the check
  is provenance-based: official project, real author, pinned hashes.
