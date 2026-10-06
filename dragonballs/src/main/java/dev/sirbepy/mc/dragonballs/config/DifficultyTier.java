package dev.sirbepy.mc.dragonballs.config;

/**
 * One rung of the post-wish difficulty ladder: the overworld scatter annulus radius. Every set
 * always scatters in its own home dimension now (the Nether reuses this radius through
 * {@code NetherScale}, the End keeps its own end-min/max-distance from 0,0 regardless of tier), so
 * there's no per-dimension split here anymore.
 */
public record DifficultyTier(int minRadius, int maxRadius) {
}
