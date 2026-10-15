# Classes: talent trees

The talent trees on top of the RPG classes (docs/classes.md). Built 2026-10-15/16 from this spec, which
Joe approved on 2026-10-15; the node tables below are what shipped, and "As built" at the end lists every
place the code differs from a table and why.

Decisions (Joe, 2026-10-15):

- **Power budget:** always-on flat stats stay capped (the level passive, at most +10% at level 25).
  Tree nodes are conditional mechanics and may go past the old wish caps, but stay mob-focused.
- **Tree shape:** 3 branches per class, points spent freely across all 3, tier gates per branch.
- **Size:** 33 nodes per class, shown as a scrolling canvas, not a single 9x6 screen.
- **Extras:** a class resource on a boss bar, a 2nd ability at level 10, an ultimate at level 25,
  particles and sounds on every proc.
- **Keys:** sneak + a key, rebindable in `/class binds`, with a legend after holding sneak for 0.4 s.
- **Respec:** always free.
- **Look:** Java can get a painted tree background from the server resource pack (`tree-background`
  in config.yml, off until the pack carries the glyphs); Bedrock always gets the plain chest.

Unchanged: the 7 classes, levels 1-25 per class, the class XP sources, the 300 XP daily cap, the 7-day
change lock, the level passives and weaknesses, Sivra21's dual-class grant (the secondary still gives
half its passive and its full weakness, but no tree, abilities or resource).

## Rules every node follows

1. **Ability damage only ever hits mobs.** No class ability, node, turret, trap or summon damages,
   slows, roots or debuffs a player. Players are only hurt by the player's own normal hits and arrows.
2. **Half against players.** Any node that changes the damage of a normal melee hit or arrow counts
   half when the other side is a player, same as today. Self-buffs (Absorption, Resistance, speed) also
   count half in PvP where they reduce player damage.
3. **No flat always-on stats in the tree.** Every damage or defence bonus in a node has a condition
   (low HP, from behind, a poisoned target, while blocking...). The only unconditional stat is still
   the level passive.
4. **Bosses:** percentage-of-max-HP effects (Death Mark) are capped against the Wither, the Ender
   Dragon and Minibosses (numbers per node). Roots, stuns and taunts don't work on bosses.
5. **Claims:** every node that breaks or places blocks (Vein Miner, Tunneler, Seismic, Drill) checks
   the same build permission a normal block break does (Towny, spawn guard), block by block.
6. **Summons** (wolves, golem, polar bear, sentry, decoy) never drop loot or XP, never target
   players, vanish on logout, death, world change and restart, and are not saved to the world.
7. **Cooldowns** are at least 30 s for abilities 1 and 2, 5 min for ultimates.
8. **Spawner mobs** still give no class XP, and give no resource either.

## Points and tiers

- 1 talent point per level after the first: level N has N-1 points, 24 at level 25. Existing class
  levels carry over, so every class starts the revamp with its points unspent.
- Points belong to a class. Each of the 7 classes has its own tree and its own spent points.
- Most nodes have 1 to 3 ranks; one point buys one rank.
- Tier gates, per branch: a node's tier opens once that many points are spent in the same branch.

| Tier | Points in the branch | Nodes per branch | Notes |
| --- | --- | --- | --- |
| 1 | 0 | 2 | |
| 2 | 3 | 3 | |
| 3 | 6 | 3 | |
| 4 | 10 | 2 | |
| 5 (keystone) | 14, and level 20 | 1 | Replaces ability 1 or 2 with a new one. One keystone per class at a time |

- A branch holds about 24 ranks in total, so 24 points can nearly fill one branch or split across two.
- **Respec is free and unlimited:** a button in the tree menu refunds every point in that class.
  Keystones are refunded too, and the replaced ability comes back.

## Abilities, keys and the legend

| Slot | Unlocks | Default key | Notes |
| --- | --- | --- | --- |
| Ability 1 | Level 1 | Sneak + 1 | Today's ability (Battle Cry, Fleet Foot...). The old Sneak + F stays as an extra bind for it |
| Ability 2 | Level 10 | Sneak + 2 | New per class, see below |
| Ultimate | Level 25 | Sneak + 3 | Needs a full resource bar (100) and is off cooldown (5 min) |

- Sneak + a hotbar key fires the ability and cancels the slot change, so the held item stays put.
  Switching slots with the scroll wheel while sneaking still works.
- **Legend:** after holding sneak for 0.4 s the resource boss bar's text changes to the key legend,
  for example `[1] Battle Cry  [2] Charge (12s)  [3] Ground Slam (needs 100 Rage)`. Letting go of
  sneak puts the resource text back. Quick crouches while building never show it.
- **`/class binds`:** a menu where each of the 3 slots can be rebound to any input the server can
  see while sneaking: left click air, right click air, F (swap hands), Q (drop), or a hotbar key
  1-9. Left and right click are marked "may clash" (missed swings, shields, bows, food). Binds are
  saved per player and shared by all classes.
- Bedrock players: the same sneak + hotbar slot binds work through Geyser where the client sends a
  slot change; the tree menu also has a button per ability, and `/class ability 1|2|3` works for
  everyone.

## Resource bar

Each class has a resource from 0 to 100 shown as a boss bar while it is above 0 or the player is in
combat (hit or was hit by a mob in the last 8 s). It fills from playing the class's style, drains
5 per second after 10 s out of combat, and resets to 0 on death, class change and restart. The
ultimate spends the full 100. A few nodes spend part of it, which then delays the ultimate: that
trade is deliberate.

| Class | Resource | Bar colour | Fills from |
| --- | --- | --- | --- |
| Warrior | Rage | Red | +5 per melee hit on a mob, +3 per mob hit taken |
| Ranger | Focus | Green | +5 per arrow or trident hit on a mob, +10 more for a fully charged bow shot |
| Arcanist | Arcana | Purple | +1 per 2 orb XP points, +5 per enchanting level spent, +5 per ability hit on a mob |
| Artificer | Charge | Yellow | +2 per ore mined (no Silk Touch), +1 per 10 blocks broken with a tool, +5 per Sentry kill |
| Guardian | Resolve | Blue | +4 per mob hit taken, +3 per mob hit blocked with a shield |
| Rogue | Energy | White | +8 per melee hit while sneaking, +15 per backstab, +10 per kill |
| Paladin | Faith | Pink | +2 per heart healed (on self or an ally), +5 per undead killed |

Unlike the out-of-combat drain, the Artificer's Charge does not drain while mining (mining counts as
"in combat" for Charge only).

## The tree menu

- `/class tree` (and a button in `/class`) opens the active class's tree; `/class tree <class>` shows
  any class's tree read-only.
- A double chest (9x6). The top 5 rows show a window onto a 9-column by 15-row canvas; the bottom
  row holds the controls: scroll up, scroll down, points left, respec, binds, back to `/class`.
- Canvas layout: row 0 holds the 3 ability icons and the resource icon. Each branch owns 3 columns
  (Branch A columns 0-2, B 3-5, C 6-8). Tiers sit on rows 2, 5, 8, 11 and 14, with connector rows
  between them. The window starts at the top and scrolls by 5 rows (3 positions).
- Node icons: the item for that node, enchant glint when maxed, rank as the stack count, lore with the
  current and next rank's effect, a "Locked: spend N more points in <branch>" line when gated.
  Clicking a node spends a point (shift-click to refund, which is how the free respec works per node;
  the respec button refunds everything).
- **Java background:** the inventory title carries a custom font glyph from the server resource pack
  (a painted tree frame with branch lines and a parchment background), shifted with negative-space
  glyphs so it sits behind the slots. Empty canvas cells are invisible (air) so the painting shows.
  Each scroll position gets its own background glyph. Bedrock players (detected through Floodgate) get
  a plain title and coloured glass panes for the connectors instead.

## Effects and sounds

Every ability and every node proc plays a particle and a sound, visible to players within 32 blocks,
so other players can see a class in action. A full resource bar plays a one-time chime and puts a
faint particle ring around the player's feet until the ultimate is used. Level-ups and keystone
unlocks get a title and a firework-style burst. Exact particle and sound picks are part of the build,
reviewed in the bot screenshots, not this spec.

---

## Warrior (Rage)

- Ability 1 **Battle Cry** (today's).
- Ability 2 **Charge** (level 10): dash 6 blocks forward, the first mob hit is knocked back and
  slowed 2 s. 45 s.
- Ultimate **Ground Slam**: leap up and slam down, 6 hearts to every mob within 5 blocks, launches
  them, Slowness IV 2 s.

### Berserker: stronger the closer to death

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Bloodlust (3) | +4 / 8 / 12% melee damage to mobs while under half health |
| 1 | Thick Blood (2) | Rage drains 25 / 50% slower out of combat |
| 2 | Frenzy (3) | Melee hits on mobs within 3 s of each other stack +3% melee damage, up to 3 / 4 / 5 stacks |
| 2 | Reckless Swing (2) | Sweep hits deal +20 / 40% |
| 2 | Blood Price (2) | Battle Cry costs 1 heart, its cooldown is 10 / 20% shorter |
| 3 | Rampage (3) | Killing a mob gives 5 / 10 / 15 extra Rage |
| 3 | Savage Roar (2) | Battle Cry gives mobs within 5 blocks Weakness I for 3 / 5 s |
| 3 | Bloodthirst (2) | Melee kills heal 0.5 / 1 heart (once per 8 s) |
| 4 | Unstoppable (1) | Above 75 Rage, mobs can't knock you back |
| 4 | Fury Spike (2) | Charge deals 2 / 4 hearts to the first mob |
| 5 | **Undying Fury** (keystone, replaces Battle Cry) | Passive instead of a button: once per 5 min, a mob hit that would kill you leaves you at 1 heart with Resistance II for 3 s and refills Rage to 100 |

### Weaponmaster: tricks per weapon

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Cleave (3) | Axe hits splash 10 / 20 / 30% to mobs within 1.5 blocks of the target |
| 1 | Fencer (3) | Every 3rd sword hit on the same mob within 4 s deals +10 / 20 / 30% |
| 2 | Execute (3) | +5 / 10 / 15% melee damage to mobs under a quarter health |
| 2 | Sunder (2) | Axe hits make the mob take 5 / 10% more melee from everyone for 4 s |
| 2 | Riposte (2) | After blocking a mob hit with a shield, the next melee hit within 2 s deals +20 / 40% |
| 3 | Whirling Blades (1) | Sweeps also hit mobs behind you |
| 3 | Disarm (2) | 5 / 10% chance a melee hit makes a zombie, skeleton or piglin drop its held weapon |
| 3 | Momentum (2) | Sprint hits knock mobs 30 / 60% further and slow them 1 s |
| 4 | Bladestorm (2) | Charge hits every mob in its path for 50 / 100% of your weapon damage |
| 4 | Heavy Blow (1) | Fully charged axe hits stun a mob (Slowness IV) for 1 s, once per 6 s per mob |
| 5 | **Whirlwind** (keystone, replaces Battle Cry) | Spin for 2 s, hitting every mob within 3 blocks 4 times for 40% of your weapon damage. 60 s |

### Warlord: the group fighter

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Rally (3) | Battle Cry also covers allies within 4 / 6 / 8 blocks at half strength |
| 1 | Inspire (2) | Your mob kills heal allies within 6 blocks 0.5 / 1 heart (once per 5 s) |
| 2 | Taunt (2) | Battle Cry pulls mobs within 4 / 8 blocks onto you |
| 2 | War Drums (3) | Battle Cry gives you and allies Speed I for 3 / 5 / 7 s |
| 2 | Shield Wall (2) | While you hold up a shield, allies within 3 blocks take 5 / 10% less from mob arrows |
| 3 | Second Wind (3) | Battle Cry heals you 1 / 2 / 3 hearts |
| 3 | Veteran (2) | Battle Cry's cooldown is 5 / 10 s shorter |
| 3 | Formation (2) | +5 / 10% melee damage to mobs while an ally is within 5 blocks |
| 4 | Warhorn (1) | Rage gain doubles for 6 s after Battle Cry |
| 4 | Lead the Charge (2) | Charge gives allies within 6 blocks Speed II for 3 / 5 s |
| 5 | **War Banner** (keystone, replaces Charge) | Plant a banner for 15 s: you and allies within 6 blocks get Regeneration I and +50% resource gain, mobs within 6 blocks get Weakness I. 60 s |

## Ranger (Focus)

- Ability 1 **Fleet Foot** (today's).
- Ability 2 **Volley** (level 10): the next bow shot within 5 s fires 5 arrows in a fan. The extra
  arrows can't be picked up. 45 s.
- Ultimate **Arrow Rain**: arrows fall on a 5-block circle around the block you look at (up to 40
  blocks away) for 6 s, 1.5 hearts per arrow to mobs.

### Sharpshooter: precise long shots

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Long Shot (3) | Fully charged shots deal +4 / 8 / 12% to mobs more than 15 blocks away |
| 1 | Eagle Eye (2) | Fully charged hits make the mob glow (for you only) for 5 / 10 s |
| 2 | Piercing Shot (2) | Fully charged arrows pierce 1 / 2 mobs |
| 2 | Called Shot (3) | The first arrow on a mob at full health deals +10 / 20 / 30% |
| 2 | Recovery (2) | 20 / 40% chance an arrow that hits a mob comes back to your inventory |
| 3 | Marked (1) | Glowing mobs from Eagle Eye take +10% from all your hits |
| 3 | Deadeye (3) | Back-to-back arrow hits on the same mob stack +5%, up to 3 / 4 / 5 stacks |
| 3 | Wind Reader (2) | Your arrows fly 10 / 20% faster |
| 4 | Ricochet (2) | An arrow that kills a mob fires a new arrow at the nearest mob within 6 / 10 blocks |
| 4 | Sniper's Nest (1) | After standing still and sneaking for 1 s, shots deal +15% to mobs |
| 5 | **Railshot** (keystone, replaces Volley) | One beam along a 40-block line, 6 hearts to every mob on it, ignores armour. 45 s |

### Trapper: control the ground

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Snare (3) | Fleet Foot drops a snare at your feet: the first mob to step on it is rooted 1 / 2 / 3 s |
| 1 | Barbed Arrows (2) | Arrows slow mobs (Slowness I) for 2 / 4 s |
| 2 | Caltrops (2) | Volley arrows leave caltrops where they land: Slowness II for 3 / 5 s to mobs walking over them |
| 2 | Bola (3) | 5 / 10 / 15% chance an arrow roots a mob for 1 s |
| 2 | Kiting (2) | Hitting a mob within 5 blocks with an arrow gives you Speed I for 2 / 4 s |
| 3 | Explosive Snare (1) | Snares explode when triggered: 3 hearts to mobs within 2 blocks, no block damage |
| 3 | Netting (2) | Rooted mobs take +10 / 20% arrow damage |
| 3 | Survivalist (2) | Killing a rooted or slowed mob gives 5 / 10 extra Focus |
| 4 | Minefield (2) | Fleet Foot drops 2 / 3 snares in a line behind you |
| 4 | Hunter's Instinct (1) | Mobs within 16 blocks that start targeting you glow (for you) for 3 s |
| 5 | **Cage Shot** (keystone, replaces Volley) | An arrow that roots every mob within 4 blocks of where it lands for 4 s. 45 s |

### Beastmaster: fight with a pack

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Pack Bond (3) | Your tamed wolves deal +10 / 20 / 30% damage to mobs |
| 1 | Thick Fur (2) | Your tamed wolves take 15 / 30% less damage from mobs |
| 2 | Call of the Wild (2) | Fleet Foot also summons 1 / 2 temporary wolves for 15 s |
| 2 | Fetch (2) | Your wolves' kills give you 3 / 5 Focus |
| 2 | Mend Pet (3) | When you heal, your nearest wolf heals 1 / 2 / 3 hearts |
| 3 | Alpha Howl (1) | Volley also gives your wolves Strength I for 6 s |
| 3 | Feral (2) | Your wolves' bites slow mobs for 2 / 3 s |
| 3 | Pack Tactics (2) | +5 / 10% arrow damage to a mob your wolf is attacking |
| 4 | Loyal (1) | A wolf of yours that would die teleports to you with 2 hearts instead (once per 60 s per wolf) |
| 4 | Wild Growth (2) | Temporary wolves last 20 / 25 s and are 20 / 40% bigger |
| 5 | **Spirit Bear** (keystone, replaces Volley) | Summon a polar bear that fights mobs for you for 20 s. 60 s |

## Arcanist (Arcana)

- Ability 1 **Mend** (today's).
- Ability 2 **Arcane Bolt** (level 10): a homing bolt at the mob you look at (up to 20 blocks),
  4 hearts. 30 s.
- Ultimate **Meteor**: a meteor falls on the block you look at (up to 30 blocks), 10 hearts to mobs
  within 4 blocks, no block damage.

### Pyromancer: fire

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Kindle (3) | Arcane Bolt sets mobs on fire for 2 / 4 / 6 s |
| 1 | Ember Skin (2) | You take 25 / 50% less fire damage |
| 2 | Fireball (2) | Arcane Bolt bursts on impact for 50 / 100% of its damage to mobs within 2 blocks |
| 2 | Heat Wave (3) | Mend also deals 1 / 1.5 / 2 hearts to mobs within 3 blocks |
| 2 | Combust (2) | Burning mobs take +10 / 20% from your abilities |
| 3 | Flame Trail (1) | For 5 s after Arcane Bolt, mobs crossing your path catch fire |
| 3 | Inferno (3) | Burning mobs you kill burst for 1 / 1.5 / 2 hearts to mobs nearby |
| 3 | Pyre (2) | Meteor leaves burning ground for 3 / 6 s |
| 4 | Phoenix (1) | Mend heals 1 extra heart per burning mob within 6 blocks (up to 3) |
| 4 | Spreading Flame (2) | Fire from Arcane Bolt jumps to 1 / 2 nearby mobs |
| 5 | **Flamestrike** (keystone, replaces Mend) | A column of fire on the block you look at (up to 20 blocks), 5 hearts and 6 s of fire to mobs within 3 blocks. 45 s |

### Frost: slow and freeze

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Chill (3) | Arcane Bolt slows mobs (Slowness I) for 2 / 3 / 4 s |
| 1 | Ice Armour (2) | Mend also gives 1 / 2 Absorption hearts |
| 2 | Frost Nova (2) | Mend freezes mobs within 3 / 5 blocks in place for 2 s |
| 2 | Shatter (3) | Slowed or frozen mobs take +5 / 10 / 15% from your abilities |
| 2 | Brittle (2) | Mobs you slow also deal 10 / 20% less damage |
| 3 | Ice Lance (1) | Arcane Bolt passes through up to 3 mobs |
| 3 | Permafrost (2) | Your slows last 50 / 100% longer |
| 3 | Glacial Ward (3) | A mob that hits you has a 10 / 20 / 30% chance to freeze for 1 s |
| 4 | Hailstorm (2) | Meteor also slows mobs within 6 blocks for 4 / 8 s |
| 4 | Cold Snap (1) | Killing a slowed mob with Arcane Bolt takes 10 s off its cooldown |
| 5 | **Ice Tomb** (keystone, replaces Mend) | Encase yourself for 3 s: mobs can't hurt you (player damage halved), heal 4 hearts, then Frost Nova. 60 s |

### Chronomancer: time and movement

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Quick Mind (3) | Abilities 1 and 2 cool down 3 / 6 / 9% faster (never under 30 s) |
| 1 | Accelerate (2) | +25 / 50% Arcana gain |
| 2 | Blink (2) | Mend also teleports you 4 / 8 blocks forward (never into blocks) |
| 2 | Echo (3) | 5 / 10 / 15% chance Arcane Bolt fires a free second bolt |
| 2 | Slow Time (2) | Mend slows mobs within 4 blocks (Slowness II) for 2 / 4 s |
| 3 | Recall (1) | Pressing Arcane Bolt's key again within 5 s returns you to where you cast it |
| 3 | Temporal Shield (2) | After Mend, the next mob hit within 5 s is reduced 30 / 60% |
| 3 | Borrowed Time (2) | Every 10 / 5 orb XP points you pick up take 1 s off Arcane Bolt's cooldown |
| 4 | Overcharge (2) | Meteor refunds 20 / 40 Arcana |
| 4 | Quickening (1) | At full Arcana you have Speed I and Haste I |
| 5 | **Rewind** (keystone, replaces Mend) | Return to the health and position you had 5 s ago. 60 s |

## Artificer (Charge)

- Ability 1 **Overclock** (today's).
- Ability 2 **Sentry** (level 10): place a turret on the block you look at (up to 5 blocks) for 20 s.
  It fires an arrow at a hostile mob within 10 blocks every second, 1.5 hearts. One sentry at a time.
  60 s.
- Ultimate **Overdrive**: 15 s of Haste III, Speed II and Resistance I, and your Sentry fires twice as
  fast.

### Miner: dig faster and find more

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Vein Sense (3) | Mining an ore makes same-type ores within 3 / 5 / 7 blocks glow (for you) for 5 s |
| 1 | Prospector (2) | +1 / 2 extra Charge per ore |
| 2 | Vein Miner (3) | Mining an ore while sneaking also mines up to 4 / 8 / 12 connected ores of the same type (each costs durability) |
| 2 | Smelter (2) | 10 / 20% chance raw iron, gold or copper drops as an ingot |
| 2 | Magnet (2) | Drops from blocks you break fly to you from 4 / 8 blocks |
| 3 | Tunneler (1) | During Overclock, your pickaxe mines the block under the one you break too |
| 3 | Deep Pockets (2) | 5 / 10% chance an ore drops one extra item (stacks with Fortune) |
| 3 | Lucky Strike (2) | 1 / 2% chance mining stone or deepslate also drops coal, raw iron or raw copper |
| 4 | Seismic (2) | During Overdrive, pickaxes mine a 3x3 face (rank 2: shovels too) |
| 4 | Geode Finder (1) | Vein Sense also shows diamond ore and ancient debris within 8 blocks (once per 30 s) |
| 5 | **Drill** (keystone, replaces Sentry) | Place a drill that mines a 3x3 tunnel 8 blocks forward over 10 s, dropping everything. 90 s |

### Engineer: turrets and gadgets

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Calibrated (3) | Sentry deals +10 / 20 / 30% |
| 1 | Reinforced (2) | Sentry lasts 5 / 10 s longer |
| 2 | Multishot (2) | Sentry fires 2 / 3 arrows per shot |
| 2 | Tesla Coil (3) | Every 3 s Sentry zaps a mob for 1 heart, chaining to 1 / 2 / 3 more |
| 2 | Range Finder (2) | Sentry range +3 / 6 blocks |
| 3 | Jump Pad (1) | Overclock also places a jump pad at your feet for 10 s: it launches you and allies 8 blocks up, no fall damage on landing |
| 3 | Frost Rounds (2) | Sentry arrows slow mobs for 1 / 2 s |
| 3 | Repair Drone (2) | Sentry heals you 0.5 / 1 heart every 5 s while you are within 6 blocks |
| 4 | Mortar (2) | Every 5 s Sentry lobs a shell, 2 / 3 hearts to mobs within 2 blocks |
| 4 | Rapid Deploy (1) | Sentry's cooldown is 15 s shorter |
| 5 | **Golem** (keystone, replaces Overclock) | Build a small iron golem that fights mobs for you for 30 s. 90 s |

### Tinker: tools, mobility, toys

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Field Repair (3) | Overclock repairs your held item by 2 / 4 / 6% of its max durability |
| 1 | Last Chance (2) | When a tool would break, it is saved at 1 durability instead (once per 10 / 5 min) |
| 2 | Grapple (2) | A fishing rod hooked into a block pulls you to it, up to 20 / 30 blocks |
| 2 | Armour Kit (3) | Overclock also repairs your armour by 2 / 4 / 6% |
| 2 | Shock Absorber (2) | 25 / 50% less fall damage while holding a tool |
| 3 | Rocket Jump (1) | Overclock launches you 6 blocks forward and up, no fall damage on landing |
| 3 | Salvage (2) | 10 / 20% chance a tool or armour piece that breaks drops one of its materials |
| 3 | Flashbang (2) | Overclock blinds mobs within 4 / 6 blocks for 3 s |
| 4 | Power Cells (2) | +25 / 50% Charge gain |
| 4 | Thrusters (1) | While gliding with an elytra, Overclock gives a firework-strength boost |
| 5 | **Grav Gun** (keystone, replaces Sentry) | Pick up the mob you look at (not a boss, villager or tamed pet) and throw it 10 blocks, 3 hearts on landing. 45 s |

## Guardian (Resolve)

- Ability 1 **Last Stand** (today's).
- Ability 2 **Shield Bash** (level 10): knock back mobs in front of you (4 blocks, a 90 degree cone),
  1 heart and Slowness II for 3 s. 40 s.
- Ultimate **Fortress**: 8 s of 60% less damage from mobs (30% from players), no knockback, and every
  mob within 10 blocks targets you.

### Bulwark: the shield

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Shield Mastery (3) | Blocking a mob's melee hit reflects 10 / 20 / 30% of it back |
| 1 | Steady (2) | 25 / 50% less knockback while blocking |
| 2 | Thorned Shield (2) | Mobs that hit your shield are slowed for 1 / 2 s |
| 2 | Iron Will (3) | Last Stand also gives 1 / 2 / 3 Absorption hearts |
| 2 | Bash Mastery (2) | Shield Bash deals 2 / 4 hearts |
| 3 | Unbreakable (1) | During Last Stand, blocked hits don't wear your shield |
| 3 | Retaliation (2) | After blocking 3 mob hits within 5 s, the next melee hit deals +20 / 40% |
| 3 | Rooted (2) | Standing still while sneaking, you take 5 / 10% less from mobs |
| 4 | Aegis (2) | Blocking a mob's arrow sends it back at the shooter for 50 / 100% of its damage |
| 4 | Stone Skin (1) | No knockback during Last Stand |
| 5 | **Bastion** (keystone, replaces Shield Bash) | For 6 s every mob projectile within 4 blocks of you is deflected. 45 s |

### Protector: guard your friends

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Guardian Angel (3) | Allies within 4 blocks take 5 / 10 / 15% less from mobs; you take that part instead |
| 1 | Bodyguard (2) | When a mob hits an ally within 8 blocks, 10 / 20% chance it switches to you |
| 2 | Intervene (2) | Shield Bash on an ally (look at them) dashes you to them, up to 8 / 12 blocks, and gives them Resistance I for 3 s |
| 2 | Shared Stand (3) | Last Stand also covers allies within 2 / 4 / 6 blocks at half strength |
| 2 | Lifeline (2) | An ally within 8 blocks who drops under 3 hearts is healed 2 / 3 hearts (once per 90 s) |
| 3 | Sacrifice (1) | Fortress also covers allies within 6 blocks |
| 3 | Taunting Shout (2) | Shield Bash makes mobs within 6 / 10 blocks target you |
| 3 | Shared Resolve (2) | +3 / 6 Resolve when an ally near you is hit |
| 4 | Guardian Spirit (1) | Once per 10 min, an ally within 8 blocks who would die to a mob is saved at 1 heart, and you lose 4 hearts |
| 4 | Stalwart Aura (2) | Allies within 6 blocks take 25 / 50% less knockback from mobs |
| 5 | **Oath** (keystone, replaces Last Stand) | Link to the nearest ally for 10 s: you take half of what mobs deal them, and both of you get Regeneration I. 60 s |

### Juggernaut: crash through

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Momentum (3) | Sprinting into a mob knocks it back for 1 / 1.5 / 2 hearts (once per 3 s) |
| 1 | Plated (2) | Heavy Plate's slowness is halved / removed while you wear full iron armour or better |
| 2 | Shoulder Charge (2) | Shield Bash dashes you 4 / 6 blocks forward first |
| 2 | Bulldoze (3) | Mobs you knock into a wall take 1 / 2 / 3 hearts |
| 2 | Grit (2) | Each mob hit taken gives +2 / 4% melee damage for 5 s, up to 5 stacks |
| 3 | Earthshaker (1) | Landing from 4+ blocks deals 2 hearts to mobs within 3 blocks |
| 3 | Iron Fist (2) | With a shield in your offhand, melee hits slow mobs for 1 / 2 s |
| 3 | Unyielding (2) | Under 30% health, Resolve gain is doubled / tripled |
| 4 | Trample (2) | During Fortress, walking into mobs knocks them back for 2 / 3 hearts |
| 4 | Immovable (1) | No knockback from mobs while sneaking |
| 5 | **Leap Smash** (keystone, replaces Shield Bash) | Leap at the spot you look at (up to 12 blocks); landing deals 4 hearts to mobs within 3 blocks and knocks them up. 45 s |

## Rogue (Energy)

- Ability 1 **Shadow Step** (today's).
- Ability 2 **Smoke Bomb** (level 10): Invisibility for 4 s; mobs within 6 blocks lose their target
  and get Blindness for 3 s. 60 s.
- Ultimate **Death Mark**: mark the mob you look at (up to 20 blocks). After 4 s, or on your next hit,
  it takes 30% of its max health (up to 20 hearts; 5% on bosses).

### Assassin: strike from behind

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Backstab (3) | Melee hits on a mob from behind deal +10 / 20 / 30% |
| 1 | Cutthroat (2) | +10 / 20% melee damage to mobs that aren't targeting you |
| 2 | Ambush (2) | The first hit out of Invisibility deals +30 / 60% |
| 2 | Shadow Strike (3) | Shadow Step lands behind the mob you look at, up to 6 / 9 / 12 blocks |
| 2 | Finisher (2) | Kills take 5 / 10 s off Shadow Step's cooldown |
| 3 | Assassinate (1) | A backstab on a mob that isn't targeting anyone deals double |
| 3 | Swift Kill (2) | Kills give Speed II for 2 / 4 s |
| 3 | Precision (2) | Critical hits on mobs deal +10 / 20% |
| 4 | Chain Kill (2) | A kill within 2 s after Shadow Step makes it ready again (once per 30 / 20 s) |
| 4 | Vanishing Act (1) | Shadow Step leaves you invisible for 2 s |
| 5 | **Phantom Blades** (keystone, replaces Smoke Bomb) | Teleport between up to 4 mobs within 8 blocks, hitting each with your weapon. 60 s |

### Shadow: unseen and agile

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Light Feet (3) | No fall damage from falls under 4 / 6 / 8 blocks |
| 1 | Quiet Steps (2) | While sneaking, mobs further than 8 / 5 blocks don't notice you |
| 2 | Double Jump (2) | Press jump in mid-air to jump again, once per 8 / 4 s |
| 2 | Smoke Screen (3) | Smoke Bomb reaches 8 / 10 / 12 blocks |
| 2 | Evasion (2) | 5 / 10% chance to dodge a mob's melee hit completely |
| 3 | Wall Climb (1) | Sneak against a wall to climb it, up to 6 blocks |
| 3 | Decoy (2) | Smoke Bomb leaves a copy of you that mobs attack for 3 / 6 s |
| 3 | Night Stalker (2) | At night or in darkness (light level 7 or less), +5 / 10% melee damage to mobs |
| 4 | Shadow Dance (2) | Shadow Step holds 2 charges (rank 2: they recharge 25% faster) |
| 4 | Ghost (1) | Smoke Bomb also hides your armour and held items |
| 5 | **Shadow Form** (keystone, replaces Shadow Step) | For 4 s mobs can't target you, you move 30% faster, and your first hit ends it with +50% damage. 60 s |

### Venom: poison everything

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Poison Blade (3) | Melee hits have a 10 / 20 / 30% chance to poison a mob (Poison I, 3 s) |
| 1 | Toxicologist (2) | Poison on you lasts 50% shorter / not at all |
| 2 | Envenom (2) | Poisoned mobs take +10 / 20% melee damage from you |
| 2 | Toxic Cloud (3) | Smoke Bomb's cloud poisons mobs inside for 1 / 2 / 3 s each second |
| 2 | Weakening Toxin (2) | Poisoned mobs deal 10 / 20% less damage |
| 3 | Wither Edge (1) | Undead (immune to poison) get Wither I instead |
| 3 | Contagion (2) | A poisoned mob that dies passes its poison to 1 / 2 mobs nearby |
| 3 | Potent (2) | Your poison is level II (rank 2: lasts 2 s longer too) |
| 4 | Plague (2) | Death Mark also marks 1 / 2 nearby mobs at half strength |
| 4 | Antidote (1) | Killing a poisoned mob heals you 1 heart |
| 5 | **Viper Lunge** (keystone, replaces Shadow Step) | Dash to the mob you look at (up to 8 blocks), Poison II and Weakness I for 6 s. 45 s |

## Paladin (Faith)

- Ability 1 **Smite** (today's).
- Ability 2 **Holy Light** (level 10): heal yourself and allies within 6 blocks 2 hearts. 60 s.
- Ultimate **Judgement**: 8 hearts to every undead within 8 blocks (4 to other hostile mobs), heals
  allies within 8 blocks 4 hearts and removes poison and wither.

### Light: heal the group

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Radiance (3) | Any heal you receive also heals allies within 4 blocks 25 / 50 / 75% of it |
| 1 | Gentle Touch (2) | Holy Light heals 1 / 2 hearts more |
| 2 | Beacon (2) | Holy Light leaves a glowing zone for 6 / 10 s that heals allies inside 0.5 heart every 2 s |
| 2 | Purify (3) | Holy Light removes 1 / 2 / all bad effects |
| 2 | Sanctuary (2) | Allies you heal take 5 / 10% less from mobs for 4 s |
| 3 | Lay on Hands (1) | Holy Light heals double on an ally under 4 hearts |
| 3 | Aura of Light (2) | Allies within 6 blocks heal 0.5 heart every 10 / 6 s |
| 3 | Divine Favour (2) | +50 / 100% Faith from healing |
| 4 | Guardian Light (1) | Once per 30 min, an ally within 8 blocks who would die to a mob is saved at 2 hearts |
| 4 | Halo (2) | Judgement heals allies 2 / 4 hearts more |
| 5 | **Lightwell** (keystone, replaces Holy Light) | Plant a well for 12 s that heals allies within 5 blocks 1 heart every 2 s. 75 s |

### Crusader: smite the undead

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Undead Bane (3) | +5 / 10 / 15% melee damage to undead |
| 1 | Holy Weapon (2) | Smite deals 1 / 2 hearts more |
| 2 | Consecrate (2) | Smite leaves holy ground for 4 / 6 s that burns undead for 1 heart a second |
| 2 | Zeal (3) | Melee kills on undead take 5 / 10 / 15% off Smite's cooldown |
| 2 | Righteous Fury (2) | A mob hit by Smite takes +10 / 20% melee damage from you for 5 s |
| 3 | Chain Smite (1) | Smite jumps to 2 more mobs at half damage |
| 3 | Exorcist (2) | Undead hit by Smite burn for 3 / 6 s |
| 3 | Sun Blade (2) | Outdoors in daylight, +5 / 10% melee damage to undead |
| 4 | Hammer of Justice (2) | Smite stuns for 1 / 2 s |
| 4 | Crusade (1) | Judgement reaches 14 blocks |
| 5 | **Divine Storm** (keystone, replaces Smite) | Lightning (visual only, no fire) strikes up to 4 mobs within 8 blocks for 3 hearts each, double on undead. 60 s |

### Martyr: pay with your own health

| Tier | Node (ranks) | Effect per rank |
| --- | --- | --- |
| 1 | Self-Sacrifice (3) | Holy Light costs you 1 heart but heals others 1 / 2 / 3 hearts more |
| 1 | Endurance (2) | Merciful Hand is halved / gone while you are under half health |
| 2 | Shield of Faith (2) | Holy Light gives you 2 / 4 Absorption hearts |
| 2 | Penance (3) | +1 / 2 / 3 Faith per heart of mob damage you take |
| 2 | Blood Oath (2) | Under 30% health, Smite heals you 2 / 3 hearts |
| 3 | Divine Bubble (1) | Once per 20 min, a mob hit that would kill you leaves you at 1 heart, and mobs can't hurt you for 3 s |
| 3 | Martyrdom (2) | When you die, allies within 8 blocks heal 4 / 8 hearts |
| 3 | Ember of Hope (2) | Under 4 hearts you heal 0.5 heart every 4 / 2 s |
| 4 | Last Rites (2) | Judgement deals 2 / 4 hearts more while you are under half health |
| 4 | Unbroken (1) | You are immune to poison and wither |
| 5 | **Ascension** (keystone, replaces Holy Light) | Give up half your current health; allies within 8 blocks heal twice that and get Resistance I for 5 s. 60 s |

---

## Data

- `players.yml` keeps per class the spent ranks (`tree.<class>.<talent>: <rank>`) and per player the
  key binds (`binds.<slot>: <input>`). Resource, cooldowns and summons stay in memory only.
- Existing class levels carried over: every class started the trees with 0 spent and N-1 points.
- A class whose XP an admin lowers below its spent points is refunded on the player's next join.

## As built

Where the code differs from the tables above, and why:

- **Glow "for you only"** (Eagle Eye, Hunter's Instinct): Paper has no per-viewer glow, so the mob
  glows for everyone. Vein Sense and Geode Finder can't glow blocks at all, so they show a particle
  burst on each ore instead.
- **"The block / mob you look at"**: found by walking the look direction block by block, or by a cone
  search for mobs, instead of Bukkit ray traces (MockBukkit can't run those).
- **Freezes and roots** are heavy Slowness (amplifier 9), never `Mob#setAware(false)`: that flag is
  saved with the mob and would survive a restart.
- **Railshot** deals its 6 hearts through normal ability damage, so the kill is credited and the
  Ender Dragon's death still runs, at the cost of armour applying.
- **Heavy Blow** stuns on any axe hit: the attack's charge isn't visible to the damage event.
- **Seismic** breaks the 3x3 around the broken block on its own level (the clicked face isn't known).
- **Drill** refuses a fixed list of unbreakable blocks (Paper has no hardness getter) and stops at the
  first block a claim refuses.
- **Rapid Deploy** shortens slot 2's cooldown, so it also shortens Drill's when Drill replaced Sentry.
- **Golem** is a normal-size iron golem (no size API for golems).
- **Recall**: casting Arcane Bolt with Recall learned returns you to the cast spot after 5 s unless you
  sneak in that time (the engine has no "same key again" hook).
- **Shadow Dance**: a single stored charge that skips one Shadow Step cooldown and recharges over
  Shadow Step's own cooldown (25% faster at rank 2), not two independent charges.
- **Knockback nodes** (Steady, Stone Skin, Immovable, Stalwart Aura, Fortress, Unstoppable) only cut
  knockback from hostile mobs, through Paper's push events, never from players.
- **Guardian Spirit**'s 4-heart cost never takes the Guardian below 1 heart; **Ascension** never takes
  the Paladin below half a heart.
- **War Drums** covers allies within 8 blocks (the table gives no radius).
- **Holy Light, Smite and other heals** set health directly, so they don't feed Blessed Vigor or the
  Paladin's heal XP twice; Faith is added per heal instead.
- **Allies** are other players within range who haven't traded a hit with you in the last 10 s.
- **Double Jump** uses allow-flight; a marker saved on the player hands that flight back at plugin
  disable or on the next join after a crash, and never touches creative or Essentials flight.

## Tests

- `:classes:test` (MockBukkit): the engine (points, gates, respec, resource, slots, binds, summons,
  allies, the tree menu) and every node, ability and keystone of all 7 kits.
- `e2e/classes-talents.mjs` (bots on the 26.2 e2e server): the tree menu learns a node and scrolls,
  `/class binds`, sneak + hotbar 2 fires ability 2 without moving the held slot, the Rage boss bar, and
  every class's ability 2 and ultimate hitting mobs but never a bystander bot.
