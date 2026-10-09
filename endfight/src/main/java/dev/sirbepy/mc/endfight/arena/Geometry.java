package dev.sirbepy.mc.endfight.arena;

import java.util.List;

/**
 * Shared math for {@link ArenaLayout}'s builders: the pillars' own obsidian (which nothing of ours
 * may replace) and the facing/placement helpers ported from {@code .for_bepy/end-build/gen_island.py}.
 *
 * <p>Vanilla also clears the square cx-r..cx+r / cz-r..cz+r above y 65 to air when a spike
 * regenerates during a dragon respawn ({@code EndSpikeFeature.placeSpike}), which takes out the
 * inner stair lane and the rings closest to the pillar. That is fine: the respawn ends with a new
 * dragon, and {@link RespawnRebuildListener} rebuilds the whole arena right after.
 */
final class Geometry {

    private Geometry() {
    }

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

    /** True if (x,y,z) is part of any pillar's obsidian, which no arena placement may replace. */
    static boolean insideAnyPillar(List<Pillar> pillars, int x, int y, int z) {
        for (Pillar p : pillars) {
            if (insideCylinder(p, x, y, z)) {
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

    static int round(double v) {
        return (int) Math.round(v);
    }
}
