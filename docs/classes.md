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

- 4 classes: Warrior, Ranger, Arcanist, Artificer. Guardian, Rogue and Paladin are not built; only
  GutGr1nder's own future Nether or End Shenron wish can add them.
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
player). Abilities fire with `/class ability`, the menu's nether star, or sneak + swap hands (F, Java);
the swap is cancelled, so the items stay where they are.

| Class | Passive | Ability (cooldown) | Weakness | Class XP from |
| --- | --- | --- | --- | --- |
| Warrior | Weapon Master: +2% to +10% melee damage | Battle Cry: 10% to 20% less damage taken for 6 to 10 s (90 to 60 s) | Heavy Hands: arrows and tridents deal 10% less | 5 per hostile mob killed in melee, 50 per boss |
| Ranger | Steady Aim: +2% to +10% arrow and trident damage | Fleet Foot: Speed I for 8 to 15 s (90 to 60 s) | Thin Skin: 5% more damage from mobs' melee hits | 5 per hostile mob killed by an arrow or trident, 50 per boss |
| Arcanist | Insight: +2% to +10% XP from orbs | Mend: heal 1 to 3 hearts and cure poison (120 to 60 s) | Frail Body: 1 heart less max health | 1 per 4 orb XP points picked up, 5 per level spent enchanting |
| Artificer | Careful Hands: 2% to 10% chance a tool or armour piece takes no durability damage | Overclock: Haste I for 10 to 20 s (90 to 60 s) | Soft Hands: melee damage 5% lower | 2 per ore mined without Silk Touch, 1 per craft |

Details:

- "Melee" is a direct hit or a sweep; "hostile" is any monster (zombies, slimes, ghasts, phantoms,
  shulkers, hoglins, the dragon and the wither); bosses are the wither and the Ender Dragon. Mobs from a
  spawner give no class XP, the same rule as the Guild's farm guard.
- Battle Cry's cut is halved against player hits (arrows included); Thin Skin only counts mobs. Fleet
  Foot, Mend and Overclock only touch the player using them.
- Frail Body is a transient max-health modifier (`classes:frail_body`), applied on join and on picking
  Arcanist and removed on leaving it, so it is never saved into the player file.
- Insight carries fractions between orbs, so 2% of small orbs still adds up. Careful Hands rolls once
  per durability loss.
- Ores: every `*_ORE` block and ancient debris. Silk Touch ores give nothing, since they can be placed
  and mined again. A craft counts once per craft action (a shift-click batch is one).

### Against AuraSkills

AuraSkills (server/config/plugins/AuraSkills, server/local/plugins/AuraSkills) stays the stronger
system at every comparable point:

| Class number at level 25 | AuraSkills comparison |
| --- | --- |
| Weapon Master / Steady Aim +10% damage | Strength: +0.4% attack damage per point, hand and bow damage (stats.yml), so 25 Strength already matches it |
| Battle Cry 20% for 10 s every 60 s | Toughness: 0.3% damage reduction per point, always on |
| Insight +10% orb XP | Wisdom: +0.4% experience per point |
| Careful Hands 10% | Unbreaking I alone saves 50% of a tool's durability loss |
| Overclock Haste I for 20 s every 60 s | Speed Mine (mana ability): Haste X |
| Fleet Foot Speed I for 15 s every 60 s | Speed: +1% movement speed per point, always on |
| Mend 3 hearts every 60 s | Regeneration stat, and Absorption (mana ability) |

## Levels and XP

Level 1 to 2 takes 50 XP and every level 10 more (`Levels.toNext`: 40 + 10 x level), 3960 XP from 1 to
25. The daily cap (`daily-xp-cap`, 300) is per player across all classes and resets at midnight in
`zone` (Europe/Zagreb), so a player at the cap every day reaches 25 in 14 days. Players get a chat line
when they hit the cap, and a title and chat line on every level-up. XP past level 25 is not counted.

## Choosing and changing

- `/class` opens the menu: the four classes with their numbers at the player's level, their progress,
  and whether they can be picked. Picking takes two clicks (the second confirms), since another class
  then waits 7 days. Chest menus reach Bedrock players through Geyser too.
- The first pick is free. Picking another class starts a 7-day lock (`change-days`) on picking any
  class but that one.
- `/class leave` drops the class at any time (progress kept). Going back to the class last picked is
  always free and does not restart the lock; any other class waits the lock out.
- Cooldowns and Battle Cry live in memory only, so a restart makes every ability ready.

## Showing the class

AuraSkills redraws its HP/mana action bar every 5 ticks, so the plugin never uses the action bar. Java
players see `Warrior · Level 7 (30/110 XP) · today 45/300 XP` as the tab list footer (none of our other
plugins sets one); everyone gets a chat line on join, a title on level-up, and the ability's message and a
"ready again" line in chat. The plugin adds nothing to names or nametags.

## Commands

| Command | Who | What |
| --- | --- | --- |
| `/class` | players | The menu |
| `/class choose <class>` | players | Pick a class (same rules as the menu, no confirm click) |
| `/class info [class]` | players | Passive, ability and weakness at your level |
| `/class levels` | players | All four classes' levels |
| `/class ability` | players | Use the active class's ability |
| `/class leave` | players | Back to no class |
| `/class admin info <player>` | `classes.admin` (ops, console) | Active class, today's XP, every class's XP and lock |
| `/class admin setxp <player> <class> <xp>` | admin | Set a class's XP (0-3960) |
| `/class admin set <player> <class\|none>` | admin | Force the active class (restarts the 7-day lock) or clear it |
| `/class admin unlock <player>` | admin | Lift the lock: the next pick is free |
| `/class admin today <player>` | admin | Reset today's earned XP |
| `/class admin ready <player>` | admin | Make the ability ready now |

Admin commands take a name (online or seen before) or a UUID.

## Files

- `plugins/Classes/config.yml`: `daily-xp-cap`, `zone`, `change-days`.
- `plugins/Classes/players.yml`: per UUID, the active class, the last pick and when, each class's XP,
  and today's earnings. Saved every minute when something changed, and on shutdown.

## Tests

- `:classes:test` (MockBukkit): the XP curve and caps, picking and the 7-day lock, leaving and going
  back, the daily cap at Zagreb midnight, every damage modifier and its half-against-players rule,
  Battle Cry, each class's XP source, Insight, Careful Hands, Frail Body, the abilities and cooldowns,
  sneak + swap, the footer, and the menu's two-click pick and lock display.
- `e2e/classes.mjs` (bots on the 26.2 e2e server): no class hits as before and earns nothing, the first
  pick and the lock, a melee kill's Warrior XP and the tab footer, a level-25 punch 10% harder, Battle
  Cry by sneak + swap cutting a mob hit by 20% and then on cooldown, the menu's two-click Arcanist pick
  and its 9 hearts, Mend, leaving and going back free, Fleet Foot and Overclock effects, and Artificer
  ore XP.
