package dev.sirbepy.mc.endfight.perch;

import dev.sirbepy.mc.endfight.arena.Pillar;
import org.bukkit.entity.EnderDragon;

import java.util.List;

/**
 * Pure: should the dragon be stopped from perching on the exit portal right now? No world access,
 * no entity spawning - {@link PerchGateListener} reads the real crystals and hands their positions
 * in as plain points.
 */
public final class PerchGate {

    /** A dragon only ever starts perching by transitioning into one of these two phases (verified
     * against the real 26.2 paper-api jar's {@code EnderDragon.Phase} enum). */
    public static boolean isLandingPhase(EnderDragon.Phase phase) {
        return phase == EnderDragon.Phase.FLY_TO_PORTAL || phase == EnderDragon.Phase.LAND_ON_PORTAL;
    }

    /** A plain (x, y, z) - crystals are checked by position only, no {@code World} reference
     * needed, so this stays testable with no Bukkit mocks at all. */
    public record Point(double x, double y, double z) {
    }

    private PerchGate() {
    }

    /**
     * {@code pillars} empty (the registry has no pillars yet) falls back to "any crystal within
     * {@code fallbackRadius} blocks horizontally of 0,0 and above {@code fallbackMinY}" - which the
     * 4 crystals a player places on the exit portal during a respawn never satisfy, since the
     * portal sits far below that floor.
     */
    public static boolean anyPillarCrystalAlive(List<Pillar> pillars, List<Point> aliveCrystals,
                                                 double pillarTopRadius, double fallbackRadius, double fallbackMinY) {
        if (pillars.isEmpty()) {
            return aliveCrystals.stream().anyMatch(c -> c.y() > fallbackMinY
                    && Math.hypot(c.x(), c.z()) <= fallbackRadius);
        }
        double radiusSq = pillarTopRadius * pillarTopRadius;
        for (Pillar pillar : pillars) {
            double crystalX = pillar.cx();
            double crystalY = pillar.topY() + 1;
            double crystalZ = pillar.cz();
            for (Point point : aliveCrystals) {
                double dx = point.x() - crystalX;
                double dy = point.y() - crystalY;
                double dz = point.z() - crystalZ;
                if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                    return true;
                }
            }
        }
        return false;
    }
}
