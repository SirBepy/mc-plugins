package dev.sirbepy.mc.dragonballs.config;

/**
 * One rung of the post-wish difficulty ladder: the overworld scatter annulus radius, and how many
 * of the 7 balls go to the Nether/End instead (the rest stay in the overworld).
 */
public record DifficultyTier(int minRadius, int maxRadius, int netherCount, int endCount) {
}
