package dev.sirbepy.mc.haru.rep;

/**
 * Haru's trust in his owner. Each tier unlocks dialogue, wares and quests, and takes a further cut off
 * emerald prices that already sit 15% under vanilla villagers (Joe's balance spec, 2026-10-06: 15-25%
 * better than vanilla, max trust after weeks of play, about four weeks of every daily request);
 * {@link #SAKURA_BOUND} opens the Secret Stock.
 */
public enum Tier {
    STRANGER(0, "Stranger", 0),
    ACQUAINTANCE(100, "Acquaintance", 2),
    FRIEND(350, "Friend", 4),
    TRUSTED(800, "Trusted", 6),
    KINDRED(1500, "Kindred Spirit", 8),
    SAKURA_BOUND(2500, "Sakura Bound", 10);

    private final int threshold;
    private final String title;
    private final int discountPercent;

    Tier(int threshold, String title, int discountPercent) {
        this.threshold = threshold;
        this.title = title;
        this.discountPercent = discountPercent;
    }

    public int threshold() {
        return threshold;
    }

    public String title() {
        return title;
    }

    public int discountPercent() {
        return discountPercent;
    }

    public boolean atLeast(Tier other) {
        return ordinal() >= other.ordinal();
    }

    public boolean isMax() {
        return this == SAKURA_BOUND;
    }

    /** The next tier up, or this one at the top. */
    public Tier next() {
        Tier[] all = values();
        return isMax() ? this : all[ordinal() + 1];
    }

    public static Tier of(int reputation) {
        Tier result = STRANGER;
        for (Tier tier : values()) {
            if (reputation >= tier.threshold) {
                result = tier;
            }
        }
        return result;
    }

    /** Emerald price after this tier's discount, rounded up so no ware ends more than 25% under vanilla. */
    public int discounted(int emeralds) {
        if (emeralds <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(emeralds * (100 - discountPercent) / 100.0));
    }
}
