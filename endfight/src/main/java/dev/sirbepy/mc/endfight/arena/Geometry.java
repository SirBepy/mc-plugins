package dev.sirbepy.mc.endfight.arena;

import java.util.List;

/**
 * Shared math for {@link ArenaLayout}'s builders: vanilla's own exclusion zones (so nothing we
 * place gets eaten on the next dragon respawn) and the facing/placement helpers ported from
 * {@code .for_bepy/end-build/gen_island.py}.
 */
final class Geometry {

    private Geometry() {
    }

    static final double SQRT2 = Math.sqrt(2);

    /** Vanilla's own respawn-clear threshold ({@code EndSpikeFeature.placeSpike}: only footprint
     * blocks above y=65 get cleared when a spike regenerates). */
    static final int RESPAWN_CLEAR_Y = 65;

    /** Vanilla's circle test for the obsidian cylinder (dx*dx+dz*dz &lt;= radius*radius+1), and
     * only below topY (the cylinder never reaches the bedrock cap). */
    static boolean insideCylinder(Pillar p, int x, int y, int z) {
        if (y >= p.topY()) {
            return false;
        }
        int dx = x - p.cx();
        int dz = z - p.cz();
        return dx * dx + dz * dz <= p.radius() * p.radius() + 1;
    }

    /** The square footprint vanilla clears to air above y=65 when a spike regenerates. */
    static boolean insideRespawnBox(Pillar p, int x, int y, int z) {
        if (y <= RESPAWN_CLEAR_Y) {
            return false;
        }
        return Math.abs(x - p.cx()) <= p.radius() && Math.abs(z - p.cz()) <= p.radius();
    }

    /** True if (x,y,z) must never be a non-cage arena placement: inside any pillar's own cylinder
     * or its respawn-clear box. The cage is the one sanctioned exception (built by the caller
     * without going through this check). */
    static boolean violatesAnyPillar(List<Pillar> pillars, int x, int y, int z) {
        for (Pillar p : pillars) {
            if (insideCylinder(p, x, y, z) || insideRespawnBox(p, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /** Nearest cardinal facing for a tangential travel direction (dx, dz); Minecraft's facing
     * axes: +x=east, -x=west, +z=south, -z=north. Mirrors gen_island.py's cardinal_facing. */
    static String cardinalFacing(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? "east" : "west";
        }
        return dz > 0 ? "south" : "north";
    }

    /**
     * Scales a unit direction (cos theta, sin theta) out to the point where the SQUARE (Chebyshev)
     * distance from the origin equals {@code nominalRadius}, instead of the Euclidean distance a
     * plain polar point would give.
     *
     * <p>gen_island.py places stair treads at a constant Euclidean radius, which (unlike this
     * plugin) never has to stay outside vanilla's respawn-clear box: near a 45-degree angle, a
     * circular lane at radius r+1 dips to Euclidean-distance/sqrt(2) in Chebyshev terms, which is
     * inside a box of half-width r once r is 3 or more (verified: at r=5, the mockup's own inner
     * lane radius 6 gives a Chebyshev distance of ~4.24 at the diagonal, under the box's half-width
     * of 5). Scaling to a constant Chebyshev radius instead keeps every tread at least
     * {@code nominalRadius - pillar.radius()} blocks outside the box at every angle, so the spiral
     * reads as a slightly rounded square rather than a perfect circle - the one deliberate shape
     * deviation from the mockup, needed so dragon-respawn regen can never eat a tread.
     */
    static double[] squarePoint(double cx, double cz, double nominalRadius, double theta) {
        double ux = Math.cos(theta);
        double uz = Math.sin(theta);
        double maxc = Math.max(Math.abs(ux), Math.abs(uz));
        double scale = nominalRadius / maxc;
        return new double[] {cx + scale * ux, cz + scale * uz};
    }

    static int round(double v) {
        return (int) Math.round(v);
    }
}
