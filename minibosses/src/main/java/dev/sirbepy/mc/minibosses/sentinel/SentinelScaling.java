package dev.sirbepy.mc.minibosses.sentinel;

/**
 * Pure solo/group scaling math for the Overgrown Sentinel, applied by {@code SentinelFight.spawn}
 * against however many players are near the summon at that moment. No precedent for scaling a
 * single boss's own stats exists elsewhere in this repo: {@code endfight}'s
 * {@code cult.WaveMath}/{@code ArenaRegistry} scale how many extra mobs a wave spawns per fighter,
 * not a single boss's health/damage, so this formula is this dispatch's own call rather than a
 * reused one. Health scales harder than damage on purpose: a group fight should feel like it's
 * fighting a tankier Sentinel, not one that one-shots whoever is unlucky enough to be targeted,
 * which would punish a lone fighter standing in for an absent friend more than it punishes the group.
 */
public final class SentinelScaling {

    /** +50% max health per fighter beyond the first. */
    static final double HEALTH_PER_EXTRA_FIGHTER = 0.5;
    /** +20% damage per fighter beyond the first - softer than health so solo stays fair. */
    static final double DAMAGE_PER_EXTRA_FIGHTER = 0.2;
    /** Caps scaling at 5 fighters' worth so a flash mob doesn't make the fight unkillable. */
    static final int MAX_SCALING_FIGHTERS = 5;

    private SentinelScaling() {
    }

    public static double scaledHealth(double baseHealth, int fighterCount) {
        return baseHealth * multiplier(fighterCount, HEALTH_PER_EXTRA_FIGHTER);
    }

    public static double scaledDamage(double baseDamage, int fighterCount) {
        return baseDamage * multiplier(fighterCount, DAMAGE_PER_EXTRA_FIGHTER);
    }

    private static double multiplier(int fighterCount, double perExtraFighter) {
        int extra = Math.min(Math.max(fighterCount, 1) - 1, MAX_SCALING_FIGHTERS - 1);
        return 1.0 + perExtraFighter * extra;
    }
}
