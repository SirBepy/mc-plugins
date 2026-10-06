package dev.sirbepy.mc.dragonballs.summon.curse;

/** Pure expiry/remaining-duration math for effect curses; no Bukkit types, so it's testable with
 * plain longs. */
public final class CurseMath {

    private CurseMath() {
    }

    public static boolean isExpired(long expiryMillis, long nowMillis) {
        return expiryMillis <= nowMillis;
    }

    public static long remainingMillis(long expiryMillis, long nowMillis) {
        return Math.max(0, expiryMillis - nowMillis);
    }

    public static long expiryFromHours(long nowMillis, double hours) {
        return nowMillis + Math.round(hours * 3_600_000.0);
    }

    /** Remaining duration in ticks (20/s), for {@code PotionEffect}'s constructor; 0 once expired. */
    public static int remainingTicks(long expiryMillis, long nowMillis) {
        long ticks = remainingMillis(expiryMillis, nowMillis) / 50L;
        return (int) Math.min(Integer.MAX_VALUE, ticks);
    }
}
