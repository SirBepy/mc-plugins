# EndFight

`endfight` redesigns the End main island into an illager-cult fortress around the 10 vanilla
end-spike pillars and the exit portal. This is phase 1: the structures themselves, their
protection, and rebuilding them after a dragon respawn regenerates the pillars. Later phases add
the illager cult (guards, waves) and rewards on top of the guard posts and wave spawns this phase
already records.

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

### `DragonFighters` (core)

The attribution logic itself - melee/projectile/pet blame via the damage source, and the bed/anchor
blast-window matching - lives in `core/dragon/DragonFighters.java`, parameterised by the caller's
own `NamespacedKey` and clock. Guild's `DragonSlayers` and EndFight's `DragonDeathListener` each
hold their own instance under their own key; since `core` is shaded + relocated into each plugin
(see `mc.plugin-conventions`), the two copies never collide even on the same dragon's PDC.
