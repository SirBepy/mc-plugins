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

    // --- Anchors (Heartguard and Wild Regrowth) ---

    /** Counted player hits that break one anchor. */
    public static int anchorHitsToBreak(Tier tier) {
        return byTier(tier, 3, 4, 5);
    }

    /** An anchor counts one hit per this many ticks, so a fast weapon cannot skip the mechanic inside a frame. */
    public static final int ANCHOR_HIT_INTERVAL_TICKS = 10;
    /** Anchors stand this far (horizontally) from the boss: at least, and at most. */
    public static final double ANCHOR_MIN_DISTANCE = 5.0;
    public static final double ANCHOR_MAX_DISTANCE = 8.0;
    /** Closest two anchors of one cast may stand. */
    public static final double ANCHOR_MIN_SPACING = 3.0;
    /** Blocks above the ground the anchor stand floats, so melee reaches it. */
    public static final double ANCHOR_HEIGHT = 1.2;
    /** Ticks between redraws of the green beams from each anchor to the heart. */
    public static final int BEAM_EVERY_TICKS = 4;
    public static final double BEAM_SPACING = 0.5;

    // --- Anchor break: the stagger and the window it opens ---

    /** Ticks the boss staggers (no attack, held still) after its last anchor is broken. */
    public static final int STAGGER_TICKS = 30;
    /** Ticks of extra damage after a stagger: 4 seconds. */
    public static final int VULNERABLE_TICKS = 80;
    public static final double VULNERABLE_DAMAGE_MULTIPLIER = 1.25;

    // --- Heartguard ---

    public static final int HEARTGUARD_COOLDOWN_TICKS = 500;
    /** The cast: the chest opens for this many ticks before the anchors appear. */
    public static final int HEARTGUARD_OPEN_TICKS = 20;
    /** The whole cast; the boss fights on after it while the shield lasts. */
    public static final int HEARTGUARD_CAST_TICKS = 30;
    /** What the boss takes while any Heartguard anchor stands. */
    public static final double HEARTGUARD_DAMAGE_MULTIPLIER = 0.25;
    /** Ticks between brighter pulses at the exposed heart while the shield holds. */
    public static final int HEARTGUARD_PULSE_TICKS = 10;
    public static final int HEARTGUARD_WEIGHT = 2;

    public static int heartguardAnchors(Tier tier) {
        return byTier(tier, 3, 4, 4);
    }

    /** Longest the shield holds: 20, 18 and 16 seconds. */
    public static int heartguardLifeTicks(Tier tier) {
        return byTier(tier, 400, 360, 320);
    }

    // --- Wild Regrowth ---

    public static final int REGROWTH_COOLDOWN_TICKS = 600;
    public static final int REGROWTH_WEIGHT = 2;
    /** Above this share of max health the boss has nothing to regrow and does not cast it. */
    public static final double REGROWTH_MAX_START_HEALTH = 0.9;
    /** Ticks between heals: once a second. */
    public static final int REGROWTH_HEAL_EVERY_TICKS = 20;
    /** Max health restored per live anchor per heal. */
    public static final double REGROWTH_HEAL_FRACTION = 0.01;
    /** Redraw rate and size of the green ring on the ground that shows the boss is healing. */
    public static final int REGROWTH_RING_EVERY_TICKS = 4;
    public static final double REGROWTH_RING_RADIUS = 3.0;

    public static int regrowthAnchors(Tier tier) {
        return byTier(tier, 2, 3, 4);
    }

    /** How long the boss stays planted: 8, 10 and 12 seconds. */
    public static int regrowthChannelTicks(Tier tier) {
        return byTier(tier, 160, 200, 240);
    }

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
