package dev.sirbepy.mc.dragonballs;

/** Shared player-facing coin formatting: a whole number prints with no decimals. */
public final class CoinFormat {

    private CoinFormat() {
    }

    public static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
