package dev.sirbepy.mc.dragonballs.scatter;

import java.util.List;

/**
 * Whether a structure {@code World#locateNearestStructure} found is one a point-of-interest star
 * may actually use: still inside the set's own scatter annulus (a search radius of ~32 chunks can
 * easily return a structure outside it), inside the pregen bound, and far enough from the other
 * balls. Pure (reuses {@link ScatterMath}) so it's testable without a server; the Towny wilderness
 * check is a separate call in {@code ScatterService} since it needs the live plugin.
 */
public final class PoiAcceptance {

    private PoiAcceptance() {
    }

    public static boolean accepts(double x, double z, double centerX, double centerZ, double minRadius,
                                   double maxRadius, int pregenLimit, List<double[]> avoid, double minSpacing) {
        double distance = ScatterMath.distance(x, z, centerX, centerZ);
        return distance >= minRadius && distance <= maxRadius
                && ScatterMath.withinPregenBound(x, z, pregenLimit)
                && ScatterMath.farEnoughFromAll(x, z, avoid, minSpacing);
    }
}
