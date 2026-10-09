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

## Warps (2026-10-09)

Players have HuskHomes `/warp <name>` and `/warplist` (Joe's call, 2026-10-09): the default group
grants `huskhomes.command.warp` and `huskhomes.command.warplist`. HuskHomes' other extras (`/rtp`,
`/phome`, `/phomelist`, home privacy) stay switched off for default, see the comment in
`server/seed/plugins/LuckPerms/yaml-storage/groups/default.yml`. The warps themselves are HuskHomes
data on the live server, not in the repo: `/warp guild` (set by ImMijo, 2026-10-09) leads to world
4472 226 551, in front of his Adventurer's Guild hall. The seed group files only reach a fresh server;
live got the two nodes from the console (`lp group default permission set huskhomes.command.warp true`,
same for `warplist`).

## Anti-xray (2026-10-09)

Paper's built-in anti-xray, no plugin, engine-mode 3 (Joe's pick: fake ores like mode 2, at about
half its join bandwidth). The Overworld settings live in `server/config/config/paper-world-defaults.yml`
(ores hidden below y 64), the Nether's in `server/config/world/dimensions/minecraft/the_nether/paper-world.yml`
(debris, quartz and gold up to y 128), and the End's override turns it off. All three are Paper's own
recommended lists. Applies from the next restart after the files are uploaded; there is no reload.

## Loyalty tridents come back from the void (Joe, 2026-10-09)

Bedrock parity, Tweaks plugin, `VoidTridentListener` (switch: `void-tridents.enabled` in Tweaks'
`config.yml`). On Java (26.3, checked in the server jar and on a private e2e server) Loyalty only pulls
a trident home after it hit something or landed, so one thrown into the void falls until Paper
deletes it 64 blocks under the world's floor. Now:

- A thrown Loyalty trident that drops below the world's min height (y 0 in the End) is stopped and
  marked as having hit, and vanilla Loyalty flies it back to the thrower like any returning trident.
- If the void still takes one (owner in another world, dead, or the trident was thrown before the
  last restart), the exact item (name, enchantments, durability, every component) goes straight into
  the owner's inventory, or at their feet if it is full. A dead owner gets it on respawn, an offline
  one on joining, unless the server restarts first (kept in memory only).
- Tridents without Loyalty, and creative throws (the inventory kept the trident), stay vanilla.
- Bedrock players behave the same: it all happens on the server. e2e: `e2e/void-tridents.mjs`.

## The Wither fight (Joe, 2026-10-09)

Two parts. The fight itself is the **Wither's Wrath** datapack (`server/datapacks/withers-wrath`, ported
to 26.3, see its `PORTED.md`): 600 HP, armor, charge/dash/homing-skull phases, a blaze and skeleton
archer burst at the midpoint, a trophy. It lets only one Wither live at a time and keeps the Wither's
own block damage on. Its settings menu is `/function wither:config` (op).

The anti-cheese is Tweaks' `WitherGuardListener` (switch: `wither-guard.enabled` in Tweaks'
`config.yml`):

- **Where it can be summoned**: building a Wither (by hand or with a dispenser) is refused inside any
  Towny town (Towny blocks the Wither's block damage there, so an obsidian box would hold it), above
  the Nether roof (y 128 and up), and when a block the Wither can't break (bedrock, barrier, reinforced
  deepslate, end portal frame: the `wither_immune` tag) is within 4 blocks sideways and 0 to 7 blocks
  up of where it would appear. The skulls and soul sand stay put, and players within 16 blocks get a
  red chat line saying why.
- **Blasts**: beds and respawn anchors (damage type `bad_respawn_point`), primed TNT, TNT minecarts and
  End Crystals do no damage to a Wither. Weapons, arrows, potions and everything else are vanilla.
- **Unstick** (checked once a second): a Wither whose body is inside a `wither_immune` block, or one
  that hasn't had line of sight to any survival/adventure player within 64 blocks for 10 checks in a
  row, teleports onto the nearest such player (3 blocks above them, lower under a ceiling) and
  targets them. Nobody within 64 blocks: nothing happens. Its summoning phase, and any time it is
  Invulnerable (Wither's Wrath's midpoint cutscene), don't count.
- e2e: `e2e/wither-guard.mjs` on the 26.2 e2e server (dispenser-built summons in the wilderness, under
  bedrock and in a real Towny town; anchor/TNT/crystal blasts against a player hit; both unstick
  cases). The datapack was boot-checked on a 26.3 test server; its fight was not played there (bots
  can't join 26.3).

## Shenron's Earthbreaker (dvolic21's Shenron wish, 2026-10-09)

Granted by Shenron (Overworld set, session `gcg0zu`, council 5-0 for a deal, 4-1 for 5x5). The pickaxe
was given by console: netherite, named "Shenron's Earthbreaker", Efficiency V, Unbreaking III, Fortune
III, Mending, and the PDC tag `claudesmp:earthbreaker` (byte 1, plus `claudesmp:earthbreaker_owner`).
Tweaks plugin, `EarthbreakerListener` (switch: `earthbreaker.enabled` in Tweaks' `config.yml`) knows it
by the tag alone, never the name, and it works for whoever holds it.

- **5x5**: breaking a block also breaks the 24 around it in the plane of the face that was dug (the face
  comes from the dig start, or from where the player looks).
- **Stone and ores only** (`AREA_MINEABLE`): stone, cobblestone, deepslate, cobbled deepslate, andesite,
  diorite, granite, tuff, calcite, netherrack, blackstone, basalt, smooth basalt, end stone, terracotta
  (plain and the 16 dyed, not glazed), and every ore incl. deepslate, nether gold and quartz. The area
  only starts when the broken block is one of those or ancient debris; debris itself is never taken as a
  neighbour. Dirt, logs, bricks, planks, glass, containers, spawners, bedrock and the rest stay.
- **Each neighbour is a real player break** (`Player.breakBlock`): Towny, MineGuard, CoreProtect and
  AuraSkills see an ordinary BlockBreakEvent, and a block the player couldn't break by hand (someone
  else's town) is skipped. Drops get Fortune, ores drop XP, and each block wears the pickaxe 1 point
  through Unbreaking, like vanilla. A pickaxe that breaks mid-area stops there.
- **Slower**: while it is in the main hand and the player isn't sneaking, a transient
  `block_break_speed` x0.25 modifier is on (never saved with the player). Deepslate goes from 3 ticks a
  block to 11, stone from 2 to 6; with the 5-tick pause between blocks that is about twice as long a
  swing, for up to 25 blocks.
- **Sneaking** mines one block at full speed.
- e2e: `e2e/earthbreaker.mjs` (dig speed, the 5x5 with ore drops and wear, sneaking, a real Towny town).

## Phantoms (CoffiebagSupreme's Shenron wish, 2026-10-07)

Granted by a 4-1 council vote in a toned-down, server-wide form, then extended by Joe the same night.

- **Trigger: 2 sleepless nights** instead of vanilla's 3: `player-insomnia-start-ticks: 48000` (was
  72000) in `server/config/config/paper-world-defaults.yml`. As in vanilla it is a chance that grows
  with the time since the player last slept, so they can come any time after that.
- **Spawn rate 2x**: `phantoms-spawn-attempt-min-seconds: 30` / `max-seconds: 59` (vanilla 60/119),
  so each player gets a spawn attempt twice as often.
- **Tweaks plugin, `PhantomBuffListener`** (switch: `phantoms.enabled` in Tweaks' `config.yml`):
  - Every phantom is **size 3** (drawn 1.45x larger), with its bite kept at vanilla's 6 damage (size
    alone would make it 9), **fireproof** (no burning in daylight, infinite hidden Fire Resistance).
  - Half are **venomous**: a hit gives **Poison I for 5 s** (about 2 hearts; poison can't kill), and
    they trail dark purple swirls from an infinite Nausea effect, which does nothing to a mob. 26.3
    has no per-mob texture, so the swirls are the only way to tell them apart, on Bedrock too.
  - The other half are **tough**: 30 HP instead of 20, no poison.
  - **Faster**: phantom speed is hardcoded in its move controller (no attribute changes it), so a
    1-tick task recovers the controller's target velocity from the last tick and adds a quarter of
    it, which doubles the target. Measured on server/local (6 circling phantoms, 20 s): average
    5.6 -> 7.9 blocks/s, top 0.5 s speed about 10 -> 14-18. The controller only speeds up while it
    faces its target, so circling stays under 2x; a straight dive should get closer to 2x but needs a
    real player to measure. Joe accepted the ~1.4x circling on 2026-10-07. True 2x circling is out
    of reach for any speed-only change: the controller turns at most 4 degrees a tick (a constant in
    `Phantom$PhantomMoveControl.tick`), so a faster phantom overshoots its circle, reads as
    misaligned and slows itself. Driving the controller's private `speed` field to 2x by reflection
    measured only 1.29x (5.94 -> 7.68 blocks/s), so it was not shipped.
  - **Heard from 32 blocks** instead of 16: the server pack stretches every phantom sound's range
    (`server/resourcepacks/claudesmp-items/README.md`, "Phantom sounds"), and the plugin replays the
    screech for players 16-32 blocks away, since the server only sends it within 16.
  - Cost per tick: one velocity read and write per loaded phantom; the screech check runs once a
    second.

## Sea gifts (ItsLogiks' and SirBepy's Porunga wishes, 2026-10-08)

Granted by Porunga (Overworld sets): permanent Dolphin's Grace and Conduit Power on each owner, matched
by UUID in `sea-gifts.owners` in Tweaks' `config.yml`. ItsLogiks won them in session `jam0yw` (no Night
Vision: it was in the pre-ruled bundle and he didn't take it); SirBepy won the same gifts on the same
terms in session `ay4gtv`. A single `sea-gifts.owner` key from before the list (live's config has one,
ItsLogiks) still counts as an owner beside the list.

- `/seagifts` shows both switches; `/seagifts dolphin` and `/seagifts conduit` flip one. Only owners
  see the command, and each switches only his own gifts. Each gift flips at most once per real hour,
  counted per owner and per gift; a refusal says how long is left.
- While a gift is on, Tweaks keeps it on him as an infinite level I effect without particles: on join,
  every second, and milk can't clear it.
- Death switches both off and they stay off until he flips them back. That first flip after a death is
  never refused by the hour (death isn't his flip), but it starts a new hour.
- Switches and cooldowns are in `plugins/Tweaks/sea-gifts.yml`, per owner, so a restart keeps them.
  With no record both gifts start on, which carries over the console's interim `effect give ...
  infinite` at a grant. The console's effect keeps its particles until it is cleared
  (`minecraft:effect clear <player> minecraft:dolphins_grace`, same for `conduit_power`); Tweaks puts
  its own particle-free one back within a second.
- `seagifts add <player|uuid>` and `seagifts remove <player|uuid>` (console or op) edit the owner list
  and write it to `sea-gifts.owners`, folding in the old single key. Removing an owner leaves any effect
  that is on him; clear it by hand. The e2e suite (`e2e/sea-gifts.mjs`) adds two bots and removes them
  after.

## Nether wishes (CoffiebagSupreme's Porunga wishes, 2026-10-08)

Granted by Porunga (Nether set, session `n97wtc`), all three for CoffiebagSupreme alone
(`nether-wishes.owner` in Tweaks' `config.yml`, matched by UUID; switch: `nether-wishes.enabled`). No
command for him: all three are always on.

- **Auto-replant** (`AutoReplant`): a fully grown wheat, carrot, potato, beetroot, nether wart or cocoa he
  breaks replants itself (cocoa on the same side of the log) and uses up one matching plain seed from
  anywhere in his inventory, offhand included. No seed, an unripe crop, or the farmland/soul sand/log gone:
  nothing is planted. No mana. It runs 6 ticks after the break, after AuraSkills' Replenish (Farming 6,
  free replant 4 ticks after the break, `replant_delay` in `mana_abilities.yml`), so a crop Replenish put
  back is left alone and costs no seed: the two never double-plant or eat two seeds. A renamed or custom
  seed item is never used.
- **Zombie truce** (`ZombieTruce`): zombies, zombie villagers, husks and drowned never target him. One he
  hurts (melee, or an arrow, trident or other projectile he shot) may fight him for **5 minutes after his
  latest hit**, or until it dies; the grudge is a timestamp in the mob's data, so it survives chunk unloads
  and restarts. Only the one he hit: the reinforcements a zombie calls and the zombies it alerts hold no
  grudge. Every second a covered mob still chasing him without a grudge is made to drop him (a grudge that
  ran out mid-chase). Mobs a plugin spawned (spawn reason `CUSTOM`, kept by Paper across restarts) are
  outside the truce, including the cartel's Salamanca cousins. Zombified piglins are not covered.
- **Hearty food** (`HeartyFood`): food he makes restores twice the hunger and twice the saturation for
  whoever eats it. The item's own food component is doubled, so effects are untouched (golden apple,
  suspicious stew, rotten flesh's Hunger). Marked food carries a gold "Made by CoffiebagSupreme: twice
  as filling" line and does not stack with plain food of the same kind. What counts as his:
  - a crafting result he takes (bread, stews, pies, cookies, cake, golden apples...);
  - a furnace or smoker result he takes out of the output slot by hand, whoever filled it; what a hopper
    pulls out is plain, and whatever a click leaves in the slot goes back to plain so the furnace keeps
    stacking;
  - campfire food whose raw item he put on that campfire (counted per item type under the campfire's spot
    in the chunk's data; placing any block on the spot forgets it);
  - a cake he crafted: its block remembers it when placed, and every slice gives anyone 4 hunger and
    0.8 saturation instead of 2 and 0.4.
  Food he buys, picks up or is given is never marked.
- `netherwishes owner <player|uuid>` (console or op) moves all three; the e2e suite
  (`e2e/nether-wishes.mjs`) borrows them for a bot and hands them back to CoffiebagSupreme.
