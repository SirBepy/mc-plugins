package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.registry.Tier;

/**
 * Every number the Overgrown Sentinel's attack kit is balanced with, in one place. Radii marked
 * "base" are multiplied by the tier's body size ({@code RigMath.tierSize}: 1.0, 1.2, 1.4) where the
 * attack uses them; damage multiples apply to the boss's own scaled attack damage.
 */
public final class SentinelTuning {

    private SentinelTuning() {
    }

    /** The value that belongs to {@code tier}. */
    public static <T> T byTier(Tier tier, T normal, T heroic, T mythic) {
        return switch (tier) {
            case NORMAL -> normal;
            case HEROIC -> heroic;
            case MYTHIC -> mythic;
        };
    }

    /**
     * Base damage when the boss has no attack-damage attribute (a test double): the Normal row of
     * the Guardian file. A live Ravager always has the attribute, so this never decides a real hit.
     */
    public static final double FALLBACK_BASE_DAMAGE = 8.0;

    // --- Stonebreaker Slam ---

    /** Base radius of the slam circle. */
    public static final double SLAM_RADIUS = 6.0;
    public static final double SLAM_DAMAGE_MULTIPLE = 1.2;
    public static final double SLAM_KNOCKBACK_HORIZONTAL = 1.2;
    public static final double SLAM_KNOCKBACK_VERTICAL = 0.5;
    /** Ticks the boss stays in its hit pose and rooted to the spot after the last impact. */
    public static final int SLAM_RECOVERY_TICKS = 12;
    /** Blocks in front of the boss the slam lands when the model has no fist to read it from. */
    public static final double SLAM_FALLBACK_REACH = 4.0;

    public static int slamWarnTicks(Tier tier) {
        return byTier(tier, 30, 26, 22);
    }

    // Mythic follow-up ring

    /** Ticks from the first impact to the second ring landing; the ring is warned for all of them. */
    public static final int RING_WARN_TICKS = 20;
    /** How far past the first circle the ring reaches. */
    public static final double RING_WIDTH = 4.0;
    public static final double RING_DAMAGE_MULTIPLE = 0.8;

    // --- Thorn Sweep ---

    public static final double SWEEP_HALF_ANGLE_DEGREES = 70.0;
    /** Base radius of the sweep cone. */
    public static final double SWEEP_RADIUS = 6.5;
    public static final double SWEEP_DAMAGE_MULTIPLE = 1.0;
    public static final double SWEEP_KNOCKBACK_HORIZONTAL = 0.9;
    public static final double SWEEP_KNOCKBACK_VERTICAL = 0.3;
    public static final int SWEEP_RECOVERY_TICKS = 10;
    /** Heroic and Mythic poison whoever the claw catches. */
    public static final int SWEEP_POISON_TICKS = 40;

    public static int sweepWarnTicks(Tier tier) {
        return byTier(tier, 24, 20, 18);
    }

    // --- Root Eruption ---

    /** Radius of one eruption spot; not scaled with the body, so the dodge distance is the same at every tier. */
    public static final double ROOT_RADIUS = 1.6;
    public static final double ROOT_DAMAGE_MULTIPLE = 0.6;
    public static final int ROOT_SLOW_TICKS = 40;
    /** Slowness II. */
    public static final int ROOT_SLOW_AMPLIFIER = 1;
    public static final int ROOT_RECOVERY_TICKS = 10;
    /** Mythic marks every fighter and this many more spots near them. */
    public static final int ROOT_EXTRA_SPOTS_MYTHIC = 2;
    /** How far from a fighter an extra Mythic spot may fall. */
    public static final double ROOT_EXTRA_SPOT_SPREAD = 6.0;

    public static int rootWarnTicks(Tier tier) {
        return byTier(tier, 40, 36, 32);
    }

    /** How many fighters get a spot under them; {@link Integer#MAX_VALUE} is everyone. */
    public static int rootTargets(Tier tier) {
        return byTier(tier, 2, 3, Integer.MAX_VALUE);
    }

    // --- Slam into Sweep chain ---

    /** Chance that the combo's slam is followed straight away by a sweep. */
    public static final double COMBO_CHAIN_CHANCE = 0.5;

    // --- Mythic thorn patches ---

    public static final int PATCHES_PER_SLAM = 2;
    public static final double PATCH_RADIUS = 1.5;
    /** How far from the impact a patch may fall. */
    public static final double PATCH_SPREAD = 5.0;
    public static final int PATCH_LIFE_TICKS = 120;
    public static final int MAX_PATCHES = 6;
    public static final double PATCH_DAMAGE = 1.5;
    /** A player is pricked at most this often, however many patches they stand in. */
    public static final int PATCH_HIT_INTERVAL_TICKS = 20;
    public static final int PATCH_SLOW_TICKS = 30;

    // --- Mythic Shadow Bloom ---

    public static final double BLOOM_RADIUS = 10.0;
    public static final int BLOOM_WARN_TICKS = 30;
    public static final int BLOOM_COOLDOWN_TICKS = 500;
    public static final int BLOOM_DARKNESS_TICKS = 100;
    public static final int BLOOM_RECOVERY_TICKS = 10;

    // --- Phases ---

    /** Ticks between attacks in phase {@code phase} (0 awakened, 1 cracking, 2 breaking). */
    public static int gapTicks(Tier tier, int phase) {
        int[] gaps = byTier(tier, new int[] {50, 40, 30}, new int[] {40, 32, 24}, new int[] {32, 26, 20});
        return gaps[phase];
    }

    // --- Body ---

    /** Ticks the body glows red after a hit; hits during the glow do not restart it. */
    public static final int HIT_FLASH_TICKS = 3;
}
