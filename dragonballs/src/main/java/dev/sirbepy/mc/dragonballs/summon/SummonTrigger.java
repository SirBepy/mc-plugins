package dev.sirbepy.mc.dragonballs.summon;

import dev.sirbepy.mc.dragonballs.registry.BallState;
import dev.sirbepy.mc.dragonballs.registry.BallView;
import org.bukkit.Location;

import java.util.List;

/**
 * Pure readiness checks for "can a summon start here": no Bukkit server access beyond what the
 * caller already hands in, so every branch is unit-testable with plain {@link BallView} records,
 * no MockBukkit required. {@link #allWithinRadius(List, String, int, int, int, int)} is the one
 * the trigger listener (and its tests) actually cares about; the {@link Location} overload is just
 * a convenience for call sites that already have one.
 */
public final class SummonTrigger {

    private SummonTrigger() {
    }

    public enum Readiness {
        /** All 7 conditions hold; the choice-of-dragon UI can be offered. */
        READY,
        /** A session is already running (anywhere); clicking another ball head does nothing new. */
        SESSION_ACTIVE,
        /** The balls are stone; dormant until the cooldown ends. */
        DORMANT,
        /** This player is permanently banned from wishing ({@code /shenron curse nowish}). */
        BANNED,
        /** Not all 7 are placed together, non-stone, same world, within radius of the clicked one. */
        INCOMPLETE
    }

    public static Readiness evaluate(List<BallView> balls, boolean dormant, boolean sessionActive,
                                      boolean wishBanned, Location clicked, int radius) {
        if (sessionActive) {
            return Readiness.SESSION_ACTIVE;
        }
        if (dormant) {
            return Readiness.DORMANT;
        }
        if (wishBanned) {
            return Readiness.BANNED;
        }
        return allWithinRadius(balls, clicked, radius) ? Readiness.READY : Readiness.INCOMPLETE;
    }

    /** All 7 balls PLACED, non-stone, in {@code world}, within {@code radius} blocks (Chebyshev
     * distance: {@code max(|dx|,|dy|,|dz|)}) of {@code (cx,cy,cz)}. */
    public static boolean allWithinRadius(List<BallView> balls, String world, int cx, int cy, int cz, int radius) {
        if (balls.size() != 7) {
            return false;
        }
        for (BallView ball : balls) {
            if (ball.state() != BallState.PLACED || ball.stone()) {
                return false;
            }
            if (!world.equals(ball.worldName())) {
                return false;
            }
            int dx = Math.abs((int) Math.floor(ball.x()) - cx);
            int dy = Math.abs((int) Math.floor(ball.y()) - cy);
            int dz = Math.abs((int) Math.floor(ball.z()) - cz);
            if (Math.max(dx, Math.max(dy, dz)) > radius) {
                return false;
            }
        }
        return true;
    }

    public static boolean allWithinRadius(List<BallView> balls, Location clicked, int radius) {
        return clicked.getWorld() != null && allWithinRadius(balls, clicked.getWorld().getName(),
                clicked.getBlockX(), clicked.getBlockY(), clicked.getBlockZ(), radius);
    }

    /** Plain average of the 7 balls' stored coordinates; the caller attaches a {@code World}
     * (already known to be the same for all 7, once {@link #allWithinRadius} has passed). */
    public static double[] averagePosition(List<BallView> balls) {
        double sx = 0;
        double sy = 0;
        double sz = 0;
        for (BallView ball : balls) {
            sx += ball.x();
            sy += ball.y();
            sz += ball.z();
        }
        int n = balls.size();
        return new double[]{sx / n, sy / n, sz / n};
    }
}
