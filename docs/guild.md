# The Adventurer's Guild

The `guild` plugin is ImMijo's Shenron wish, granted 2026-10-08 22:19 UTC by Nuova Shenron (Nether set,
session `i9mha1`, logged in `plugins/DragonBalls/wishes.jsonl`) after the dragon's council voted
GRANT_WITH_DEAL 5-0. Joe delegated the verdict and the design to Player Claude and the council.

**This file is for Joe and Claude only. Not for players, never copy it to the website.** ImMijo asked
for the hidden quests, secret titles, surprises, Promotion Trials and S-Rank content to stay secret
from everyone, himself included. Nothing in the "Secret" sections below goes into in-game text that a
player can read before they reach it, the public website, `docs/plan.md`, or anything Player Claude
says. `docs/plan.md` has a spoiler-free summary.

## The wish (ImMijo, 22:15:57-22:19:10 UTC, 20 lines)

A permanent, server-wide Adventurer's Guild every player can join; ImMijo founds it and is its
permanent Guildmaster, but the Guild is mainly a progression and quest system for everyone. A one-time
fee, start at E-Rank, ranks E-D-C-B-A-S by reputation, quests and harder promotion requirements; S
genuinely hard. Ranks unlock harder, more interesting contracts, privileges and special content. Varied
contracts (hunting, gathering, exploration, fishing, deliveries, structures, dungeons, bosses) using the
server's own content (vanilla, worldgen, structures, mobs, dimensions, installed packs), not repetitive.
Normal, daily, weekly, rare contracts, major bounties, Grand Quests, some cooperative or server-wide.
Every contract shows its reward. A Treasury fed by fees and a commission generated ON TOP of contract
rewards, never taken from the adventurer. A Guildmaster interface (members, ranks, stats, Treasury,
withdraw earnings, management). Custom contracts funded from the Treasury, featured contracts,
announcements, occasional activities, while normal play runs by itself. Being Guildmaster does not make
him S-Rank: he has his own rank and earns it. A physical fantasy/isekai Guild Hall (reception, quest
board, gathering hall, Treasury, Guildmaster's office) with permanent protected NPCs that have
personalities and contextual dialogue. Statistics and a Hall of Fame (first S-Rank, records), major
promotions announced. Hidden achievements, titles, unusual contracts, secrets and surprises. Balanced
around the real economy; worth seven Dragon Balls but creating gameplay, not handing out resources.

## The dragon's caps (announced in-game, never exceeded)

| Cap | Value |
| --- | --- |
| Joining fee | 500 coins, once, into the Treasury |
| Per adventurer, per real day, from Guild contracts | 2,000 coins and 15 XP levels |
| Item rewards from generated contracts | iron tier at most: no diamond/netherite gear, no shulkers, no elytra, no high enchant books |
| Contract volume | 5 dailies + 1 weekly per adventurer (3 dailies until Shenron's council raised it on 2026-10-09, see Pace), plus rare contracts, bounties and Grand Quests, all inside the daily caps |
| Treasury income | fees + a 5% commission minted on top of every paid generated contract |
| Guildmaster's own withdrawals | at most 1,000 coins per real day |
| Guildmaster powers | view members/ranks/stats/ledger, feature contracts, announcements, occasional activities, up to 3 Treasury-funded commissions a week (paid from the Treasury, never minted; he can't take or claim his own) |
| Never | promoting anyone, admin powers, a head start: his rank starts at E |
| Guild Hall | a public building near spawn, not his property; his office is inside |

The dragon said in-game "up to 3 quests a week of your own design (the Guild sets their rewards within
the caps)" and, at the grant, "fund your own contracts from the Treasury". Both hold: 3 commissions a
week, rewards he picks inside the Guild's bounds (10-1,000 coins), escrowed from the Treasury.

## Economy calibration (measured 2026-10-08)

`baltop` on live at 22:22 UTC: **the whole server held 8,613 coins** (top balance 4,240; three players
at 0). The spawn market pays 28.8 for 64 logs, 16 for 64 cod, 160 for 16 diamonds; `prices.yml` values a
diamond at 20. A Shenron money wish was 100,000 (40,000 in the Overworld since 2026-10-09). So the 2,000/day cap is a ceiling nobody should reach;
real rewards sit an order of magnitude under it, and the Guild's value lives in progression, titles,
access and story rather than coins. A full E-Rank day (5 dailies) pays about 125 coins, roughly 4 stacks
of logs sold at the market; a full S-Rank day about 660 plus the weekly (700). Only an S day where most
slots rolled rare (2x coins) plus the weekly can reach the 2,000 cap, and the claim clip handles it.

## Ranks

| Rank | Title | Reputation | Contracts done | Then |
| --- | --- | --- | --- | --- |
| E | E-Rank Adventurer | 0 | 0 | registered |
| D | D-Rank Adventurer | 150 | 6 | pass the D Trial |
| C | C-Rank Adventurer | 450 | 20 | pass the C Trial |
| B | B-Rank Adventurer | 1,000 | 45 | pass the B Trial |
| A | A-Rank Adventurer | 2,000 | 90 | pass the A Trial |
| S | S-Rank Adventurer | 3,600 | 150 | pass the S Trial |

Reaching the reputation and the contract count unlocks that rank's Promotion Trial; only passing the
trial promotes. Reputation keeps counting while a trial waits. Nobody, the Guildmaster and ops
included, can set a rank by hand in normal play (`/guildadmin` exists for testing and repairs only).

Pace (5 dailies + 1 weekly, Shenron's council ruling on ImMijo's "more quests a day, 3 weeks of every
quest = S rank", 2026-10-09, 5-0: more dailies, every threshold unchanged, so S stays genuinely hard):
dailies pay 10-30 reputation by rank and a weekly about 4x a daily, so a perfect player gains ~56/day
at E, ~141/day at A and ~163/day at S (effort multipliers averaged to 1, rares ignored). D comes in about
3 days, C ~1 week, B ~2 weeks, A ~3 weeks (day ~22), S ~5 weeks (day ~33) of perfect play, plus the time
five trials take, so A in about 3-4 weeks and S in about 5-6; the 150-contract floor alone needs ~29
days. Realistically S is a 1.5-2 month goal. (At 3 dailies it was A ~5 weeks, S ~7, floor ~48 days.)

### Rank privileges (never stat buffs)

| Rank | Unlocks |
| --- | --- |
| E | 5 dailies + 1 weekly, 1 free reroll a day |
| D | rare contracts can appear (10% per daily slot); commissions default to D+ |
| C | 2 rerolls a day, the Nether opens on the board (fortresses, blazes, wart), Bounty Board (phase 3) |
| B | rare chance 15%, bastions, trial chambers and Incendium's named mobs on the board |
| A | 3 rerolls, ancient cities, monuments, mansions, Incendium's elites; Expedition contracts (phase 3, secret) |
| S | S-Rank title and announcement, bosses on the board, a place in the Hall of Fame, Legend contracts (secret) |

Promotions to D and C are told to the player; B, A and S are announced to the whole server.

## Contracts

Each adventurer has a personal board: **5 dailies** (new at midnight Europe/Zagreb; `daily-contracts`
in config.yml, 1-5, the menu has room for 5) and **1 weekly** (new Monday 00:00 Europe/Zagreb).
Whatever is left when a new set is drawn expires. Daily slot 1 draws from one rank below the adventurer
(E stays E), slots 2-5 from their own rank; the weekly from their own rank's weekly pool. No contract
appears twice on a board, and the last day's dailies are avoided when the pool allows (every rank has
10+ dailies, so 5 on the board plus yesterday's 5 still fit; past that, yesterday's come back before
anything repeats), so a board rarely repeats. A board drawn with fewer slots than the config asks for
(the 3-daily boards on 2026-10-09) gains the missing slots the same day, keeping what was already
claimed, worked and rerolled; the full redraw waits for midnight. In the menu the dailies sit in row 3
(slots 19-23), the weekly at its right end (25).

Rewards by the **contract's** rank (each contract has an effort multiplier, 0.8-1.5, applied to coins
and reputation, rounded):

| Contract rank | Coins | Reputation | XP levels |
| --- | --- | --- | --- |
| E | 25 | 10 | 1 |
| D | 35 | 14 | 1 |
| C | 50 | 18 | 2 |
| B | 70 | 22 | 2 |
| A | 100 | 26 | 3 |
| S | 140 | 30 | 3 |

Weekly: 5x coins, 4x reputation, 5 levels. Rare: 2x coins, 2x reputation, plus an iron-tier supply
bundle. The dailies alone pay at most 5x3 = 15 levels (S), but with the weekly a perfect A day comes to
19 and an S day to 20 (B lands on exactly 15), so A and S adventurers who claim everything are clipped
to 15 that day. Every claim is clipped
to what is left of that day's caps (coins and levels separately); the menu shows the clip before the
click, and the Treasury commission is 5% of what was actually paid (at least 1 coin on a paid claim).

Contract kinds: **hunt** (entity types), **named hunt** (Incendium's named mobs, matched by their
translation key so a renamed mob doesn't count), **mine** (ores, no Silk Touch), **gather** (hand
items in at claim; they are consumed, a deliberate item sink), **harvest** (ripe crops), **fish**,
**breed**, **tame**, **trade**, **enchant**, **smelt** (taking items out of a furnace), **explore
biome** (Terralith biomes included), **explore structure** (stand inside a structure's bounding box:
vanilla, Dungeons and Taverns `nova_structures:*`, Structory, Structory Towers, Terralith, Incendium),
**boss** (Wither, Elder Guardian, Warden; everyone who hurt it in its last 60 seconds is credited, so
bosses can be done together). The full pool lives in `ContractPool.java`.

### Farm rules (hunting, since 2026-10-08)

ImMijo reported on 2026-10-08 that the week's Great Hunt (1,000 hostile mobs) was finished with a mob
farm; Joe set these rules the same day (`listener/FarmGuard.java`). They apply to every hunt and named
hunt kill: contracts, trials, commissions, bounties, the Grand Quest and Hunting Season. Bosses (Wither,
Elder Guardian, Warden, Ender Dragon) skip all of them and are credited as before.

- **Spawners**: a mob a monster spawner made counts for nothing. Paper keeps a mob's spawn reason in its
  save data, so this holds for mobs spawned before a restart too.
- **Trial spawners**: their mobs count only toward objectives that name the mob (Defeat 3 Breezes, Wanted:
  Breezes, Defeat Zombies...), never as "any hostile mob" (the Great Hunt, The Long Patrol and other
  `#HOSTILE` objectives, Hunting Season). Breezes come from nowhere else, and a trial spawner can't be
  AFK-farmed (each player triggers one once per 30-minute cooldown). Joe chose this over a flat ban.
- **Environment**: a kill counts for nothing when more than half (`farm-environment-share: 0.5`) of the
  damage the mob took came from anything but a player: falls, lava, fire and burning, suffocation,
  drowning, cramming, cactus and other mobs. A player's damage includes their arrows, tridents and other
  projectiles, and their tamed pets. Each hit counts at most the health the mob had left, so a 40-block
  drop onto a mob with 1 heart weighs 2, not 40. The tally lives on the mob (PDC `guild:player-damage`,
  `guild:other-damage`). Known edge: a zombie burning in daylight that a player finishes off may not
  count, and Fire Aspect's burn counts as environment (the sword hits themselves still win in practice).
- **Chunk limit**: once more than 30 mobs (`farm-chunk-kill-limit`) have died to players in one chunk within
  10 minutes (`farm-chunk-window-minutes`), further kills there count for nothing until the window clears.
  Refused kills are remembered too, so a farm that keeps running keeps its chunk shut, while the next
  chunk over is unaffected. Estimated, not measured: a big cave fight is well under 30, and a raid's
  raiders arrive in waves and die across several village chunks, so neither normally reaches it. A
  grinder that passed the other rules is held to 30 counted kills per 10 minutes per chunk at most.
- **Grand Quest share cap**: one adventurer's part counts up to 25% of the goal (`grand-quest-share-cap`,
  rounded up: 250 of the Great Hunt, 500 crops, 200 ores, 75 catches, 63 animals), every Grand Quest
  kind. Past that their deeds add nothing to the server's total, so a Grand Quest always needs at least
  four adventurers; they're told once in chat when they reach it.
- **Mine, placed ore (2026-10-09)**: Silk Touch let a player mine one ore, place it back and break it
  again with a plain pickaxe forever; Joe's decision was mine only, the other kinds unchanged.
  `listener/MineGuard.java` marks a just-placed ore (any `*_ORE` plus Ancient Debris) in its chunk's
  PersistentDataContainer, one packed position per block, so the mark survives a restart; a marked
  block never counts when broken, by any tool, and the mark is cleared either way. A natural ore
  mined with Silk Touch still doesn't count, unchanged. A piston push drops the mark rather than
  following it: migrating it across a contraption (and possibly into another chunk) is a lot of
  bookkeeping for a loophole that already needs redstone to build, so a pushed ore reverts to
  counting normally once it settles.

Not changed (todo 80): harvest, fish, breed, trade and smelt have their own farm loopholes.

Rerolls: E 1, D 1, C 2, B 2, A 3, S 3 a day, free, dailies only.

Iron-tier supply bundles (rare contracts and trials only): iron ingots, bread, cooked meat, arrows,
torches, coal, golden carrots, rails, an iron tool. Never anything enchanted.

## The Treasury

- Income: every 500-coin fee, plus 5% on top of every paid generated contract (minted into the
  Treasury, never taken from the adventurer).
- Out: the Guildmaster's own withdrawals (1,000 a real day at most, into his balance) and his
  commissions' escrowed rewards.
- Every movement is a ledger line (time, kind, amount, who, balance after), kept in
  `plugins/Guild/guild.yml`, the last 500 entries.

## The Guildmaster

ImMijo (`8c0c13b9-057a-4ac8-aacf-7b6c77f8742b`), `guildmaster:` in `plugins/Guild/config.yml`. He
registers like anyone (the fee goes into his own Treasury, which is fine: it is one of his own coins in,
and he can only take 1,000 a day out) and starts at E. His office (`/guild office`, the Office button
in `/guild`, or a right-click on Silas the treasurer) has:

- **Treasury**: balance, today's withdrawals left, withdraw 100 / 500 / all allowed, the last ledger lines.
- **Members**: every adventurer with rank, reputation, contracts done, last active.
- **Statistics**: members by rank, contracts completed today/this week/all time, coins paid, Treasury in/out.
- **Commissions** (`/guild commission <hunt|gather|mine|fish> <target> <amount> <reward> [min rank]`):
  3 a week (Monday reset), reward 10-1,000 coins taken from the Treasury into escrow when posted.
  Open to members of the minimum rank (default D). Every eligible member makes progress at once; the
  first to finish and claim it is paid in full from escrow. That is Treasury money already in
  circulation, not minted, so it sits outside the daily cap (which is for Guild-generated contracts).
  The claimer earns 5 reputation, and a commission does not count toward promotion contract numbers,
  so commissions can't rush a friend's rank. Gathered items go into the Guildmaster's Stores,
  collected from his office. He can cancel an unclaimed commission for a full refund; cancelled ones
  still count toward the 3 a week, so posting can't be used to spam the server. He can't take or
  claim his own.
- **Featured contract**: one of his commissions, highlighted at the top of every member's board and
  announced once.
- **Announcements**: `/guild announce <text>`, server-wide as "[Guild] Guildmaster ImMijo: ...", one per
  30 minutes.
- **Activities** (phase 3): occasional Guild events he starts, from a fixed menu.

## Commands

- `/guild` (everyone): the Guild menu (register, board, profile, commissions, office). Anywhere but the
  hall's desk it is for looking only: see "Taking and turning in work" below.
- `/guild profile [player]`, `/guild board`, `/guild announce`, `/guild commission ...`, `/guild cancel <id>`, `/guild feature <id>`.
- `/guild titles` (the title picker), `/guild titles show|hide`: whether the worn title shows as a tag
  beside the member's name (see "Title tags" below). Off until the member turns it on.
- `/guildadmin` (ops, console, RCON): `status`, `member <player>`, `rep <player> <n>`, `contracts <player> <n>`,
  `newday <player>`, `newweek <player>`, `setrank <player> <rank>` and `resettrial <player>` (repairs only),
  `complete <player> <0|1|2|weekly|trial>` (testing), `treasury <n>`, `resetannounce`, `guildmaster <player>`,
  `grandquest reroll` (voids this week's Grand Quest, done or not, and starts a different one for the
  rest of the week with a server-wide announcement; shares already claimed stay paid, contributions and
  claims start over),
  `firstdragon` (First Dragon status: armed, or the party and when; also names anyone whose coins are
  still owed) and `firstdragon reset` (testing and repair only: forgets the record so the next dragon
  killed in the End runs the event again; what was already paid stays paid),
  `firstdragon add <player>` (repair: puts a member who was in the fight but never hurt the dragon into
  the recorded party and grants the Dragonslayer title; no coins or reputation, pay those by hand),
  `probe <world> <x> <y> <z>` (the biome and structures exploration contracts see there), `staff ...` and
  `hall guard ...` (above), `reload`.

## Secret: Promotion Trials

Shown to a player only once they qualify for that rank. A trial is a special board slot with several
parts, open for 3 real days (7 for S); expired or failed, it can be retaken 24 h later. "Without dying"
parts reset to zero on death. Rewards: the rank itself, 1 supply bundle, and a story line from the
examiner.

| Trial | Name | Parts |
| --- | --- | --- |
| D | Proof of Steel | defeat 25 hostile mobs without dying; hand in an iron sword |
| C | The Delve | stand inside a Dungeons and Taverns crypt or dungeon (`catacomb`, `undead_crypt`, `small_undead_crypt`, `creeping_crypt`, `small_creeping_crypt`, `surface_dungeon_*`, `cave_chamber_dungeon_colony`); mine 3 diamond ore; without dying |
| B | Trial by Fire | in the Nether: defeat 8 Blazes and 3 Wither Skeletons; stand inside a Bastion or any Incendium structure; without dying |
| A | The Drowned Throne | defeat an Elder Guardian (shared credit); stand inside an Ocean Monument |
| S | The Legend | defeat the Wither; defeat a Warden (both shared credit); stand inside Incendium's Forbidden Castle |

## Secret: rare contracts (phase 1)

From D, a daily slot can roll a rare contract instead: 2x coins, 2x reputation and a supply bundle,
shown with a gold border and the line "A rare request". Pool in `ContractPool.RARE`, e.g. catch a
pufferfish and a tropical fish, tame a parrot, slay a Toxic Slime, a Reactor Sentry or a Restless
Spirit, visit a Dungeons and Taverns tavern and a firewatch tower, stand in the mushroom fields, find a
Structory old manor, Terralith's mage towers.

## Secret: titles and hidden achievements

Worn from the profile (click the head in `/guild`, or `/guild titles`); the title list shows only
what the adventurer has earned. Each new one shows a title card and a line to its finder only. All in
`fame/Titles.java` and `fame/Achievements.java`.

| Title | Earned for |
| --- | --- |
| E-Rank ... S-Rank Adventurer | reaching the rank (first A and first S announced to the server) |
| Founder of the Guild | the Guildmaster, on registering |
| Guildmaster | the Guildmaster, on registering (or on his next join, for one who registered before it existed) |
| The First of Many | the very first contract the Guild ever paid (announced) |
| Night Owl | a claim between 03:00 and 05:00 Europe/Zagreb |
| Pathfinder | 25 different structure kinds stood in while a member (tracked passively, contract or not) |
| Wanderer of Many Lands | 40 different biomes, same |
| Master Angler | 25 fishing contracts |
| Jack of All Trades | contracts of 10 different objective kinds |
| The Guildmaster's Hand | 10 commissions |
| Swiftblade | a weekly finished within an hour of being posted |
| Death's Regular | three deaths during one Promotion Trial |
| Steadfast / Unbroken | claims on 7 / 30 days in a row |
| Centurion | 100 contracts |
| Seeker of Oddities | 10 rare contracts |
| Bane of Giants | credited on a Wither, a Warden and an Elder Guardian |
| Bounty Hunter | 5 bounties |
| Champion of the Guild | the biggest contributor to a Grand Quest |
| The Patron's Favourite | the mysterious patron's rare request (a cake, a lantern and a pumpkin pie) |
| Keeper of the Charter | Expedition: The Sunken Charter (A weekly: a shipwreck, buried treasure, an Ocean Monument) |
| Ashwalker | Expedition: Ashes of the Old Guild (A weekly: Incendium's abandoned tower, a Nether keep, a Headless Warrior) |
| Heir of Legends | Legend: The Last S-Rank (S weekly: an Ancient City, a Warden, 4 echo shards, an Incendium Sanctum) |
| Derby Champion | winning one of the Guildmaster's activities |
| Dragonslayer | hurting the first Ender Dragon the server ever killed, as a member (First Dragon below) |

### Title tags (since 2026-10-10)

ImMijo asked for Guild titles beside members' names ("mine would be Guildmaster of course"); Joe
approved it as opt-in. A member who turns it on (`/guild titles show`, or the name-tag button at the
bottom of `/guild titles`) wears their worn title as a tag before their name in chat, the tab list
and the floating nametag: `[S-Rank]` in the rank's colour for a rank title, `[Night Owl]` in gold for
any other. Members who leave it off, and non-members, look exactly as before. Only the title the
member wears and has earned ever shows, so nobody's other titles are revealed; wearing a different
title or earning one that is auto-worn updates the tag at once. The choice is saved as `show-title`
in guild.yml.

The tag is a Names prefix (`NamesTitleSink`, key `guild-title`, priority 10, so `[BE] ☣ [S-Rank] Name`),
the same mechanism as the [BE] tag and Tag's IT marker, re-applied for every member on enable and on
join. Without the Names plugin the Guild runs as before and logs that tags won't show.

## Server-wide events (`events/ServerEvents.java`, `events/EventPools.java`)

- **Bounties** (C-Rank and up, never the Guildmaster): one at a time on the board, 180-300 coins +
  30-50 reputation + 2 levels inside the daily caps (commission minted on top). First to finish and
  claim wins it, announced to all; a new one comes 12 h after a claim, or after 3 days unclaimed. Pool:
  an Evoker, 6 Wither Skeletons, a Ravager (B), 3 Ghasts, 5 Breezes, 3 Piglin Brutes (B), 2 Reactor
  Sentries (B), an Elder Guardian (A), 4 Witches, 6 Phantoms, 2 Sanctum Inquisitors (B), 15 Drowned.
- **Grand Quests**: one a week (Monday), the whole server's progress counted together: 1,000 hostile
  mobs, 2,000 ripe crops, 800 ores, 300 catches or 250 animals bred. Once done, everyone who gave at
  least 2% of the goal (10 minimum) claims 150 coins + 30 reputation + 3 levels inside their caps; the
  biggest contributor earns Champion of the Guild (on a tie, whoever started contributing first). One adventurer
  counts for at most 25% of the goal (Farm rules above).
- **Guildmaster activities**: `/guild activity <hunt|fish|mine> <prize> [minutes]`, once a real day,
  10-60 minutes (30 by default), prize 50-500 coins escrowed from the Treasury. Members (not the
  Guildmaster) score hostile kills, catches or ore; the top score takes the prize (split on a tie,
  refunded with no entrants), from the Treasury so outside the daily cap.
- **The First Dragon** (one time only; Joe, 2026-10-09, for the server's first Ender Dragon fight; public:
  Guild members who fight the first dragon are rewarded). When the first Ender Dragon ever dies in the
  End, every registered member who hurt it during that fight gets 300 coins, +50 reputation (it is no
  contract, so promotion's contract count doesn't move), the secret title Dragonslayer, and a place in
  the Hall of Fame's First Dragon party (stored once, never overwritten); the server hears the party
  named. The Guildmaster counts like anyone else. Non-members and members who only stood in the End
  get nothing from the Guild. Every party member's coins are paid outside the daily caps, minted like a
  Grand Quest share with the 5% Treasury commission minted on top (15 each); party members offline at
  the kill are paid straight into their balance at once and told on their next join (if the bank is
  down at the kill, the coins wait and are paid on the next join). Item and XP rewards for the fight
  belong to the separate `endfight` plugin (b7db, 2026-10-09), so nothing is paid twice.
  - *Hurt it* means the game blames the player for damage the dragon took: their melee hits, any
    arrow, trident or other projectile they shot or threw, TNT they lit, an End Crystal they broke
    (the game's own blame, which for a crystal the dragon was healing from can fall on the nearest
    player), and a bed or respawn anchor they set off within 2 blocks of the blast (the game blames
    nobody for those, so `listener/DragonSlayers.java` matches the click to the blast). Not counted:
    pets (they can't hurt the dragon anyway), dispensers, redstone-lit TNT, an op's `/kill`, and hits
    the dragon shrugs off (deflected arrows while it perches, its half-second hurt cooldown, the
    death animation).
  - The fighters are a list kept on the dragon itself (PDC `guild:dragon-fighters`), so it survives a
    restart mid-fight and every new dragon starts with an empty one. Only a dragon dying in the End
    counts, summoned ones included (ops only).
  - First kill ever: at boot, and when an End loads, the Guild reads the End's own record (the dragon
    fight's "previously killed" flag). If the End already saw a dragon die before the Guild could
    record one, the event is spent with no party and the log says `The End (world_the_end) records a
    dragon killed before the Guild tracked it`; otherwise the log says `First Dragon event armed`. The
    game only sets that flag once the death animation ends, so the Guild never reads it at a kill.
    State: `first-dragon` in guild.yml's events section.

## Hall of Fame

`/guild halloffame`, the book in `/guild`, or Pip the archivist: the first to each rank, the first
contract and the First Dragon party (stored once, never overwritten), and top-5 tables for contracts, reputation, longest daily
streak, bounties, commissions, structure kinds explored and titles.

## The Guild Hall and its staff

**ImMijo builds the Guild Hall himself** (Joe, 2026-10-08: "ImMijo will build the entire building, you
just spawn the npcs where he tells you to"). Claude builds nothing; Player Claude places each staff
member where ImMijo asks, from the console:

- `guildadmin staff place <role> <world> <x> <y> <z> <yaw>` (yaw: 0 faces south, 90 west, 180 north,
  -90 east; an op standing on the spot can use `guildadmin staff place <role>`), `guildadmin staff remove
  <role>`, `guildadmin staff list`. The world names are `world`, `world_nether`, `world_the_end`.
- Optional, only if ImMijo or Joe wants it: `guildadmin hall guard <world> <x0> <y0> <z0> <x1> <y1> <z1>`
  stops non-ops breaking or placing blocks, editing signs, taking item-frame or armour-stand items,
  toggling trapdoors or candles, and blows up nothing inside that box; `guildadmin hall guard off` lifts
  it. Off by default.

Each staff member is two bodies on one spot, built like Haru (ImMijo, 2026-10-09: "the way Haru was
done well"). Java players see a player-shaped Mannequin in that character's own skin, drawn in the
server pack (`claudesmp:entity/guild/<role id>`, from `server/resourcepacks/guild_skins.py`), with the
title under the name. Bedrock players, and Java players whose pack declined or failed, see an AI-less
villager with a name and a floating title instead (a cached pack is answered before join with no status
event, so unknown counts as loaded). Both bodies turn to face the nearest visitor, can't be hurt,
leashed, converted or traded with, and right-clicking either one does the same thing; a guard tick
re-creates or snaps back any body missing within 5 seconds while its chunk is loaded, including the
Mannequin of staff placed before it existed.

The skins: Lyra in the Guild's navy uniform with a rose ribbon tie, a gold badge and a quill behind her
ear; Bram with receding grey hair and beard, a scar over one eye, a pencil, a tan leather vest and a
finger missing from his left hand; Silas with gold round spectacles, a gold waistcoat and watch chain
under a black frock coat, and an ink-stained finger; Captain Maren in steel plate under a blue tabard
and cape with a gold sword; Pip with copper curls, a quill stuck in them, freckles, a green robe and a
satchel. Lyra and Maren have slim arms.

| Role id | Name | Title | Right-click opens |
| --- | --- | --- | --- |
| receptionist | Lyra Fennwick (cheerful, a little overworked, knows every file) | Guild Receptionist | registration for strangers (the only place to register), the board for members |
| clerk | Bram Ashgrove (grumpy retired adventurer, lost a finger to a Breeze) | Quest Clerk | the board (members) |
| treasurer | Silas Coinwright (fastidious, loves his ledger) | Guild Treasurer | the Guildmaster's office (Guildmaster only); others hear the balance |
| examiner | Captain Maren Holt (stern, fair, ex-soldier) | Trial Examiner | the board when a trial is open or running; otherwise says what is still needed |
| archivist | Pip Quillfeather (chatty, keeps every record) | Guild Archivist | talks Guild records; the Hall of Fame |

Dialogue depends on the first visit, the visitor's rank, finished-but-unclaimed work, a finished weekly,
rerolls, the trial state and time left, the Guildmaster, night and thunder. Sneak-click only talks.

### Taking and turning in work (since 2026-10-09)

ImMijo asked that players have to come to the Guild Hall; Joe picked the compromise. `/guild` and every
screen behind it still open anywhere for looking: today's contracts and their progress, the profile,
ranks, commissions, bounty, Grand Quest, Hall of Fame, titles. **Doing** anything that takes or turns in
work needs the hall's desk:

- registering (only Lyra opens the registration screen, so registering means talking to her),
- claiming a daily or the weekly, rerolling a daily,
- claiming a commission (featured or from the Commissions screen), the bounty or a Grand Quest share,
- beginning or completing a Promotion Trial.

Elsewhere those clicks do nothing but say "Contracts are taken and turned in at the Guild Hall: talk to
Lyra or Bram there. /warp guild" (registration: "Registration is at the Guild Hall's front desk: talk to
Lyra there. /warp guild"). A ready contract's icon says "turn it in at the Guild Hall, /warp guild"
instead of "click to claim", the reroll hint only shows at the desk, and the profile head always says
where contracts are taken and turned in. "Done: ... Turn it in at the Guild Hall (/warp guild)" and the
bounty and Grand Quest broadcasts point there too.

The desk is a staff member, not a place (`gui/HallDesk.java`): right-clicking any of the five (either
body) puts the player at the desk for as long as they stay within 6 blocks of where that staff member
stood when talked to. Nothing about the hall's coordinates is coded, so the staff can be moved with
`guildadmin staff place` and the desk moves with them. While still beside them, `/guild` itself counts
too (a player who closed the board and typed `/guild commissions` at the counter isn't sent away). In
practice: Lyra registers newcomers and opens the board, Bram opens the board for members, Captain Holt
opens it for trial business, Silas opens the Guildmaster's office and Pip the Hall of Fame (whose back
button leads to the board, also at the desk). The desk is memory only; a restart forgets it, so a
player just talks to a staff member again. The Guildmaster's own office functions (Treasury withdrawals,
posting, cancelling and featuring commissions, announcements, activities) work anywhere, unchanged.
Caps, ranks and rewards are unchanged.

## Testing

- Unit tests (`./gradlew :guild:test`): ranks and promotion, board drawing, every contract kind's
  progress, caps and clipping, commission math, Treasury and withdrawal limits, commissions, trials,
  persistence round-trip, menus, every farm rule and the Grand Quest share cap and reroll, the First
  Dragon payout and who counts as a fighter, title tags (opt-in, updates, persistence, unearned titles
  never shown, the Guildmaster backfill) and the titles menu's show/hide button.
- `e2e/first-dragon.mjs` (private server): a sword hit, a bed blast and a `/damage ... by` each make a
  fighter, a member who only stands there and a non-member who hits get nothing, an offline fighter is
  paid and told on join, an op's `/kill` and a second dragon pay nobody. Checked once by hand on the
  same server (2026-10-09): a fighter's hit survives a restart mid-fight, the main island's own dragon
  pays its party, and after a full natural kill a restart with the record cleared logs the earlier
  kill and spends the event.
- `e2e/guild.mjs` (private server): registering and claiming a finished daily from `/guild` away from the
  hall are refused and cost or pay nothing; the same done after right-clicking Lyra pays; a commission
  is turned in beside her; `/guild titles show` puts the worn title before the name in another
  player's tab list and chat, and the titles menu's button takes it off again.
- Not testable by Claude: how menus look in a real 26.3 client.
