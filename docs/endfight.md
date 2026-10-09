# EndFight

`endfight` redesigns the End main island into an illager-cult fortress around the 10 vanilla
end-spike pillars and the exit portal. Phase 1 built the structures themselves, their protection,
and rebuilding them after a dragon respawn regenerates the pillars. Phase 2 (this doc's Cult and
Perch gate sections) adds the illager cult itself - guards, waves, and a gate that keeps the dragon
fighting instead of perching early.

Built from the approved mockup (`.for_bepy/end-build/gen_island.py` and its renders), verified
against vanilla's own end-spike/cage geometry by disassembling the 26.3 server jar
(`net.minecraft.world.level.levelgen.feature.EndSpikeFeature`).

## What's built

- **The 5 tallest pillars** get a 2-wide spiral staircase up to a top balcony, with outposts at
  roughly a third and two-thirds of the climb.
- **The 5 shortest pillars** get a ladder strip, a halfway outpost, a top balcony, and the vanilla
  cage shape (iron bars) around every one of their crystals - not just the 2 vanilla already
  guards.
- **The fortress ring** around the exit portal: a crenellated curtain wall, 4 gatehouses (N/E/S/W)
  with carved gate openings, 4 watchtowers, real ominous banners (the vanilla raid-captain
  pattern), soul-fire braziers, gate arches and hanging soul lanterns.
- Guard posts (for the cult phase to use later) on every outpost, balcony, gatehouse roof and a few
  wall walkway spots, plus one wave spawn point just inside each gate.

## Protection and rebuilding

Every block the arena places is tracked and protected: it survives the dragon's own wall-breaking,
end crystal explosions, beds/respawn anchors going off, and fire. Non-admins can't break it by
hand.

When vanilla regenerates the end-spike pillars (a dragon respawn), it clears a square footprint
around each pillar above y=65 and rebuilds the obsidian cylinder from scratch - anything of ours
sitting on that footprint would be eaten. `EndFight` schedules a rescan + full rebuild ~2 seconds
after a fresh Ender Dragon spawns in the End, so the structures reappear on the regenerated
pillars. (The stair/outpost geometry itself is shaped to stay outside that footprint in the first
place - see `Geometry.squarePoint`'s javadoc for why it isn't a perfect circle like the mockup.)

## Building it the first time

Live keeps `allow-end: false` until Joe opens it, so the End world doesn't exist there at all until
then. `EndFight` auto-builds the arena the moment it does see an End world with nothing built yet
(on plugin enable if the world already exists, or the moment it loads later) - safe to install on
live before the End is open. Set `arena.auto-build: false` in `config.yml` to require a manual
`endfight build` instead.

## The cult

The dragon's army holds every outpost, balcony, gatehouse and wall spot the arena recorded in
phase 1. The dragon controls it and doesn't care if its own attacks hurt it - crystal blasts,
wing/head hits and breath attacks near a cultist still hurt it like any other mob, same as vanilla.

- **Two factions** (Joe, 2026-10-09). Walking the ring of pillars by angle around the portal,
  every other pillar is Nether-themed (`ArenaLayout.factions`): nether brick floors, blackstone
  stair supports, crimson stairs and fences, a shroomlight on each outpost, a soul fire on soul
  soil near each outpost's rim, and a black piglin war banner. Those are held by piglins, with a
  brute on every outpost tier and balcony. The other 5 keep the illager look and are held by
  crossbows and axes: a pillager on every post, a vindicator on every outpost tier and balcony.
  The fortress ring is always the illagers' (an evoker on each gatehouse). Piglins never turn
  into zombified piglins in the End and never spawn as babies.
- **Guards** spawn once a dragon is alive in the End, the arena is built, and a player has been
  within 160 blocks of 0,0 in the last couple of seconds - one per post, plus the bonus mob once
  per physical outpost/gatehouse. They hold their post: no wandering off, and a guard that strays
  or falls too far with no target is walked or teleported straight back. A guard with no player
  within `cult.guards.sleep-range` (48) has its AI switched off until one comes back or something
  hurts it. They never hurt or target each other (the dragon's attacks and a player's are the only
  things that land). When the dragon dies, every surviving cultist vanishes with it in a puff of
  soul particles a couple of seconds later, and any wave mobs still queued never come - unless
  `cult.die-with-dragon` is off.
- **Waves** follow an HP schedule (`cult.waves.schedule`), each wave once per dragon, the first
  time its health drops to that fraction of its (possibly datapack-raised) max:

  | At | Wave | Comes from |
  | --- | --- | --- |
  | 90% | Scouts: 2 pillagers + 1 per 2 fighters, 1 vindicator + 1 per 3 | the 4 fortress gates |
  | 75% | Pillager squad: 4 pillagers + 1 per fighter, 2 vindicators + 1 per 2; 2 piglins + 1 per 2 | gates; piglins dropped off the dragon |
  | 50% | 3 phantoms on each of the top 6 dragon damage dealers; 1 brute + 1 per 4 | over each of those players; brutes off the dragon |
  | 35% | 1 evoker + 1 per 4, 1 ravager + 1 per 4, 2 vindicators + 1 per 2; 1 brute + 1 per 3 | gates; brutes off the dragon |
  | 15% | Nether paratroopers: 4 + 1 per 2 | gliding off the dragon |

  "Fighters" are the players actually fighting: alive, survival or adventure, within 160 blocks
  of 0,0. Gate mobs march out at the nearest fighter. Nether troops dropped off the dragon start 6
  blocks under it (its wings and head hurt what they touch), pulled in to 60 blocks of the portal
  if it is out over the void, and float down with slow falling until they land. Paratroopers are
  Incendium's castle reinforcements (piglin brutes in gold armor, 50 health, fast) launched off
  the dragon towards the nearest fighter on elytras that never drop. Phantoms start 14 blocks over
  the player they were sent after. Piglins and brutes are made angry at their player (their brain
  ignores `Mob#setTarget`, javap on the 26.3 jar). Each wave has its own chat line.
- **Performance** (Joe's rules, 2026-10-09, for ~15 players): at most `cult.max-alive` (150) wave
  mobs alive at once, queued ones included (guards don't count); a wave's mobs come out over
  `cult.waves.spread-ticks` (50 ticks) instead of all in one tick; and nothing new spawns while
  the server's average tick time is over `cult.waves.max-mspt` (40 ms) - the queue just waits.
- **Tower wardens** (Joe, 2026-10-09). Every pillar whose crystal still stands always has 2
  phantoms circling 8 blocks over its top (Paper's phantom anchor), swooping at whoever climbs it.
  A killed warden comes back on the next top-up, every 30 s, while its crystal stands; once the
  crystal breaks nothing replaces them. They are cult mobs (type `phantom`) but neither guards nor
  wave mobs, so they never sleep and never count against the wave cap. `cult.wardens`.
- **Mob counter.** A boss bar under the dragon's counts the army still alive, guards included,
  with the title naming the biggest types ("Dragon's army: 42 left (18 Pillagers, 9 Piglins,
  ...)"). It is shown only to players who already see the dragon's own bar, so the client draws
  it underneath, and only while a dragon lives and the army isn't empty. AuraSkills owns the
  action bar, so it can't go there. Off switch: `cult.counter.enabled`.
- **Fight tallies** (`fight.FightStats`, on the dragon's PDC like the fighter list): damage dealt
  to the dragon per player (blamed by the same `DragonFighters` rules; a killing blow counts only
  the health that was left) and cult kills per player per type (`pillager`, `piglin_brute`,
  `paratrooper`, `phantom`...). The 50% phantoms use the damage ranking; the extra rewards for the
  top dealer and the top killer of each type read them too. `/endfight cult stats` prints them.
- Everything here is behind `cult.guards.enabled` / `cult.waves.enabled`, both on by default, plus
  the composition and timing knobs in `config.yml`'s `cult` section.

## Perch gate

The dragon is meant to be fought down, not landed on early: while any pillar still has a living
crystal, `endfight` keeps the dragon from phasing into landing on the exit portal (it stays in
whatever phase it was already in - circling, strafing, whatever - so the fight just continues).
Dying is never touched, and the 4 crystals players place on the exit portal during a dragon respawn
never count as "a pillar is still guarded". Off switch: `perch-gate.enabled`.

## Summoning a new dragon

Joe's decisions, 2026-10-09. Placing the 4 end crystals on the exit portal starts vanilla's own
dragon respawn sequence; from that moment EndFight takes the whole island out of play until the
new dragon is standing there:

1. **Lockout.** The instant `RespawnWatcher` sees `DragonBattle#getRespawnPhase()` leave `NONE`,
   every player in the End within 80 blocks (horizontal) of 0,0 gets pulled onto the obsidian
   arrival platform (vanilla's own `ServerLevel.END_SPAWN_POINT`, built if it was ever mined out)
   and told to wait there. Survival/adventure players only - creative/spectator are left alone.
   That sweep repeats every 20 ticks for as long as the respawn + restore take, so nobody can walk
   back onto the island mid-reset.
2. **Full island restore.** Once the new dragon has actually spawned, the ENTIRE main island (not
   just the arena's own structures) is set back to exactly how it looked right after the very
   first build: anything dug out comes back, anything players built gets removed, the fortress and
   pillars reappear. This only runs if a snapshot exists (see below) and `reset.enabled` is true;
   otherwise behaviour is unchanged from phase 1's own per-pillar rebuild. It never touches
   entities - the new crystals, players and items all stay exactly where they are. Once every
   differing block is back in place, every remaining cult mob is cleared, the arena is
   re-registered (from the saved pillar/terrain state - no rescan needed, the snapshot already
   contains the arena), and everyone in the End is told "The arena is ready."
3. **The snapshot.** `IslandSnapshot`/`IslandSnapshotStore` (package `reset`) capture every block
   in x -64..64, z -64..64, the world's own min height up to 160, as a palette of `BlockData`
   strings plus one index per block, GZIP-compressed to `plugins/EndFight/island-snapshot.bin`.
   Taken automatically right after the very first arena build ever completes, and only if no
   snapshot file already exists - never again after a rebuild, and never overwritten by a later
   auto-trigger once one exists.

Commands (admin): `endfight snapshot` retakes it on demand; `endfight restore` runs the restore
right now; `endfight respawn` (testing) puts the 4 summoning crystals around the exit portal the
way players do and starts the sequence with them (`DragonBattle#initiateRespawn(crystals)`: the
no-argument one is vanilla's tryRespawn and does nothing without real crystals, and 26.2 aborts an
empty-crystal respawn); `endfight reset status` shows the watcher's phase, vanilla's own respawn
phase, the snapshot's presence/size/age, and the last restore's stats. Verified end to end on a
private 26.2 server (e2e/endfight.mjs): the respawn locked the fighter onto the platform, the new
dragon spawned about 30 s later, and the restore put back 1386 blocks in 409 ms.

## Commands

All require `endfight.admin` (default: op).

| Command | What it does |
| --- | --- |
| `endfight status` | Built?, pillar count, arena block count, guard post count |
| `endfight build` | Scans the pillars and builds now |
| `endfight rebuild` | Same as build - re-places everything |
| `endfight clear` | Removes every tracked arena block (testing/repair) |
| `endfight reload` | Reloads `config.yml` |
| `endfight rewards status` | Pending reward entries, the current dragon's fighter count, and the current tier |
| `endfight rewards test <player> [tier]` | Gives that player one Dragon Hoard through the live delivery chain (default: the current tier), no XP, ignoring the weekly limit |
| `endfight cult status` | Cult mobs alive (by type), which wave thresholds each tracked dragon has crossed, mobs still queued |
| `endfight cult stats` | This fight's damage per player and the top killer of each mob type |
| `endfight cult spawn` | Force-spawns guards now, ignoring the once-per-dragon rule |
| `endfight cult clear` | Removes every live cult mob (testing/repair) |
| `endfight snapshot` | Retakes the whole-island snapshot now |
| `endfight restore` | Runs the whole-island restore now |
| `endfight respawn` | Places the 4 summoning crystals on the portal and starts a dragon respawn (testing) |
| `endfight reset status` | Watcher phase, vanilla's respawn phase, snapshot presence/size/age, last restore stats |

## Rewards (phase 3)

Joe's full reward spec, 2026-10-09. Behind `rewards.enabled` in `config.yml` (default `true`, live
for the first fight, Saturday 2026-10-10).

**Who counts.** A participant is a player who *damaged the dragon* - nothing else. Standing in the
End, healing someone, or being the one who ultimately gets credit as "killer" without ever landing
a hit does not count on its own; dying's own participant set is fighters (melee, anything they shot
or threw, TNT they lit, a crystal they broke, a bed/respawn anchor they set off within 2s/2 blocks)
plus the dragon's killer. Fighter attribution is the same `DragonFighters` class the Guild's First
Dragon event uses (moved to `core`, see below) under EndFight's own PDC key
(`endfight:dragon-fighters`), so both plugins agree on who fought without re-implementing the rules
differently. An op's `/kill` and a dragon dying outside the End spend nothing, exactly like the
Guild's own filter.

**Tier.** The tier the altar locked in when this dragon spawned (`TierService`, see Altar & tiers
below), read when the dragon dies; while no dragon has ever died in this End it is Ascended with
the first-kill row. `TierService` records the kill one tick after the death, so every listener
handling that death still sees it as the first.

**XP.** Every participant gets `Player#giveExp` on every kill, by tier, from `rewards.tiers.<tier>`
in `config.yml`: Fledgeling 500, Powerful 750, Cruel 1000, Immortal 1500, Ferocious 2000,
Primordial 3000, Ascended 4000 - except the server's actual first-ever kill (Ascended), which pays
12000 (`rewards.first-kill.xp`). The weekly Dragon Hoard limit (below) never touches XP.

Vanilla's own XP used to be read by cancelling the dragon's death-animation `ExperienceOrb` spawns
and summing them (javap on `EnderDragon.tickDeath()` showed them spawned directly through a
cancellable `EntitySpawnEvent`), but that does not actually work: a live kill on the e2e server
(Paper 26.2) showed the orbs landing on the ground 7-10s after death with the fighter's XP bar
never moving, and the summed total reading 0 - either the spawn event never fires for them, or
Paper/vanilla folds the dragon's XP into orbs already lying on the ground instead of creating new
ones. Since the per-tier table decides the XP amount now, EndFight only needs the orbs gone, not
read: `OrbSweep` removes every live `ExperienceOrb` within 24 blocks of the dragon's death location
on every tick for ~12s after death (plus once immediately, to catch orbs already lying there) -
cultists and endermen dying farther than 24 blocks away keep their own orbs.

**Dragon Hoard.** Each fighter gets at most one Dragon Hoard *per tier per week* (the week resets
Monday 00:00 Europe/Zagreb - a stable date key, not a week-of-year number, so it survives a DST
edge). A fighter who already has that tier's hoard this week gets XP only, with a chat line saying
so. The hoard is a `minecraft:black_shulker_box` with an enchantment glint, named "Dragon Hoard"
(dark purple, not italic), lore naming the tier and the date, holding (in its own 27-slot
inventory): diamonds, shulker shells, enchanted golden apples, netherite scrap (only Primordial and
Ascended), bulk filler (iron, gold, emeralds, lapis and golden carrots: Fledgeling 12/12/3/16/12 up
to Ascended 96/96/36/128/56, 10ca's spec), and a chance at bonus rolls - all from
`rewards.tiers.<tier>` in `config.yml`. Every
whole 1.0 of roll chance is a guaranteed roll and the remainder one more independent chance
(Fledgeling's 0.10 is a 10% chance at one roll, Ascended's 1.5 one roll plus 50% at a second),
never more than 4 rolls; a hoard with 2+ rolls gets no netherite scrap. The server's actual
first-ever kill is paid like the Ascended dragon it is (rewards follow the dragon summoned, Joe
2026-10-09): the Ascended row with 12000 XP, counting as that week's Ascended hoard.

**Layout** (`HoardLayout`, the radial layout Joe approved from 10ca's renders). The centre slot
holds the best thing in the box: the final-blow helm, else an armor roll, else the best valuable.
With an odd number of specials (helm and roll items) the first takes the centre, the helm always
does, and the rest go in pairs above and below it, then left and right. Everything else spreads
outward in mirrored groups of slots by value (apples, scrap, diamonds, shells, emeralds, carrots,
lapis, gold, iron), each count split evenly over its group, and leftover groups take extra filler
stacks until every slot is used. Nothing is ever dropped: a single item takes part of a group, and
a full box takes slots off the biggest filler.

**Final blow** (10ca's spec). Whoever deals the dragon's lethal damage (the hit at least as big as
its health left, blamed like any hit; the game's killer otherwise) gets a **Dragon Head Helm**:
plain netherite, no enchantments, `custom_model_data` "dragon_head" (the vanilla dragon head
model, 10ca 6535d64), no equippable asset so the client draws the model on the head. It sits in the
hoard's centre slot, or is delivered alone (inventory, ender chest, feet) when they get XP only.

**Crystals.** Every pillar end crystal a player breaks this fight (blamed like a hit on the dragon,
once per crystal; the 4 summoning crystals on the portal never count) adds 5% to their armor-roll
chance, solo fights included, like the honours only in a hoard actually given.

**Fight honours** (10ca's spec, Joe decided 2026-10-09; `fight.FightHonours`). Announced in chat at
the end of each kill, to every participant online and everyone in the End: **Dragon's Bane** for
the most damage to the dragon, and one honour per mob group (`HonourGroup`: Cultists = pillagers,
vindicators, evokers and the cult's ravagers; Piglins = piglins and brutes; Phantoms;
Paratroopers) for the most kills of it. Only in a fight with 2+ participants; a group honour needs
3+ kills; a tie goes to whoever reached the count first. An honour only raises its holder's
armor-roll chance in this kill's hoard (Bane +0.5, each group honour +0.25, on top of the tier's
chance), so it does nothing for a player who already took this tier's hoard this week. Damage and
kills come from the fight tallies (see The cult).

**Hit confirmation** (Joe, 2026-10-09). The hit that first makes a player a fighter this fight
gets them a chat line and a level-up ding, once per fight: "Your Dragon Hoard is secured for this
fight", or that they'll get the XP when they already took this tier's hoard this week. Only a hit
that does damage counts (`DragonFighters` ignores 0-damage hits, so a snowball never makes a
fighter; any arrow does).

A roll grants, in order (`RollEngine`, tracked per player in `rewards.yml`): a **Dragonscale** armor
piece the player doesn't own yet (plain netherite, no enchantments, `custom_model_data` string
"dragonscale" and, on the chestplate, leggings and boots, equippable asset `claudesmp:dragonscale` -
the same look shipped on main in 9d59217; the helmet has no asset, so the client draws its 3D
dragon-skull item model on the head (10ca, 50d1de2). Every piece sets its own slot and netherite's
equip sound on the equippable, since Paper hands a fresh meta a HEAD-slot default (javap on 26.3
`CraftMetaItem.getEquippable`)); once all 4 pieces are owned, a **dragon
aura** they don't own yet (Dragon's Breath, Ender, Void Ember - an unlock, not an item, announced in
chat, worn one at a time with `/dragonaura <name|off>`, default permission true, API:
`DragonAuraService#toggle`, which the altar menu will call directly later); once all 3 auras are
owned, a **trophy head** they don't own yet (5 dragon/End-themed player heads, wired through
`core.item.Heads` the same way haru's own heads are - see `DragonTrophyHeads` for the texture
sourcing and verification trail); once everything is owned, a duplicate random armor piece.

**Dragon's Presence** (Joe, 2026-10-09; `DragonPresence`). A full-set aura, no roll needed: while a
player wears the Dragonscale chestplate, leggings and boots plus a dragon helmet (the Dragonscale
helm or the Dragon Head Helm), slow purple dragon-breath wisps drift around them with an occasional
end rod or portal sparkle, drawn alongside any won aura. On by default; `/dragonaura presence` or
the altar's Auras menu turns it off, remembered in `plugins/EndFight/presence.yml`. Hidden for
spectators and invisible players. Aura particles go through `AuraParticles`, which hands
`dragon_breath` its power float: Paper refuses that particle without it on 26.x.

**Delivery.** Online: the hoard goes to the player's inventory, else their ender chest, else drops
at their feet, and they're told which in chat along with the XP amount. Offline at the kill: the
same chain runs on their next join (one second later), from `plugins/EndFight/pending-rewards.yml`
- entries survive a restart and are removed only once actually delivered, so nothing pays twice and
nothing is lost to being offline. The hoard is rolled once, at kill time (not re-rolled on
delivery), so a player's owned-armor/aura/head progress stays correct even while they're offline.

**Not from EndFight.** The Guild's coins, reputation and the Dragonslayer title (First Dragon event,
`guild/events/FirstDragon.java`) are paid separately by the Guild plugin, to Guild members only, and
only for the server's first dragon ever. Dragon's breath, elytra, damage-share/head-count scaling
and Dragonkind Evolved's own portal drop are not part of the hoard either. EndFight's XP + Dragon
Hoard go to every participant, Guild member or not, on every kill.

## Altar & tiers

Joe's decisions, 2026-10-09. The dragon's difficulty tier - `dev.sirbepy.mc.endfight.tier.Tier`,
Dragonkind Evolved's own order and health (Fledgeling 250 HP to Ascended 1250, see `Tier.java`) -
is picked by players, not by DKE's own kill-count math, which EndFight overrides every fight.

**The altar.** A small crying-obsidian dais (`AltarStructures`) stands just inside the fortress
courtyard, north of the exit portal. Right-clicking its lodestone opens the "Dragon Altar" menu: one
icon per tier, click to set it as the pick for the **next** fight (announced to everyone currently in
the End), plus an "Auras" slot for a later phase. A choice made while a dragon is alive only takes
effect on the fight after it - the tier locks in the moment a fresh dragon spawns
(`TierApplyListener`/`TierService#lockForSpawn`), not when the altar is clicked.

**First kill ever.** The server's actual first Ender Dragon kill (Saturday 2026-10-10) is forced to
Ascended (`Tier.FIRST_KILL`, 1250 HP, Dragonkind's max: "the strongest boss fight there is", Joe
2026-10-09) regardless of what the altar says, and the menu shows every tier locked with an
explanation until then. "No dragon has ever died in this End" combines two signals, either one
enough to clear it: `World#getEnderDragonBattle().hasBeenPreviouslyKilled()` (behind
`EndDragonBattleHistory`, since MockBukkit throws for that call) and EndFight's own persisted flag in
`tier.yml`, set the moment a real kill is detected in the End (independently of `reward/`'s own kill
listener, since this module doesn't depend on that package).

**Pushing the tier to DKE.** DKE ticks every dragon once a second looking for one it hasn't started
yet, and starts/buffs it the moment it finds one - there's no gap to intercept mid-chain. So EndFight
lets DKE go first, waits past that window (`tiers.apply-delay-ticks`), then re-runs DKE's own
`dke:dragons/dragon_buff_<tier>` function on the dragon (after `dke:dragons/reset_tag`, so the
re-run's cosmetic type reskin doesn't stack with DKE's first pass) - safe because that function always
does an absolute `attribute ... base set`/`data modify ... Health set value`, never a relative
modifier, so re-running it after DKE's own pass is idempotent. See `DkeTierPusher`'s class docs for
the full research (tier name mapping, exact tick timing, why pre-seeding DKE's own kill-count scores
was rejected). Whichever dragon vanilla had already spawned before this plugin could register a
listener (the live End's very first one) is caught once on enable, marked with a PDC flag so a
restart never re-applies.

**Without DKE.** If the datapack isn't installed (checked via Bukkit's datapack list, with a
`tiers.dke.force-installed` override if the real filename doesn't match the "dke"/"dragonkind"
heuristic), EndFight sets the dragon's max health and health directly to `Tier#health()` instead.
Either way, the dragon's actual max health is logged once applied.

**The 1024 cap.** spigot.yml's `settings.attribute.maxHealth.max` caps every max health, 1024 by
default, below Ascended's 1250: a fallback push threw ("Health value (1250.0) must be between 0 and
1024.0", e2e server log) and Dragonkind's own buff would be clamped the same way. The repo's
`server/config/spigot.yml` raises it to 2048, and the fallback now heals only up to the max the
dragon actually got.

Commands: `/endfight tier status` (next pick, current fight's tier, first-kill-ever state) and
`/endfight tier set <tier>` (same as the altar, refused during the first-kill-ever lock).

### `DragonFighters` (core)

The attribution logic itself - melee/projectile/pet blame via the damage source, and the bed/anchor
blast-window matching - lives in `core/dragon/DragonFighters.java`, parameterised by the caller's
own `NamespacedKey` and clock. Guild's `DragonSlayers` and EndFight's `DragonDeathListener` each
hold their own instance under their own key; since `core` is shaded + relocated into each plugin
(see `mc.plugin-conventions`), the two copies never collide even on the same dragon's PDC.
