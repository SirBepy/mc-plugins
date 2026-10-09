package dev.sirbepy.mc.endfight.arena;

import java.util.List;

/** Everything {@link FortressStructures} builds: the curtain wall, 4 gatehouses, 4 watchtowers. */
record FortressResult(List<Placement> placements, List<GuardPost> guardPosts, List<WaveSpawn> waveSpawns) {
}
