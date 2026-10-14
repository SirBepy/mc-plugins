package dev.sirbepy.mc.cartel.call;

import java.util.function.IntSupplier;

/**
 * One cousin's crossbow drill, in server ticks: load for {@link #CHARGE_TICKS}, aim for a pillager's
 * 1-2 s, fire when the shot is clear and in {@link #RANGE}, reload; every {@link #ROCKET_EVERY}th shot
 * is a firework rocket instead of a bolt (Joe, 2026-10-08). Also decides whether he closes in,
 * holds or backs off. Pure state, driven by {@link RangedGoal}; keyed on the server's tick count
 * rather than on calls, since a goal isn't necessarily ticked every tick.
 */
public final class Marksman {

    public enum Move { CLOSE_IN, HOLD, BACK_OFF, STRAFE }

    public enum Shot { NONE, BOLT, ROCKET }

    /** Quick Charge III: a crossbow loads in 25 ticks, 5 fewer per level. */
    static final int CHARGE_TICKS = 25 - 5 * 3;
    /** A pillager waits 20 + random(20) ticks between loading and firing. */
    static final int AIM_TICKS = 20;
    static final int AIM_SPREAD = 20;
    /** A pillager's own crossbow range. */
    static final double RANGE = 16;
    /** A pillager stops walking in at 8 blocks. */
    static final double HOLD = 8;
    /** Closer than this (horizontally) he can't see over a pillar's top, and a monster is on him. */
    static final double TOO_CLOSE = 4;
    /** Two bolts, then a rocket: the rockets' splash is the heavy hit, the bolts the steady one. */
    static final int ROCKET_EVERY = 3;

    private final IntSupplier aimDelay;
    private long loadingSince = -1;
    private long fireAt = -1;
    private int shots;

    /** {@code aimDelay} answers 0 to {@link #AIM_SPREAD} - 1, like a pillager's random. */
    public Marksman(IntSupplier aimDelay) {
        this.aimDelay = aimDelay;
    }

    /**
     * A blocked shot (the owner or the other cousin standing in the line of fire) never resolves by
     * walking straight at the target - that line is collinear with the blockage by construction, so
     * {@link #STRAFE} sidesteps it instead of {@link #CLOSE_IN}, at any range.
     */
    public Move move(double horizontal, boolean clearShot) {
        if (horizontal < TOO_CLOSE) {
            return Move.BACK_OFF;
        }
        if (!clearShot) {
            return Move.STRAFE;
        }
        return horizontal > HOLD ? Move.CLOSE_IN : Move.HOLD;
    }

    /** What leaves the crossbow on server tick {@code tick}, if anything; firing starts the next reload. */
    public Shot tick(long tick, double distance, boolean clearShot) {
        if (loadingSince < 0) {
            loadingSince = tick;
        }
        long loadedAt = loadingSince + CHARGE_TICKS;
        if (tick < loadedAt) {
            return Shot.NONE;
        }
        if (fireAt < 0) {
            fireAt = loadedAt + AIM_TICKS + aimDelay.getAsInt();
        }
        if (tick < fireAt || !clearShot || distance > RANGE) {
            return Shot.NONE;
        }
        loadingSince = tick;
        fireAt = -1;
        return ++shots % ROCKET_EVERY == 0 ? Shot.ROCKET : Shot.BOLT;
    }
}
