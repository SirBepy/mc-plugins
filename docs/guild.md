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
| Contract volume | 3 dailies + 1 weekly per adventurer, plus rare contracts, bounties and Grand Quests, all inside the daily caps |
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
diamond at 20. A Shenron money wish is 100,000. So the 2,000/day cap is a ceiling nobody should reach;
real rewards sit an order of magnitude under it, and the Guild's value lives in progression, titles,
access and story rather than coins. A full E-Rank day (3 dailies) pays about 75 coins, roughly 2.5 stacks
of logs sold at the market; a full S-Rank day about 420 plus the weekly.

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

Pace: dailies pay 10-30 reputation by rank and a weekly about 4x a daily, so a perfect player gains
~35/day at E and ~90/day at A. D comes in about 4-5 days, C ~11, B ~3 weeks, A ~5 weeks, S ~7 weeks of
perfect play plus five trials; the 150-contract floor alone needs ~48 days at 3 dailies + 1 weekly.
Realistically S is a 2-3 month goal.

### Rank privileges (never stat buffs)

| Rank | Unlocks |
| --- | --- |
| E | 3 dailies + 1 weekly, 1 free reroll a day |
| D | rare contracts can appear (10% per daily slot); commissions default to D+ |
| C | 2 rerolls a day, the Nether opens on the board (fortresses, blazes, wart), Bounty Board (phase 3) |
| B | rare chance 15%, bastions, trial chambers and Incendium's named mobs on the board |
| A | 3 rerolls, ancient cities, monuments, mansions, Incendium's elites; Expedition contracts (phase 3, secret) |
| S | S-Rank title and announcement, bosses on the board, a place in the Hall of Fame, Legend contracts (secret) |

Promotions to D and C are told to the player; B, A and S are announced to the whole server.

## Contracts

Each adventurer has a personal board: **3 dailies** (new at midnight Europe/Zagreb) and **1 weekly**
(new Monday 00:00 Europe/Zagreb). Whatever is left when a new set is drawn expires. Daily slot 1 draws
from one rank below the adventurer (E stays E), slots 2-3 from their own rank; the weekly from their
own rank's weekly pool. No contract appears twice on a board, and the last day's dailies are avoided
when the pool allows, so a board rarely repeats.

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
bundle. The most a perfect day pays in levels is 3x3 + 5 = 14, under the 15 cap. Every claim is clipped
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

- `/guild` (everyone): the Guild menu (register, board, profile, commissions, office).
- `/guild profile [player]`, `/guild board`, `/guild announce`, `/guild commission ...`, `/guild cancel <id>`, `/guild feature <id>`.
- `/guildadmin` (ops, console, RCON): `status`, `member <player>`, `rep <player> <n>`, `contracts <player> <n>`,
  `newday <player>`, `newweek <player>`, `setrank <player> <rank>` and `resettrial <player>` (repairs only),
  `complete <player> <0|1|2|weekly|trial>` (testing), `treasury <n>`, `resetannounce`, `guildmaster <player>`,
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

## Secret: phase 3 content (planned, not built yet)

- **Hidden achievements and titles**, chosen from the profile as an active title: first contract ever
  claimed on the server; 50 fishing contracts; contracts of 10 different kinds; 25 distinct structure
  types visited while a member (tracked passively); a claim between 03:00 and 05:00; 10 commissions
  completed; finishing a weekly within an hour of it being drawn; dying three times inside one trial.
  Each announced to its finder only, with the title's line.
- **Expedition contracts** (A) and **Legend contracts** (S): short story chains (the Guild's founding
  charter, a lost S-Rank adventurer from before the server) with unique titles and cosmetics.
- **Bounties** (C+): one server-wide bounty at a time, first finisher claims it.
- **Grand Quests**: weekly server-wide goals (a Great Hunt, a Great Harvest, an Ore Rush); every
  contributor over a threshold earns reputation and coins inside their caps, the top contributor a title.
- **Hall of Fame**: first to each rank, most contracts, most reputation, longest daily streak, bounties.
- **Guildmaster activities**: a Hunting Season or Fishing Derby he can start, funded from the Treasury.

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

The staff are AI-less, invulnerable villagers with a name and a floating title, the same body for Java
and Bedrock; they turn to face the nearest visitor, can't be hurt, leashed, converted or traded with,
and a guard tick re-creates or snaps back anyone missing within 5 seconds while their chunk is loaded.

| Role id | Name | Title | Right-click opens |
| --- | --- | --- | --- |
| receptionist | Lyra Fennwick (cheerful, a little overworked, knows every file) | Guild Receptionist | registration for strangers, the board for members |
| clerk | Bram Ashgrove (grumpy retired adventurer, lost a finger to a Breeze) | Quest Clerk | the board (members) |
| treasurer | Silas Coinwright (fastidious, loves his ledger) | Guild Treasurer | the Guildmaster's office (Guildmaster only); others hear the balance |
| examiner | Captain Maren Holt (stern, fair, ex-soldier) | Trial Examiner | the board when a trial is open or running; otherwise says what is still needed |
| archivist | Pip Quillfeather (chatty, keeps every record) | Guild Archivist | talks Guild records; the Hall of Fame |

Dialogue depends on the first visit, the visitor's rank, finished-but-unclaimed work, a finished weekly,
rerolls, the trial state and time left, the Guildmaster, night and thunder. Sneak-click only talks.

## Testing

- Unit tests (`./gradlew :guild:test`): ranks and promotion, board drawing, every contract kind's
  progress, caps and clipping, commission math, Treasury and withdrawal limits, commissions, trials,
  persistence round-trip, menus.
- Not testable by Claude: how menus look in a real 26.3 client.
