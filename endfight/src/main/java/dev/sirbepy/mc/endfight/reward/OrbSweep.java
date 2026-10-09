package dev.sirbepy.mc.endfight.reward;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure radius/window logic for sweeping the dragon's death-animation XP orbs out of the world
 * (see {@link RewardService#onDragonDeath}).
 *
 * <p>Cancelling the orbs' {@code EntitySpawnEvent} does not actually work: a live kill on the e2e
 * server (Paper 26.2) showed orbs landing on the ground 7-10s after death and the fighter's XP bar
 * never moving, with {@link RewardService}'s old summed total reading 0 - either the spawn event
 * never fires for them, or Paper/vanilla folds the dragon's XP into orbs already lying on the
 * ground instead of creating new ones. A sweep removes any live orb in range every tick for the
 * window, plus once immediately at death to catch orbs already lying there, which works
 * regardless of which path vanilla actually takes - and does not need to know, since the per-tier
 * reward table decides the XP amount now, not the orbs.
 */
public final class OrbSweep {

    private OrbSweep() {
    }

    /** True while the sweep should still run: from the moment of death (tick 0) through
     * {@code windowTicks} inclusive. */
    public static boolean activeAt(long ticksSinceDeath, long windowTicks) {
        return ticksSinceDeath >= 0 && ticksSinceDeath <= windowTicks;
    }

    /** True when {@code orbLocation} is close enough to the dying dragon's own location to be
     * swept: the same world, within {@code radius} blocks. */
    public static boolean inRange(Location orbLocation, Location deathLocation, double radius) {
        return orbLocation.getWorld().equals(deathLocation.getWorld())
                && orbLocation.distanceSquared(deathLocation) <= radius * radius;
    }

    /** Every location in {@code orbLocations} that a sweep at {@code ticksSinceDeath} would
     * remove - a pure function over plain coordinates, for a unit test that never touches an
     * entity or a scheduler. */
    public static List<Location> toRemove(List<Location> orbLocations, Location deathLocation, double radius,
                                           long ticksSinceDeath, long windowTicks) {
        List<Location> hits = new ArrayList<>();
        if (!activeAt(ticksSinceDeath, windowTicks)) {
            return hits;
        }
        for (Location orbLocation : orbLocations) {
            if (inRange(orbLocation, deathLocation, radius)) {
                hits.add(orbLocation);
            }
        }
        return hits;
    }
}
