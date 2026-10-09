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

Illager guards hold every outpost, balcony, gatehouse and wall spot the arena recorded in phase 1.
The dragon controls them and doesn't care if its own attacks hurt them - crystal blasts, wing/head
hits and breath attacks near a guard still hurt it like any other mob, same as vanilla.

- **Guards** spawn once a dragon is alive in the End, the arena is built, and a player has been
  within 160 blocks of 0,0 in the last couple of seconds - one per post, plus a bonus mob once per
  physical outpost/gatehouse (one vindicator per stair/ladder outpost tier, one evoker per
  gatehouse). They hold their post: no wandering off, and a guard that strays or falls too far with
  no target is walked or teleported straight back. They never hurt or target each other (the
  dragon's attacks and a player's are the only things that land). When the dragon dies, every
  surviving cultist vanishes with it in a puff of soul particles a couple of seconds later - unless
  `cult.die-with-dragon` is off.
- **Waves** pour out of all 4 fortress gates when the dragon's health crosses 75%, 50% and 25% of
  its (possibly datapack-raised) max health, once per threshold per dragon. Each wave is bigger
  with more players in the End near the portal, and a chat line announces it. A cap
  (`cult.max-alive`, default 60) on live wave mobs holds regardless of how big a wave's math says it
  should be; guards don't count toward it.
- Everything here is behind `cult.guards.enabled` / `cult.waves.enabled`, both on by default, plus
  the composition and timing knobs in `config.yml`'s `cult` section.

## Perch gate

The dragon is meant to be fought down, not landed on early: while any pillar still has a living
crystal, `endfight` keeps the dragon from phasing into landing on the exit portal (it stays in
whatever phase it was already in - circling, strafing, whatever - so the fight just continues).
Dying is never touched, and the 4 crystals players place on the exit portal during a dragon respawn
never count as "a pillar is still guarded". Off switch: `perch-gate.enabled`.

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
| `endfight cult status` | Cult mobs alive, which wave thresholds each tracked dragon has crossed |
| `endfight cult spawn` | Force-spawns guards now, ignoring the once-per-dragon rule |
| `endfight cult clear` | Removes every live cult mob (testing/repair) |

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
below), read when the dragon dies; while no dragon has ever died in this End it is Ferocious with
the first-kill row. `TierService` records the kill one tick after the death, so every listener
handling that death still sees it as the first.

**XP.** Every participant gets `Player#giveExp` on every kill, by tier, from `rewards.tiers.<tier>`
in `config.yml`: Fledgeling 500, Powerful 750, Cruel 1000, Immortal 1500, Ferocious 2000,
Primordial 3000, Ascended 4000 - except the server's actual first-ever kill (Ferocious), which pays
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
Ascended), and a chance at one bonus roll - all from `rewards.tiers.<tier>` in `config.yml`. A roll
chance of 1.0 is a single guaranteed roll; Ascended's 1.5 guarantees the first and spends the
remaining 0.5 on an independent second roll - if that second roll lands, the hoard gets no
netherite scrap. The server's actual first-ever kill uses the Ferocious row's materials with a
guaranteed roll (`rewards.first-kill.roll-chance: 1.0`) and counts as that week's Ferocious hoard.

A roll grants, in order (`RollEngine`, tracked per player in `rewards.yml`): a **Dragonscale** armor
piece the player doesn't own yet (plain netherite, no enchantments, `custom_model_data` string
"dragonscale" and equippable asset `claudesmp:dragonscale` - the same look shipped on main in
9d59217, keeping netherite's vanilla slot and equip sound); once all 4 pieces are owned, a **dragon
aura** they don't own yet (Dragon's Breath, Ender, Void Ember - an unlock, not an item, announced in
chat, worn one at a time with `/dragonaura <name|off>`, default permission true, API:
`DragonAuraService#toggle`, which the altar menu will call directly later); once all 3 auras are
owned, a **trophy head** they don't own yet (5 dragon/End-themed player heads, wired through
`core.item.Heads` the same way haru's own heads are - see `DragonTrophyHeads` for the texture
sourcing and verification trail); once everything is owned, a duplicate random armor piece.

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
Ferocious (850 HP) regardless of what the altar says, and the menu shows every tier locked with an
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

Commands: `/endfight tier status` (next pick, current fight's tier, first-kill-ever state) and
`/endfight tier set <tier>` (same as the altar, refused during the first-kill-ever lock).

### `DragonFighters` (core)

The attribution logic itself - melee/projectile/pet blame via the damage source, and the bed/anchor
blast-window matching - lives in `core/dragon/DragonFighters.java`, parameterised by the caller's
own `NamespacedKey` and clock. Guild's `DragonSlayers` and EndFight's `DragonDeathListener` each
hold their own instance under their own key; since `core` is shaded + relocated into each plugin
(see `mc.plugin-conventions`), the two copies never collide even on the same dragon's PDC.
