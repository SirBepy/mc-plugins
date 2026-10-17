# RPG classes

The `classes` plugin is GutGr1nder's Shenron wish (`9366f4f8-b61e-4612-8644-f626a9a378bb`), granted
2026-10-10 17:20 UTC by the Overworld Shenron (session `3721nc`, logged in
`plugins/DragonBalls/wishes.jsonl`). The dragon's council voted GRANT_WITH_DEAL 5-0; GutGr1nder then
traded the daily class change for a fourth class. The terms below are final: a wish never goes to Joe
(docs/dragonballs.md, Judging).

## The wish (verbatim, from wishes.jsonl)

"Želim da server dobije RPG class system sa 7 klasa: Warrior, Guardian, Ranger, Rogue, Arcanist, Paladin
i Artificer. Svaka klasa ima mali pasivni bonus, jednu sposobnost s cooldownom i blagu slabost. Klase
trebaju biti balansirane uz postojeći AuraSkills, bez zabrane opreme, uz besplatnu promjenu klase jednom
dnevno." / "Želim da svaka klasa ima zaseban level od 1 do 50, koji raste igranjem u stilu te klase.
Levelanje postupno poboljšava pasivni bonus i posebnu sposobnost, uz ograničene maksimalne bonuse
balansirane oko AuraSkillsa. Svaka klasa pamti svoj napredak nakon promjene, a samo aktivna klasa daje
bonuse i dobiva XP." / "MOCNI ZMAJU AKO KAZEMO DA JE CLASSA LOCKED KAD JE IZABERES MOZES LI DODATI JOS
JEDNU KLASU SADA"

## The granted terms

- 4 classes at first: Warrior, Ranger, Arcanist, Artificer. Guardian, Rogue and Paladin were built
  later (2026-10-11), cashing in that promise via GutGr1nder's own Nether Shenron wish (session
  `k07gsq`): "unlock the other classes". No fresh council vote was needed since the terms were
  already granted; see "Guardian, Rogue and Paladin" below.
- Opt-in: a player with no class plays exactly as before (no passive, no ability, no class XP).
- Each class has its own level 1-25 (not the 50 asked for), earned by playing in that class's style.
  Every class keeps its progress across changes; only the active class gives bonuses and earns XP.
- Caps: a passive is at most +10% at level 25, a cooldown is at least 30 s, nothing is stronger than
  AuraSkills' own comparable stats and abilities, everything counts half against players, and there
  are no equipment bans.
- Class change: the first pick is free, then once per 7 real days (free). This replaced the daily
  change, in exchange for the fourth class.
- XP has a daily cap: 300 class XP per player per day.

## The classes

Every value grows in even steps from level 1 to level 25. "Half against players" means the
percentage is halved when the other side of the hit is a player (Weapon Master +10% is +5% on a
player). Abilities fire with a three-click combo while holding a class weapon (docs/classes-revamp.md,
Casting), `/class ability [1|2|3]`, the buttons in `/class` and `/class tree`, or the talent-tree
mod's optional ability keys.

| Class | Passive | Ability (cooldown) | Weakness | Class XP from |
| --- | --- | --- | --- | --- |
| Warrior | Weapon Master: +2% to +10% melee damage | Battle Cry: 10% to 20% less damage taken for 6 to 10 s (90 to 60 s) | Heavy Hands: arrows and tridents deal 10% less | 5 per hostile mob killed in melee, 50 per boss |
| Ranger | Steady Aim: +2% to +10% arrow and trident damage | Fleet Foot: Speed I for 8 to 15 s (90 to 60 s) | Thin Skin: 5% more damage from mobs' melee hits | 5 per hostile mob killed by an arrow or trident, 50 per boss |
| Arcanist | Insight: +2% to +10% XP from orbs | Mend: heal 1 to 3 hearts and cure poison (120 to 60 s) | Frail Body: 1 heart less max health | 1 per 4 orb XP points picked up, 5 per level spent enchanting |
| Artificer | Careful Hands: 2% to 10% chance a tool or armour piece takes no durability damage | Overclock: Haste I for 10 to 20 s (90 to 60 s) | Soft Hands: melee damage 5% lower | 2 per ore mined without Silk Touch, 1 per craft |
| Guardian | Stalwart: 2% to 10% less damage from mobs' melee hits | Last Stand: 10% to 20% less damage taken for 6 to 10 s (90 to 60 s) | Heavy Plate: 5% slower movement speed | 3 per hostile mob's melee hit survived, 10 per boss hit survived |
| Rogue | Sneak Attack: +2% to +10% melee damage while sneaking | Shadow Step: dash 3 to 6 blocks (90 to 60 s) | Light Fingers: 5% more damage from mobs' ranged attacks | 5 per hostile mob killed in melee while sneaking, 50 per boss |
| Paladin | Blessed Vigor: +2% to +10% healing received from any source | Smite: 2 to 6 hearts of damage to the nearest hostile mob, cures poison and wither (90 to 60 s) | Merciful Hand: melee damage 5% lower | 1 per 4 health points regenerated |

Details:

- "Melee" is a direct hit or a sweep; "hostile" is any monster (zombies, slimes, ghasts, phantoms,
  shulkers, hoglins, the dragon and the wither); bosses are the wither and the Ender Dragon. Mobs from a
  spawner give no class XP, the same rule as the Guild's farm guard.
- Battle Cry's and Last Stand's cuts are halved against player hits (arrows included); Thin Skin and
  Stalwart only count mobs, Light Fingers only counts mobs' ranged hits. Fleet Foot, Mend, Overclock,
  Last Stand, Shadow Step and Smite only touch the player using them (Smite's damage lands on the
  mob it hits, never a player).
- Frail Body and Heavy Plate are transient max-health and movement-speed modifiers (`classes:frail_body`,
  `classes:heavy_plate`), applied on join and on picking Arcanist or Guardian and removed on leaving,
  so neither is ever saved into the player file.
- Insight and Blessed Vigor carry fractions between orbs or heals, so a 2% bonus on a small amount
  still adds up over time. Careful Hands rolls once per durability loss.
- Ores: every `*_ORE` block and ancient debris. Silk Touch ores give nothing, since they can be placed
  and mined again. A craft counts once per craft action (a shift-click batch is one).

### Against AuraSkills

AuraSkills (server/config/plugins/AuraSkills, server/local/plugins/AuraSkills) stays the stronger
system at every comparable point:

| Class number at level 25 | AuraSkills comparison |
| --- | --- |
| Weapon Master / Steady Aim / Sneak Attack +10% damage | Strength: +0.4% attack damage per point, hand and bow damage (stats.yml), so 25 Strength already matches it |
| Battle Cry / Last Stand 20% for 10 s every 60 s | Toughness: 0.3% damage reduction per point, always on, against every damage type |
| Insight +10% orb XP | Wisdom: +0.4% experience per point |
| Careful Hands 10% | Unbreaking I alone saves 50% of a tool's durability loss |
| Overclock Haste I for 20 s every 60 s | Speed Mine (mana ability): Haste X |
| Fleet Foot Speed I for 15 s every 60 s | Speed: +1% movement speed per point, always on |
| Mend 3 hearts every 60 s | Regeneration stat, and Absorption (mana ability) |
| Stalwart 10% less melee damage from mobs, always on | Toughness: 0.3% per point (7.5% at 25) against every damage type, always on and broader in scope than Stalwart's melee-only, mobs-only cut |
| Shadow Step dash 6 blocks every 60 s | No direct AuraSkills analog; a one-shot repositioning tool on the same 90-to-60 s cooldown as every other ability, not a fight-ender |
| Smite 6 hearts to one mob every 60 s | No direct AuraSkills analog; mob-only (never a player) and the same size as Mend's self-heal |
| Blessed Vigor +10% healing received | Regeneration stat heals passively every tick already, independent of any heal landing; Blessed Vigor only amplifies healing that already happened |

## Guardian, Rogue and Paladin

GutGr1nder's original wish (session `3721nc`) asked for 7 classes but the council only built 4,
explicitly promising the rest "only via GutGr1nder's own future Nether or End Shenron wish". His
Nether Shenron wish (session `k07gsq`, 2026-10-11) said "unlock the other classes", cashing in that
exact promise, so the council didn't vote again: this was a deal already granted, not a new wish.

- Same caps as the first four: a passive at most +10% at level 25, a cooldown no shorter than 30 s,
  nothing stronger than AuraSkills' own comparable stats, half strength against players, no equipment
  bans, the same level 1-25 curve and the same XP-source pattern tied to how that class plays.
- Guardian is the tank: Stalwart and Last Stand both reduce damage taken (Last Stand mirrors Battle
  Cry's size and length exactly, just for a different class), Heavy Plate costs it mobility.
- Rogue is the striker: Sneak Attack only pays off while sneaking, Shadow Step is a short, one-shot
  dash rather than a sustained buff, and Light Fingers punishes it for skipping heavy armour.
- Paladin is the self-sustain class: Blessed Vigor amplifies any healing it receives, Smite is a
  small, mob-only burst (never touches a player) that also cures poison and wither, and Merciful Hand
  trades melee damage for that sustain.
- Not held to a stricter standard than the first four: where Stalwart could have matched Toughness's
  max percentage exactly (like Weapon Master matches Strength), it was set lower instead, so it stays
  safely under Toughness's always-on, every-damage-type reduction (see the table above).

## Dual-classing (Sivra21's wish)

Sivra21 (`9366f4f8-b61e-4612-8644-f626a9a378bb`) wished for a second class on top of his normal one;
granted by the Overworld Shenron and the council of 5 on 2026-10-11 (docs/dragonballs.md, Judging).
Bound to his UUID only, via `dual-class-grants` in config.yml, not a feature every player gets.

- `/class secondary <class>` picks a permanent secondary class alongside the normal, freely-swappable
  main one. It can be picked once, ever: no admin command, including `/class admin unlock` or
  `/class admin set`, can change or clear it afterwards.
- The secondary contributes half its passive bonus (rounded down) at its own level, and the FULL
  weakness, unhalved, since the weakness is the wish's cost. It gives no ability.
- The secondary only counts alongside an active main class; leaving the main class (`/class leave`)
  pauses the secondary's bonus and weakness too, without losing the permanent pick.
- Revocable without another wish: remove the UUID from `dual-class-grants` and restart. The player's
  stored pick stays in `players.yml` and resumes counting if the grant is ever restored.

## Talent trees

Every class has a 33-node talent tree, a resource (Rage, Focus, Arcana, Charge, Resolve, Energy, Faith)
on a boss bar, a second ability at level 10 and an ultimate at level 25: one talent point per level after
the first, spent in `/class tree`, refunded for free. docs/classes-revamp.md has every node, the rules
they follow and where the code differs from the spec. Ability 1 below is still the class's own; the
numbers in this file are before talents.

## Levels and XP

Level 1 to 2 takes 50 XP and every level 10 more (`Levels.toNext`: 40 + 10 x level), 3960 XP from 1 to
25. The daily cap (`daily-xp-cap`, 300) is per player across all classes and resets at midnight in
`zone` (Europe/Zagreb), so a player at the cap every day reaches 25 in 14 days. Players get a chat line
when they hit the cap, and a title and chat line on every level-up. XP past level 25 is not counted.

## Choosing and changing

- `/class` opens the menu: the seven classes with their numbers at the player's level, their progress,
  and whether they can be picked. Picking takes two clicks (the second confirms), since another class
  then waits 7 days. Chest menus reach Bedrock players through Geyser too.
- The first pick is free. Picking another class starts a 7-day lock (`change-days`) on picking any
  class but that one.
- `/class leave` drops the class at any time (progress kept). Going back to the class last picked is
  always free and does not restart the lock; any other class waits the lock out.
- Cooldowns and Battle Cry live in memory only, so a restart makes every ability ready.

## Showing the class

AuraSkills redraws its HP/mana action bar every 5 ticks, so the plugin never uses the action bar. The
spell bar (the resource and every ability's combo and cooldown) is a boss bar instead, always up for a
player with a class; `/class hud` hides and shows it. Players with the talent-tree mod get it drawn
next to the hotbar instead and no boss bar (docs/classes-revamp.md, Spell bar). Java
players see `Warrior · Level 7 (30/110 XP) · today 45/300 XP` as the tab list footer (none of our other
plugins sets one); everyone gets a chat line on join, a title on level-up, and the ability's message and a
"ready again" line in chat. The plugin adds nothing to names or nametags.

## Commands

| Command | Who | What |
| --- | --- | --- |
| `/class` | players | The menu |
| `/class choose <class>` | players | Pick a class (same rules as the menu, no confirm click) |
| `/class info [class]` | players | Passive, ability and weakness at your level |
| `/class levels` | players | All seven classes' levels |
| `/class ability [1\|2\|3]` | players | Use ability 1 (default), ability 2 or the ultimate |
| `/class tree [class]` | players | The talent tree (another class's is view only) |
| `/class hud` | players | Hide or show the spell bar (saved) |
| `/class leave` | players | Back to no class |
| `/class secondary <class>` | players with a dual-class grant | Pick the permanent secondary class (once, ever) |
| `/class admin info <player>` | `classes.admin` (ops, console) | Active class, today's XP, every class's XP and lock |
| `/class admin setxp <player> <class> <xp>` | admin | Set a class's XP (0-3960) |
| `/class admin set <player> <class\|none>` | admin | Force the active class (restarts the 7-day lock) or clear it |
| `/class admin unlock <player>` | admin | Lift the lock: the next pick is free |
| `/class admin today <player>` | admin | Reset today's earned XP |
| `/class admin ready <player>` | admin | Make every ability ready now |
| `/class admin tree <player> <class>` | admin | Spent points and every learned talent |
| `/class admin talent <player> <class> <talent> <rank>` | admin | Set a rank, ignoring points and gates |
| `/class admin respec <player> <class>` | admin | Refund a class's tree |
| `/class admin resource <player> <0-100>` | admin | Set the resource bar |

Admin commands take a name (online or seen before) or a UUID.

## Files

- `plugins/Classes/config.yml`: `daily-xp-cap`, `zone`, `change-days`, `dual-class-grants`,
  `tree-background`, `ability-icons`.
- `plugins/Classes/players.yml`: per UUID, the active class, the secondary class (if granted and
  picked), the last pick and when, each class's XP, today's earnings, every class's talent ranks and `spell-bar: false` for a player who hid the spell bar. Saved every minute when
  something changed, and on shutdown.

## Tests

- `:classes:test` (MockBukkit): the XP curve and caps, picking and the 7-day lock, leaving and going
  back, the daily cap at Zagreb midnight, every damage modifier and its half-against-players rule,
  Battle Cry and Last Stand, each class's XP source (including Guardian's survived hits and Paladin's
  healed points), Insight, Careful Hands, Blessed Vigor, Frail Body and Heavy Plate, the abilities and
  cooldowns (Shadow Step's dash, Smite's mob-only damage and cleanse), sneak + swap no longer casting, the footer, and
  the menu's two-click pick and lock display.
- `e2e/classes.mjs` (bots on the 26.2 e2e server): no class hits as before and earns nothing, the first
  pick and the lock, a melee kill's Warrior XP and the tab footer, a level-25 punch 10% harder, Battle
  Cry by the R-L-R sword combo (and not by sneak + swap) cutting a mob hit by 20% and then on cooldown, the menu's two-click Arcanist pick
  and its 9 hearts, Mend, leaving and going back free, Fleet Foot and Overclock effects, and Artificer
  ore XP. Not yet extended to Guardian, Rogue or Paladin (repo-only dispatch, 2026-10-11); covered by
  `:classes:test` for now.
