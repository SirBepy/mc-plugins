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
}
