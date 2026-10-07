package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;

/**
 * Pure math for where the summon dragon hovers and which look it gets, kept free of Bukkit types
 * so both are unit-testable without a MockBukkit server. The Bukkit-coupled half (scanning the
 * actual world for a ceiling, placing the entity) lives in {@link DragonActor}.
 */
final class DragonPlacement {

    // The vanilla Ender Dragon's hitbox is 16x8x16 (W x H x W) at Attribute.SCALE 1.0; SCALE
    // multiplies every axis uniformly, so this is the number that tells us how much vertical room
    // a scaled body actually needs.
    static final double BODY_HEIGHT_AT_SCALE_1 = 8.0;

    private DragonPlacement() {
    }

    /**
     * Hover height (blocks above the summon centre) for a Nether dragon, given {@code
     * ceilingDistance} (blocks up to the first non-air block, or the scan's max range if none was
     * found). Prefers {@code clearance} blocks of headroom above the scaled body, capped at
     * {@code maxHeight}; falls back toward {@code minHeight} when the ceiling is close, but never
     * so low that the scaled body would poke into the ceiling itself - that hard limit always wins.
     */
    static double netherHoverHeight(double ceilingDistance, double scale, double maxHeight, double clearance, double minHeight) {
        double bodyTop = BODY_HEIGHT_AT_SCALE_1 * scale;
        double neverInsideBlocks = Math.max(0.0, ceilingDistance - bodyTop);
        double desiredWithMargin = Math.min(maxHeight, ceilingDistance - clearance - bodyTop);
        double withFloor = Math.max(desiredWithMargin, minHeight);
        return Math.min(withFloor, neverInsideBlocks);
    }

    /** A circling-motion amount (radius or bob) scaled down with the dragon's own body, so the
     * Nether's tighter headroom contains the motion too, not just the model. */
    static double scaleForNether(double baseAmount, double scale) {
        return baseAmount * scale;
    }

    /** Scale, circling motion and aura for {@code set}: Overworld is unchanged, the Nether shrinks
     * the dragon and its motion and wraps it in a smoke column, the End keeps full size and adds a
     * purple aura (Joe, 2026-10-07). */
    static Look lookFor(BallSet set, EffectsConfig config, double baseCircleRadius, double baseBobAmplitude) {
        return switch (set) {
            case NETHER -> {
                double scale = config.netherDragonScale();
                yield new Look(scale, scaleForNether(baseCircleRadius, scale), scaleForNether(baseBobAmplitude, scale),
                        true, false);
            }
            case END -> new Look(config.dragonScale(), baseCircleRadius, baseBobAmplitude, false, true);
            case OVERWORLD -> new Look(config.dragonScale(), baseCircleRadius, baseBobAmplitude, false, false);
        };
    }

    /**
     * @param scale        {@link org.bukkit.attribute.Attribute#SCALE} base value
     * @param circleRadius horizontal circling radius, in blocks
     * @param bobAmplitude vertical bob amplitude, in blocks
     * @param smokeColumn  Nether look: a black-smoke-and-ember column for the whole session
     * @param purpleAura   End look: a purple particle aura around the dragon for the whole session
     */
    record Look(double scale, double circleRadius, double bobAmplitude, boolean smokeColumn, boolean purpleAura) {
    }
}
