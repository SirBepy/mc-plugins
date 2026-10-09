package dev.sirbepy.mc.endfight.arena;

import java.util.List;

/** Everything {@link PillarStructures} builds for one pillar. */
record PillarResult(List<Placement> placements, List<GuardPost> guardPosts, List<Tread> treads) {
}
