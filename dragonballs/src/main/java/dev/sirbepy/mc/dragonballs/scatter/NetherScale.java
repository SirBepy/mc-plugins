package dev.sirbepy.mc.dragonballs.scatter;

/** The Nether is 1/8 scale of the overworld (vanilla's own ratio); pure so the math is testable. */
public final class NetherScale {

    private static final double FACTOR = 8.0;

    private NetherScale() {
    }

    public static double toNether(double overworldValue) {
        return overworldValue / FACTOR;
    }

    public static int toNetherRadius(int overworldRadius) {
        return (int) (overworldRadius / FACTOR);
    }

    /** A Nether scatter radius: the overworld one at 1/8 scale, stretched by {@code multiplier}
     * (config's nether-radius-multiplier) so the Nether hunt isn't a short walk. */
    public static int toNetherRadius(int overworldRadius, double multiplier) {
        return (int) (overworldRadius / FACTOR * multiplier);
    }

    /**
     * Pulls a scatter centre inwards so the whole annulus stays inside the pregenerated square: the
     * Nether's square (+/-1200) is small next to its radii (up to 1125), and a centre near its edge
     * left too few valid spots for 7 spaced-out balls (live, 2026-10-07: 2 of 7 never placed).
     */
    public static double clampCenter(double value, int pregenLimit, int maxRadius) {
        double bound = Math.max(0, pregenLimit - maxRadius);
        return Math.max(-bound, Math.min(bound, value));
    }
}
