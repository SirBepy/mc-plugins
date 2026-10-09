# MC plugins: build plan and decisions

Friends-only Paper server. Mostly Java players; 2 Bedrock players join through Geyser + Floodgate, and the custom plugins treat Floodgate as an optional soft dependency (Bedrock players get forms or typed-command hints where Java players get clickable chat). Nothing paid, ever.

## Platform

- Server: **Paper 26.3** (beta channel at time of writing, Minecraft 26.3 "Wilderness Bound" released 2026-09-15). Chosen over 26.2 because new biomes only generate in chunks created on 26.3 and the Stardust Labs worldgen datapacks already target 26.3.
- Plugins compile against **paper-api 26.2** (latest stable API, matches MockBukkit `mockbukkit-v26.2`). The Bukkit API is forward compatible, so 26.2-compiled jars run on 26.3.
- Java 25 toolchain (Temurin 25 installed at `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`).
- Gradle 9.8 wrapper, Kotlin DSL, one multi-module build. Shared build logic lives in `buildSrc` convention plugins.
- Plain `plugin.yml` (not the experimental `paper-plugin.yml`). Commands are registered with Brigadier through `getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, ...)`, never in `plugin.yml`.
- All player-facing text is Adventure `Component`, built from MiniMessage strings in each plugin's `config.yml` so the owner can restyle every message. Never the deprecated `sendMessage(String)` or `ChatColor`.
- Every plugin has `/<plugin> reload` that re-reads config without a restart.
- Base package: `dev.sirbepy.mc.<module>`.

## Repo layout

```
core/         shared library (shaded + relocated into each plugin, not a plugin itself)
names/        Names plugin
safechests/   SafeChests plugin (add-on for Bolt)
tag/          Tag plugin
testserver/   run-paper config: boots a local Paper 26.3 server with every plugin + datapack
server/       assembles the real friends-server folder (see docs/server.md)
docs/         this plan
```

`testserver/run/` is the local test server's directory (gitignored). `./gradlew :testserver:runServer` builds every plugin and boots it.

## Third-party plugins on the test server

Downloaded by `testserver/build.gradle.kts` (`downloadPlugins { modrinth(...) }`), never committed:

- LuckPerms (permissions; `/lp editor` for the web UI)
- HuskHomes (`/tpa`, `/tpaccept`, `/sethome`, `/home`, `/back`, `/warp`, `/warplist`)
- PvPManager (per-player `/pvp` toggle with cooldown)
- Bolt (chest protection, SafeChests builds on its API)
- PacketEvents (Names uses it for the floating nametag)
- DiscordSRV (needs a bot token from the owner, stays unconfigured until then)
- Shops: QuickShop-Hikari + VaultUnlocked + EssentialsX (EssentialsX only for its economy; its own `/home`, `/tpa`, `/back`, `/nick` etc must be disabled so HuskHomes and Names own those commands)
- ViaVersion + ViaBackwards (lets slightly older clients and test bots join)
- Towny (land claims; a town's land grows with its residents, `town_block_ratio` 8 chunks each; on top of that a mayor can buy up to 64 bonus chunks per town with `/t buy bonus <n>` from the town bank at a flat $25 each). Config is versioned in `server/config/plugins/Towny/settings/config.yml` and copied to the test servers too: towns are free (creation, claims, outposts, upkeep, nations, death fee all 0) and the wilderness stays open to build, break and use. Tag still works inside towns with PvP off; a blocked tag (tagback, opted out) is a normal PvP hit, so it can combat-tag both players wherever PvP is on.

Datapacks (Stardust Labs, Modrinth, installed into `world/datapacks` BEFORE the world is first generated): Terralith, Incendium, Nullscape, Structory, Structory: Towers, Amplified Nether.

## Names plugin

Owns everything about how a player's name looks. Replaces ColorfulNames, HexNicks and TAB. Other plugins (Tag) add prefixes through its API instead of touching names themselves, so nothing fights.

### Features

- `/color` opens a chest GUI: the 16 named colors, a curated set of hex colors, a few gradients, and Reset. Click applies instantly. Permission `names.color` (default: everyone). Gradients behind `names.color.gradient` (default: everyone).
- `/nick` opens an anvil GUI to type a nickname (rename field). Also `/nick <name>`, `/nick reset`, and admin `/nick <player> <name|reset>`. Permission `names.nick` (default: op only; the owner grants it with LuckPerms). Rules (configurable): max 16 visible chars, letters/digits/underscore/space, cannot equal another known player's real name (anti-impersonation), MiniMessage tags stripped from player input.
  - **Bedrock:** `/nick` opens a Cumulus input form instead of the anvil GUI when the player is on Bedrock (Floodgate), behind `bedrock.forms` (default: on).
- **Bedrock tag (Joe, 2026-10-09):** Bedrock players (Floodgate) get a gray `[BE] ` prefix (Names' own prefix, key `bedrock`, priority -10 so it sits left of Tag's marker: `[BE] ☣ .Steve`). Shows in chat, tab list and above head, not in displayName (Discord, HuskHomes). Floodgate's `.` username prefix stays visible. Config `bedrock.tag.enabled|format|priority`.
- `/names reload`.
- Data: `plugins/Names/players.yml`, UUID to `{color, nick}`. Atomic writes (write temp file, then move over the original).

### Rendering (everywhere the name shows)

Name component = `[prefixes] + colored(nick or real name)`.

- **Chat:** custom `ChatRenderer` on `AsyncChatEvent`, built from scratch (do not wrap Paper's default renderer, see Paper issue #13574). Format configurable, default `<prefixes><name><gray>: </gray><message>`.
- **Tab list:** `player.playerListName(...)`.
- **displayName:** `player.displayName(...)` too (without prefixes), so HuskHomes, DiscordSRV etc show the nick and color.
- **Above head:** vanilla nametags only support 16 colors and always show the real username, so the real nametag is hidden (scoreboard team, `nameTagVisibility = never`) and a **packet-only TextDisplay** (PacketEvents) is mounted on the player client-side. Packet-only on purpose: a real passenger entity breaks cross-world teleports (Paper refuses to teleport a player with passengers across worlds, which would break `/home` and `/tpa`).
  - Spawn the fake display for a viewer on Paper's `PlayerTrackEntityEvent`, destroy on `PlayerUntrackEntityEvent`; re-send on respawn, world change, and name change.
  - **Crouching hides the label** (owner requirement), restored on stand-up. Also hidden in spectator mode and while invisible.
  - A player never sees their own label.
- **Scoreboard coexistence:** the hidden-nametag team lives on the main scoreboard. Plugins that give a player their own scoreboard (Tag's sidebar) must call `NamesApi.registerScoreboard(board)` so Names mirrors the team onto it.

### Public API (used by Tag)

`dev.sirbepy.mc.names.api.NamesApi`, fetched via Bukkit `ServicesManager`:

- `setPrefix(UUID, String key, Component prefix, int priority)`, `removePrefix(UUID, String key)`. Lower priority renders first. Persisted? No: prefixes are owned and re-applied by the plugin that set them (Tag re-applies on enable and join).
- `nameComponent(Player)`, the full rendered name with prefixes.
- `registerScoreboard(Scoreboard)`, `unregisterScoreboard(Scoreboard)`.
- `refresh(Player)`.

## SafeChests plugin (add-on for an unmodified Bolt)

Bolt already auto-locks chests on placement (owner-only). SafeChests adds the request and grant flow through Bolt's public `BoltAPI` (ServicesManager). Bolt is a hard dependency.

- **Denied open:** Bolt silently cancels the open. SafeChests notices (monitor-priority listener, sees the cancelled event, confirms with `BoltAPI.canAccess`) and tells the requester: `This chest belongs to <owner>. [Request access]`. Debounced so spam-clicking doesn't spam chat.
- **Owner alert on attempt:** server-config toggle `alerts.notify-owner-on-attempt` (default false), the owner's choice.
- **`/safe-chests-one-time`** (also what the [Request access] button runs): sends the request. If the owner is offline: `<owner> is offline`, request dropped.
- **Owner gets buttons:** `<requester> wants to open your chest at x y z: [Allow once] [5 min] [Always] [Deny]`. Requests expire after 2 minutes (configurable).
  - Allow once = that one chest, one open, then revoked (on close).
  - 5 min / Always = **all the owner's chests**, same as the whitelist command.
- **`/safe-chests-whitelist <player> [time]`** = access to all the owner's chests; no time = permanent; time accepts `300`, `5m`, `1h`. `/safe-chests-unwhitelist <player>`, `/safe-chests-list`.
- Timed grants persist with their expiry (`grants.yml`), so a restart never leaves a timed grant open forever; expired grants are revoked on startup and by a repeating check.
- Implementation preference: use Bolt's own owner-wide trust/access lists where they exist, with SafeChests tracking expiries. Never edit Bolt's code.
- `/safechests reload`.
- **Bedrock:** a denied requester on Bedrock gets a typed `/safe-chests-one-time` instruction instead of the chat click-event; an owner on Bedrock gets a typed `/safe-chests-pending` instruction instead of chat buttons, with an optional (config, default off) auto-opened answer form on every new request. `/safe-chests-pending` re-lists pending requests for a Java owner, or for a Bedrock owner opens the answer form directly (a chooser first if more than one), where a click answers through the exact same validated respond path the chat buttons use.

## Tag plugin

Always-on, server-wide game of tag. Hard depends on Names.

### Rules

- **Everyone plays by default**; `/tag optout` and `/tag optin`. The IT player cannot opt out. Opted-out players cannot be tagged.
- **Admin picks IT:** `/tag set <player>` (works for offline players: applied when they join), `/tag clear`.
- **Tagging:** IT melee-hits a player. Projectiles do not count (config toggle). **No damage and it works even with PvP off or inside protected areas**: listen to `EntityDamageByEntityEvent` at `LOWEST` with `ignoreCancelled = false`, and cancel the damage on a valid tag. Both players must be in survival or adventure.
- **Tagback cooldown:** the new IT cannot tag the player who just tagged them for 60s (configurable). Anyone else is fair game immediately. Hitting them anyway tells IT how many seconds are left (`<seconds>` in `logs.tagback-blocked.format`).
- **No teleporting away (`teleport-block:` in config.yml):** an opted-in survival/adventure player within `radius` (100) blocks of IT, in the same world, cannot use command or plugin teleports (`/home`, `/tpa`, `/back`, `/spawn`, `/warp`, `/rtp`, Haru's home item, even an admin's `/tp`) and is told why. Ender pearls, chorus fruit and portals still work. IT, opted-out players, and anyone near an AFK or non-playing IT are free.
- **IT logs off:** stays IT until they come back.
- **AFK handling (configurable, `afk:` in config.yml):** idle this long (no movement/chat/command/block/interact/inventory activity, `afk.threshold-seconds`) counts as AFK, detected silently (no broadcast; EssentialsX already announces its own AFK). AFK players are excluded from the points multiplier's `n` (`afk.exclude-from-multiplier`) and cannot be tagged (IT gets a rate-limited `afk.untaggable-message`). An IT who stays AFK keeps accruing points at the normal online rate; if IT is online and idle past `afk.it-pass-after-seconds`, IT auto-passes to a random eligible online player (not AFK, not opted out, survival/adventure, avoiding the previous IT unless they're the only one), with no +10 and no tagback cooldown for the receiver. IT gets a one-time warning `afk.it-pass-warning-seconds` before the pass. Offline IT is never auto-passed.
- **Tracker compass (`tracker:` in config.yml):** IT carries a compass that points at the nearest taggable player in its world; right-click cycles through trackable players (same world nearest first, then other dimensions by name). While held, the action bar shows `<player> - <n>m`, `<player> - Not in this realm` or `<player> - AFK, can't be tagged` (the compass spins for those two). Opted-out, creative, spectator and vanished players are never tracked. The compass can move around IT's own inventory but never leaves it (no drop, container, crafting grid, bundle, frame, death drop) and disappears the moment IT passes.
- **Persistence:** all state in `plugins/Tag/state.yml` (current IT, last tagger, tagback expiry, per-player stats, opt-outs, pending actions for offline players). Written atomically on every change and every minute. Never rely on player PDC as the source of truth (offline PDC is read-only).

### IT prefix

Added through `NamesApi.setPrefix(uuid, "tag", ...)`. Configurable MiniMessage, default: green biohazard symbol `<green>☣</green> ` (owner picked option G). Shows in chat, tab list and above head via Names. A nickname change can never remove it because Names renders prefixes separately from the name.

### Head item

- Configurable: either a material id or a player-head texture. Default: a sheep head (player head with a sheep-face texture; vanilla has no sheep head item).
- Becoming IT: the current helmet moves to the inventory; if full, the ender chest; if both full, it drops at the player's feet. The moved helmet is marked (PDC) so it can be found later.
- Stops being IT: the head is removed and the marked original helmet goes back on the head if it's still in the inventory or ender chest.
- The head cannot be removed, moved, dropped, or duped: cancel inventory click/drag/creative/drop events touching it (PDC marker), strip it from death drops, and delete stray marked heads found anywhere outside the IT player's helmet slot. A repeating check re-applies the head if something (like `/clear`) removed it.

### Points (lowest wins)

- Getting tagged: +10.
- Every minute (configurable) while someone is IT: `n` = online players other than IT who haven't opted out.
  - multiplier: `n = 0` gives 0 (safe); otherwise log curve `1 + 0.5 * log2(n)` (1 other = x1, 2 = x1.5, 4 = x2, 8 = x2.5). `curve: linear` gives `1 + 0.5 * (n - 1)`.
  - IT online: `1 * multiplier` per minute. IT offline: `0.5 * multiplier` per minute (factor configurable).
- Raw stats kept per player: times tagged, tags made, minutes IT online, minutes IT offline, points.
- `/tag stats [player]`, `/tag top` (chat leaderboard, lowest first), `/tag board` toggles a personal sidebar (live top 5, your score, who is IT; registered with `NamesApi.registerScoreboard`). `/tag who`.
- `/tag leaderboard place` (admin, in-game only) drops a floating leaderboard at the admin's location; `/tag leaderboard remove` takes it down. It is a non-persistent `TextDisplay` (never written to the world file), respawned from the location saved in `state.yml` on plugin enable and whenever its chunk (re)loads, so it survives a restart without ever duplicating. Ties share a rank (competition ranking), using the same ranking as `/tag top` and the sidebar.

### Logs

Per-event config: `enabled`, `broadcast` (all players), `console`, `format` (MiniMessage with placeholders like `<tagger>`, `<tagged>`, `<it>`, `<points>`). Events: tag, tagback blocked (to the tagger only), IT set, IT cleared, IT joined, IT quit, opt out, opt in.

`/tag reload`.

## Carry plugin

Carry On style mob carrying, fully server-side so vanilla Java and Bedrock (Geyser) clients both work with no mod. Towny and Bolt are soft dependencies.

- **Pick up:** sneak + right-click a carryable mob with an empty main hand. The mob rides the player's head as a real passenger (exact visuals on both editions; a crash leaves it a normal entity in the world, so nothing can be lost or duplicated). The carrier walks 15% slower (Slowness I), through a transient attribute modifier that is never saved.
- **Carryable:** passive and friendly mobs listed in `config.yml` (animals, villagers, wandering traders, iron/snow/copper golems, allays). Players, the Ender Dragon and the Wither never, even if listed. A tamed pet only by its owner. Leashed, ridden, riding or trading mobs are refused.
- **Hands locked while carrying:** no slot switching, item use, attacking, block breaking/placing, or inventory clicks. Nobody can interact with or damage the carried mob (void and `/kill` still work).
- **Put down:** right-click a block, the mob lands on the clicked face. **Throw:** sneak + left-click launches it along the view (vanilla fall damage applies).
- **Teleport, logout, death:** the mob is put down where the player was. Paper refuses a cross-world player teleport while the player has a passenger, before any event fires; HuskHomes ejects passengers itself before every teleport, and any dismount the plugin didn't make counts as a put-down there. An admin's cross-world Essentials `/tp` while carrying still fails.
- **Protections:** picking up needs Towny destroy permission at the mob, putting down or throwing needs Towny build permission at the spot. A Bolt-locked mob needs Bolt destroy access, which SafeChests grants also satisfy.
- `/carry reload`.

## Market plugin

The spawn market's rotating buyers. The server buys as little as possible so players trade with each other, and there is no passive income.

- **Rotation every in-game day:** at each sunrise (about every 20 minutes; sleeping through the night counts, `rotate-on: real-day` switches to midnight Europe/Zagreb) each of the six selling stalls picks items at random from its pool (`items-per-stall` in config.yml, capped by the stall's sign spots: 4 on the Farmer, 3 on the others) and writes them as dyed, glowing Essentials [Sell] signs on its sign spots. A chat line announces the change.
- **Unpredictable:** picks come from `SecureRandom` and are saved to `plugins/Market/state.yml`, so a restart keeps the market day's picks and nothing hints at the next one's.
- **Price drift:** each picked item's price is its base times a random factor in ±`price-spread` (20%), rounded to the cent, re-rolled with the picks every market day.
- **Prices:** AFK-farmable items (crops, fish, mob drops, ender pearls, blaze rods) are cheap; items only gathered by hand (logs, coal, raw ores, diamond, ancient debris, netherite scrap, leather, rabbit hide) keep a real price. The Smith never buys ingots, since iron and gold farms drop them, and pays 10% over its 2026-10-06 prices (Joe, 2026-10-09). Cod and salmon pay $8 a stack (Joe, 2026-10-09, down from the doubled $32). The Farmer sometimes buys seeds: wheat, beetroot, melon and pumpkin seeds at $1 a stack (every harvest drops more than replanting needs), torchflower seeds (sign name `torchseeds`) and pitcher pods at $1 each (sniffer only). The Lumberjack pays half for spruce, jungle, dark oak and pale oak logs (2x2 giants drop several times a normal tree's logs) and 20% over a log for crimson and warped stems (Joe, 2026-10-08). Mob heads (added 2026-10-08 at Joe's request) sell one at a time: the Mob drops stall buys zombie, skeleton, creeper and piglin heads (charged-creeper drops) at a diamond's price, Rare goods buys the wither skeleton skull (sign name `withersskull`, Essentials' alias that fits a sign line) at twice that and the dragon head at ten times, plus breeze rods at twice a blaze rod (trial spawners can't be AFK-farmed). No stall ever buys a player head: cartel trophies, Dragon Balls and every custom head are player heads, so `MarketLayout` refuses to load a `stalls.yml` that lists one. Pools, prices, sign spots and the price lectern live in `stalls.yml` inside the jar, generated by `server/spawn-market/generate.mjs`.
- **Shop street (Joe, 2026-10-09):** a paved street runs south out of the plaza under a "Shops" arch, with the Heads stall and three shops (Radars, Homes, Cosmetics, under striped awnings in their colours) facing each other along it. It keeps selling (the plaza) and buying (the street) apart and leaves room for more shops. Built by the same generator; sign spots in `stalls.yml` (`head-stall`, `shops`).
- **Heads stall (Joe, 2026-10-09, replacing the Head collector NPC):** buys the textured mob heads the More Mob Heads datapack drops, which no Essentials [Sell] sign can take. Each market day it wants the heads of `heads-per-day` (4) random mobs, one per sign; right-click any of its signs holding a wanted head (one or a stack) to sell the whole stack. Its base price is by drop chance, $0.50 over the unenchanted chance on a player kill (Breeze Head, 10%: $5; Enderman Head, 0.5%: $100; heads that always drop, like the Warden's: $0.50), rolled by `price-spread` per mob per day. A head counts only by its skin texture with no owner in its profile, so a real player's head is refused even with a mob's skin. The catalogue (84 mobs, about 200 heads) is `heads.yml` inside the jar, generated by `server/market-heads/generate.mjs` from the datapack zip. Spawn protection is off on live (the Tweaks spawn guard protects spawn instead), so plugin signs work for everyone. On enable the plugin removes the old collector's Mannequin and villager (they carry its `market:head-collector` marker). Pays through Vault.
- **Wanted board (Player of the Day, Joe, 2026-10-09):** a notice board in the plaza east of spawn. Each market day it picks a target, half the time an online player (when anyone is online) and otherwise anyone who has ever joined, shows their head and a price rolled around $100 (log-normal, median 100, clamped to $10-$1000, `wanted:` in config.yml). Right-click its sign holding the target's player head to sell it: a head taken after the bounty went up pays the full price, any older head (or one with no kill time) pays 75% (`old-head-share`). Heads carry their kill time (`sirbepy:killed_at`, core's KillStamp): the market stamps PvP drop heads in PlayerDeathEvent's drops, the cartel stamps its trophies with the hit's time. One sale per market day for the whole server, the target can't turn in their own head, and after a sale the next market day has no bounty. A sale is announced in chat; `/market rotate` (op) always picks a fresh target.
- **Homes shop:** sells extra `/sethome` slots: the 4th for $500, the 5th $1,500, the 6th $4,000, 6 at most (`homes:` in config.yml). Everyone starts with 3 (LuckPerms `default` has `huskhomes.max_homes.3`); a purchase grants the player `huskhomes.max_homes.<n>` through a console `lp` command, and HuskHomes' LuckPerms hook takes the highest node. The plugin also records what it sold in `homes.yml`, so a fast double click can't charge twice before LuckPerms applies the node.
- **Cosmetics shop:** sells a Cosmetic Token for $2,500 (`cosmetics.price`). Hold one item (not a stack) and run `/cosmetic <idea>`: the token is used up, the item gets a hidden id (`market:cosmetic_id` and a `cosmetic_<n>` custom_model_data string, keeping any it already had), and the request is saved in `cosmetics.yml` and logged as `Cosmetic request #<n> from <name> (<uuid>): <material> - <idea>` for Player Claude. Claude makes the look in the server pack and Joe checks it before it's marked done; it goes live at the next natural restart, never a forced one. `/cosmetic list`, `/cosmetic done <id>` and `/cosmetic refund <id>` (op) manage requests; done and refund messages (and a refunded token) reach an offline player on their next join. Bedrock players see the vanilla item.
- **Radars shop:** the same three radar signs as the Dragon Ball station, at the station's prices, with the Dragon Balls guide book on a lectern; the dragonballs plugin sells them (`station.shop-radar-signs`).
- **Price book:** every stall item and every Heads stall mob at its base price (the middle of its daily roll), one stall per page. The plugin keeps it on the Guide stall's "Prices" lectern (placing the lectern if it's missing) on every start and rotation, so it always matches the jar, and `/market prices` (everyone) opens it anywhere.
- `/market` or `/market today` (everyone) lists what each stall and the Heads stall buy today, at today's prices, and the Wanted board's target: one block per buyer, its name in its colour on a line of its own, then one line per item.
- `/market rotate` (op) picks new buyers now. `/market reload` (op) re-reads config.yml; new item counts and price spread apply from the next rotation.
- `/suggested-price` (alias `/price`, everyone) quotes the item in the main hand, per item and per stack, for pricing player shops. Market items are suggested at twice their stall base price; every other item has a price in the jar's `prices.yml`, generated by `server/market-prices/generate.mjs` from hand-set prices for gathered materials (`raw-prices.mjs`) and the cheapest vanilla 26.3 recipe for everything craftable. Enchantments (on gear, and stored on enchanted books) add their own price, listed one line each under the quote: an enchantment's price at its max level comes from how rarely the 26.3 enchanting table rolls it (weight 10 common $20, 5 uncommon $30, 2 rare $40, 1 very rare $60; treasure enchantments 1.5x; Mending set by hand at $100), each level below max halves it (two books of a level make the next), and each level past max adds it once more. Curses multiply the whole price, enchantments included (Binding x0.5, Vanishing x0.75). Potion contents add a set amount per type, and wear scales the bare item down but not its enchantments. Creative-only items say they can't be traded. Essentials' `/worth` and its `/price` alias are disabled so the alias reaches Market.

## Haru plugin

Haru, Keeper of the Sakura Sanctuary: ImMijo's Shenron wish (2026-10-06), an NPC merchant and quest giver at his base.

- **Everyone:** right-click Haru for his Everyday Wares, vanilla villager goods for emeralds, a little cheaper than a villager, with limited stock that comes back every 3 Minecraft days.
- **ImMijo:** the full menu: rotating wares, Spirit Requests twice a day that pay Spirit Petals, a reputation that unlocks more of the shop and of Haru's story, a Petal Exchange, a daily Mystery Curio and more he has to find himself. Everything from his own shelves is bound to him.
- Haru can't be hurt, converted, leashed or moved, and comes back by himself if anything removes him. `/haru` (op) places and inspects him.
- Full design, including every hidden reward, in `docs/haru.md`: not for players, never copy it to the website.

## Guild plugin

The Adventurer's Guild: ImMijo's Shenron wish (Nuova Shenron, 2026-10-08), a server-wide quest and rank system. ImMijo is its founder and Guildmaster.

- **Everyone:** register with the receptionist at the Guild Hall (500 coins once, into the Guild Treasury) and start as an E-Rank Adventurer. `/guild` shows the board anywhere, but contracts are taken and turned in only by talking to the hall's staff (`/warp guild`). Each adventurer gets 5 daily contracts (new at midnight Europe/Zagreb) and 1 weekly (new on Monday), each showing its reward up front: hunts, mining, hand-ins, harvests, fishing, breeding, taming, trading, enchanting, smelting, and visits to biomes and structures from vanilla, Terralith, Structory, Dungeons and Taverns and Incendium. Ranks E, D, C, B, A, S come from reputation and contracts, and each rank above E needs its Promotion Trial; higher ranks draw harder, stranger contracts. At most 2,000 coins and 15 levels a day from Guild contracts.
- **The Guildmaster:** an office in `/guild` with the Treasury (fees plus a 5% commission minted on top of every paid contract; he may draw 1,000 a day), members, ledger and statistics, up to 3 Treasury-funded commissions a week, a featured commission and server announcements. He has his own rank, starts at E, and can promote no one.
- **Server-wide:** one bounty at a time for C-Rank and up (first to claim wins), a weekly Grand Quest the whole server works on together (everyone who helps enough shares the reward), the Guildmaster's occasional contests (`/guild activity`, prize from the Treasury), a Hall of Fame (`/guild halloffame`) and titles to wear, some of them hidden.
- **The Guild Hall:** ImMijo builds it himself; its five staff NPCs (receptionist, quest clerk, treasurer, trial examiner, archivist) are placed by console wherever he asks (`/guildadmin staff place`), with an optional protected box (`/guildadmin hall guard`).
- `/guildadmin` (op) inspects and repairs members, boards and the Treasury.
- Full design, including every secret, in `docs/guild.md`: not for players, never copy it to the website.

## Cartel plugin

The cartel flip phone: Archontopouloi's Shenron wish (2026-10-06). Right-clicking his phone opens a menu that calls in Leonel and Marco Salamanca (zombies painted as the cousins by the server pack, in a chainmail chestplate and leggings, with a crossbow drawn as a black pistol; none of it ever drops).

- **Guard Mode:** they follow him for 10 minutes and shoot monsters near him and anyone who hurts him. Once every 2 real hours.
- **Assassinate Mode:** he picks an online player; the cousins appear 10 blocks from them and hunt them for 3 minutes, and the whole server reads "[name], you have made enemies of the cartel." Once every real day (24 h).
- **Fully ranged, both cousins, both modes (Joe, 2026-10-07, replacing the council's Marco-only pistol and its 36 h cooldown):** the crossbow carries Piercing IV, Quick Charge III and Power V. The plugin fires the bolts itself (a zombie has no ranged attack), so it applies all three: bolts pierce 4 entities, reload in half a second, and do 2.5x an arrow's base damage (Power V's +150%). They keep their distance (close in past 8 blocks, back off inside 4), never melee, and their bolts hurt only who the call lets them fight, with no knockback; nobody can pick the bolts up.
- Every limit and the reasoning behind it: `docs/cartel.md`. `/cartel` (op) gives phones and checks or resets cooldowns.

## Testing

1. **Unit tests** (JUnit 5, plain logic wherever possible: points math, cooldowns, duration parsing, grant expiry, name validation). MockBukkit for listener/command behavior where it supports the API used.
2. **Real server + bots:** `testserver` runs in offline mode with ViaBackwards; Mineflayer scripts (Node) join as two or more bots and assert behavior through chat and inventory: tagging, tagback, no-damage-with-PvP-off, head swap, persistence across a server restart, chest denial and request flow.
3. **Screenshots** of the visual parts (prefix in chat/tab/nametag, color GUI) for the owner.

## Later (todos, not now)

- Bedrock testing on a real Bedrock client (forms and Geyser-translated GUIs can't be driven by the bots).
- Deploying to the real server.
