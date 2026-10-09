package dev.sirbepy.mc.endfight.reset;

/** How the most recent {@link IslandRestorer} run went: how many blocks actually differed and
 * were changed, and how long applying them took. */
public record RestoreStats(int blocksChanged, long millis) {
}
