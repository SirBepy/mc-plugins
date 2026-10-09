package dev.sirbepy.mc.endfight.arena;

/**
 * The ground heights the fortress and the altar stand on, read from the island once, before the
 * arena first covers it, and kept in {@code state.yml} so every rebuild puts them back exactly
 * where they were. The End's main island is domed and its surface varies by more than the
 * fortress's foundations at the pillars' distance, so the pillars' own ground is no guide here.
 */
public record Terrain(int fortressY, int altarY) {
}
