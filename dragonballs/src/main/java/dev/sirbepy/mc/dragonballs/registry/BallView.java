package dev.sirbepy.mc.dragonballs.registry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * Immutable snapshot of one ball's state, safe to hand out to phase 2 code: mutating it back
 * requires going through {@link BallRegistry}'s own mark* methods instead.
 */
public record BallView(
        int star,
        BallState state,
        boolean stone,
        String worldName,
        double x,
        double y,
        double z,
        UUID holderId,
        String holderName,
        long heldSinceMillis,
        long lastSeenMillis,
        UUID entityId
) {

    /** Last known (or current, for PLACED) location, or null if its world isn't loaded. */
    public Location lastKnownLocation() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z);
    }
}
