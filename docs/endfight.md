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
| `endfight rewards status` | Pending reward entries, and the current dragon's fighter count |
| `endfight rewards test <player>` | Gives that player one reward bundle through the live delivery chain, no XP |
| `endfight cult status` | Cult mobs alive, which wave thresholds each tracked dragon has crossed |
| `endfight cult spawn` | Force-spawns guards now, ignoring the once-per-dragon rule |
| `endfight cult clear` | Removes every live cult mob (testing/repair) |

## Rewards (phase 3)

Joe's decisions, 2026-10-09. Behind `rewards.enabled` in `config.yml` (default `true`, live for the
first fight, Saturday 2026-10-10).

**Who counts.** A participant is a player who *damaged the dragon* - nothing else. Standing in the
End, healing someone, or being the one who ultimately gets credit as "killer" without ever landing
a hit does not count on its own; dying's own participant set is fighters (melee, anything they shot
or threw, TNT they lit, a crystal they broke, a bed/respawn anchor they set off within 2s/2 blocks)
plus the dragon's killer. Fighter attribution is the same `DragonFighters` class the Guild's First
Dragon event uses (moved to `core`, see below) under EndFight's own PDC key
(`endfight:dragon-fighters`), so both plugins agree on who fought without re-implementing the rules
differently. An op's `/kill` and a dragon dying outside the End spend nothing, exactly like the
Guild's own filter.

**XP.** Every participant gets the dragon's full vanilla XP straight into their XP bar: 12000 on
the server's actual first Ender Dragon kill, 500 on every kill after (vanilla's own numbers).
Vanilla never routes this through `EntityDeathEvent#getDroppedExp` - javap on `EnderDragon
.tickDeath()` (26.3's `paper-26.3.jar`) shows it spawning `ExperienceOrb` entities directly in 10
batches during the death animation, each going through a cancellable spawn event before ever
reaching the world. `EndFight` cancels every one of those orbs in the dying dragon's world and
sums their `getExperience()` as it does, then calls `Player#giveExp` with the total once the
batches are done - giving the exact amount vanilla decided to award, deterministically, with no
dependency on `DragonBattle` (which a dragon's own `tickDeath` also doesn't re-read at that point,
and which `World.getEnderDragonBattle()` cannot even be unit-tested against here - MockBukkit's
`WorldMock` throws unconditionally). No orbs reach the ground; nobody races for drops.

**Bundle.** Every participant gets one reward bundle (a `BUNDLE` item) built from `rewards.bundle`
in `config.yml` - a display name, lore and a flat item list (material/amount, optional name and a
small fixed set of enchantments). The shipped bundle is a **placeholder** ("Dragon Hoard"): its
real contents are still being designed in another chat, and the config comment says so.

**Delivery.** Online: the bundle goes to the player's inventory, else their ender chest, else drops
at their feet, and they're told which in chat along with the XP amount. Offline at the kill: the
same chain runs on their next join (one second later), from `plugins/EndFight/pending-rewards.yml`
(UUID -> list of `{xp, bundles}` entries, one per missed fight) - entries survive a restart and are
removed only once actually delivered, so nothing pays twice and nothing is lost to being offline.

**Not from EndFight.** The Guild's coins, reputation and the Dragonslayer title (First Dragon event,
`guild/events/FirstDragon.java`) are paid separately by the Guild plugin, to Guild members only, and
only for the server's first dragon ever. EndFight's XP + bundle go to every participant, Guild
member or not, on every kill.

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
