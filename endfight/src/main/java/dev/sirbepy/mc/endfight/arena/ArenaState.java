package dev.sirbepy.mc.endfight.arena;

import java.util.List;

/** {@code state.yml}'s content: just enough to rebuild {@link ArenaRegistry} on a restart without
 * touching the world (no chunks needed) - the actual block list is always recomputed from
 * {@link ArenaLayout#build}, never stored. {@code terrain} is null until the first build has read
 * the island's ground. */
public record ArenaState(boolean built, int layoutVersion, List<Pillar> pillars, Terrain terrain) {

    public static final int CURRENT_LAYOUT_VERSION = 1;

    public static final ArenaState EMPTY = new ArenaState(false, CURRENT_LAYOUT_VERSION, List.of(), null);
}
