package dev.sirbepy.mc.tag.logic;

/**
 * Pure points-per-tick math (lowest total wins). Kept free of Bukkit types so it can be unit
 * tested without a server.
 */
public final class PointsMath {

    private PointsMath() {
    }

    /**
     * {@code n} = online players other than IT who have not opted out. {@code n == 0} is a safe
     * zero (nobody to chase, no risk), everything else scales up with more players in play.
     */
    public static double multiplier(int n, PointsCurve curve, double step) {
        if (n <= 0) {
            return 0.0;
        }
        return switch (curve) {
            case LOG -> 1.0 + step * (Math.log(n) / Math.log(2));
            case LINEAR -> 1.0 + step * (n - 1);
        };
    }

    /** Points IT gains for one tick, online or offline (offline is scaled by {@code offlineFactor}). */
    public static double tickGain(boolean itOnline, double base, double offlineFactor, double multiplier) {
        double gain = base * multiplier;
        return itOnline ? gain : gain * offlineFactor;
    }
}
