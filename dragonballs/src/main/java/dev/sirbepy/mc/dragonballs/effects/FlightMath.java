package dev.sirbepy.mc.dragonballs.effects;

/**
 * Pure position math for a flying dragon ball after a wish: rise, then either fly horizontally
 * toward the destination (same world) or keep climbing straight up (other world / no
 * destination). No Bukkit types, so this is testable without a server.
 */
final class FlightMath {

    static final double RISE_HEIGHT = 25.0;
    static final long RISE_TICKS = 30; // 1.5s at 20 tps
    static final long FLY_TICKS = 80; // 4s at 20 tps
    static final double FLY_SPEED_PER_TICK = 1.75; // within the requested 1.5-2 blocks/tick
    static final double FLY_CLIMB_PER_TICK = 0.05;
    static final long TOTAL_TICKS_HORIZONTAL = RISE_TICKS + FLY_TICKS;

    static final double STRAIGHT_UP_HEIGHT = 60.0;
    static final long TOTAL_TICKS_STRAIGHT = RISE_TICKS + FLY_TICKS; // same overall lifetime as the horizontal path

    private FlightMath() {
    }

    /**
     * Horizontal unit direction from origin toward destination, Y ignored. Returns {@code (0, 0)}
     * rather than NaN when the two points coincide horizontally (e.g. destination == origin),
     * so a degenerate same-spot "destination" never breaks the animation.
     */
    static double[] horizontalDirection(double originX, double originZ, double destX, double destZ) {
        double dx = destX - originX;
        double dz = destZ - originZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) {
            return new double[] {0.0, 0.0};
        }
        return new double[] {dx / len, dz / len};
    }

    /** Offset from origin at {@code elapsedTicks}, same-world case: rise, then fly toward (dirX, dirZ). */
    static double[] offsetTowardDestination(long elapsedTicks, double dirX, double dirZ) {
        if (elapsedTicks <= RISE_TICKS) {
            double y = RISE_HEIGHT * ((double) elapsedTicks / RISE_TICKS);
            return new double[] {0.0, y, 0.0};
        }
        long flyElapsed = Math.min(elapsedTicks - RISE_TICKS, FLY_TICKS);
        double x = dirX * FLY_SPEED_PER_TICK * flyElapsed;
        double z = dirZ * FLY_SPEED_PER_TICK * flyElapsed;
        double y = RISE_HEIGHT + FLY_CLIMB_PER_TICK * flyElapsed;
        return new double[] {x, y, z};
    }

    /** Offset from origin at {@code elapsedTicks}, other-world/no-destination case: straight up only. */
    static double[] offsetStraightUp(long elapsedTicks) {
        long clamped = Math.min(elapsedTicks, TOTAL_TICKS_STRAIGHT);
        double y = STRAIGHT_UP_HEIGHT * ((double) clamped / TOTAL_TICKS_STRAIGHT);
        return new double[] {0.0, y, 0.0};
    }

    static boolean isHorizontalFlightDone(long elapsedTicks) {
        return elapsedTicks >= TOTAL_TICKS_HORIZONTAL;
    }

    static boolean isStraightUpDone(long elapsedTicks) {
        return elapsedTicks >= TOTAL_TICKS_STRAIGHT;
    }
}
