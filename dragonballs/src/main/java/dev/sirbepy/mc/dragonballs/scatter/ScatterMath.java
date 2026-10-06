package dev.sirbepy.mc.dragonballs.scatter;

import java.util.List;
import java.util.Random;

/**
 * Pure scatter math: no Bukkit, no I/O, so it can be unit-tested without a server. Angles and
 * distances only; surface/liquid checks live in {@link ScatterPlanner} behind {@link SurfaceProbe}.
 */
public final class ScatterMath {

    private ScatterMath() {
    }

    /**
     * A uniformly random point in the annulus {@code minDistance..maxDistance} from the origin.
     * Sampling {@code r} uniformly (instead of the angle-radius pair naively) would bunch points
     * near the inner edge; {@code r = sqrt(u * (max^2 - min^2) + min^2)} makes the AREA uniform.
     */
    public static double[] annulusOffset(Random random, double minDistance, double maxDistance) {
        double min2 = minDistance * minDistance;
        double max2 = maxDistance * maxDistance;
        double r = Math.sqrt(random.nextDouble() * (max2 - min2) + min2);
        double theta = random.nextDouble() * Math.PI * 2;
        return new double[]{r * Math.cos(theta), r * Math.sin(theta)};
    }

    /** Pregenerated terrain is a square of this half-size on each axis, centered on the world origin. */
    public static boolean withinPregenBound(double x, double z, int limit) {
        return Math.abs(x) <= limit && Math.abs(z) <= limit;
    }

    public static double distance(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** True if {@code (x, z)} is at least {@code minSpacing} from every point already placed. */
    public static boolean farEnoughFromAll(double x, double z, List<double[]> placed, double minSpacing) {
        for (double[] other : placed) {
            if (distance(x, z, other[0], other[1]) < minSpacing) {
                return false;
            }
        }
        return true;
    }
}
