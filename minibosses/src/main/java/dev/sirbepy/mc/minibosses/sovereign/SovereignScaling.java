package dev.sirbepy.mc.minibosses.sovereign;

/**
 * Pure solo/group scaling math for the Void Sovereign, applied by {@code SovereignFight.spawn}
 * against however many players are near the summon at that moment. Same formula and same values
 * as {@code sentinel.SentinelScaling}/{@code tyrant.TyrantScaling} - no End-specific reason to
 * diverge (an exposed-sky arena doesn't change how many fighters show up, only what the fight's own
 * abilities do with the edges) - but kept as its own class rather than imported across packages,
 * the same "each Guardian gets its own copy, neither needs to know the other exists" reasoning
 * {@code tyrant.SummonCooldown}/{@code tyrant.TyrantScaling} already document for their own
 * deliberate duplication of the Sentinel's.
 */
public final class SovereignScaling {

    /** +50% max health per fighter beyond the first. */
    static final double HEALTH_PER_EXTRA_FIGHTER = 0.5;
    /** +20% damage per fighter beyond the first - softer than health so solo stays fair. */
    static final double DAMAGE_PER_EXTRA_FIGHTER = 0.2;
    /** Caps scaling at 5 fighters' worth so a flash mob doesn't make the fight unkillable. */
    static final int MAX_SCALING_FIGHTERS = 5;

    private SovereignScaling() {
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
