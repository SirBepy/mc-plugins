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
