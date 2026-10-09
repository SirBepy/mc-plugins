package dev.sirbepy.mc.endfight.arena;

/** One stair tread of a tall pillar's spiral staircase, kept around purely so tests can walk the
 * climb order and verify no gap a player would have to jump (mirrors gen_island.py's
 * measure_stair_gaps). {@code lane} is "inner" or "outer". */
public record Tread(int pillarCx, int pillarCz, String lane, int x, int y, int z) {
}
