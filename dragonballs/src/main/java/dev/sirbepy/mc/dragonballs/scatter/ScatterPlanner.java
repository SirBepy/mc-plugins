package dev.sirbepy.mc.dragonballs.scatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure orchestration on top of {@link ScatterMath}: finding a geographically valid candidate point
 * (in bounds, far enough from the others) needs no world access, so it's fully unit-testable. The
 * actual liquid/ice resampling needs live chunk data (see {@code ScatterService}), which drives this
 * class one candidate at a time instead of looping inside it.
 */
public final class ScatterPlanner {

    private ScatterPlanner() {
    }

    /**
     * A random point in the annulus around {@code (centerX, centerZ)} that lands inside the pregen
     * square (an absolute bound around the world origin, not around the center) and at least
     * {@code minSpacing} from every point in {@code avoid}, or {@code null} if none turned up within
     * {@code maxAttempts} tries (a defensive bound; with 7 balls and a multi-thousand-block annulus
     * this should never actually trigger). Returns an ABSOLUTE (x, z), not an offset.
     */
    public static double[] findCandidate(Random random, double centerX, double centerZ, double minDistance,
                                          double maxDistance, int pregenLimit, List<double[]> avoid,
                                          double minSpacing, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double[] offset = ScatterMath.annulusOffset(random, minDistance, maxDistance);
            double x = centerX + offset[0];
            double z = centerZ + offset[1];
            if (ScatterMath.withinPregenBound(x, z, pregenLimit) && ScatterMath.farEnoughFromAll(x, z, avoid, minSpacing)) {
                return new double[]{x, z};
            }
        }
        return null;
    }

    /**
     * Offsets around (0, 0) in expanding square rings, for the shore search once liquid/ice
     * resampling has run out of tries. A square ring (not a true circle) is good enough: it only
     * needs to find SOME nearby solid ground, not the closest possible one.
     */
    public static List<int[]> spiralOffsets(int maxRadius) {
        List<int[]> offsets = new ArrayList<>();
        offsets.add(new int[]{0, 0});
        for (int r = 1; r <= maxRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                offsets.add(new int[]{dx, -r});
                offsets.add(new int[]{dx, r});
            }
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                offsets.add(new int[]{-r, dz});
                offsets.add(new int[]{r, dz});
            }
        }
        return offsets;
    }
}
