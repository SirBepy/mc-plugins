package dev.sirbepy.mc.haru.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Where Haru stands, set by {@code /haru place}. Stored by world name so it survives restarts. */
public record NpcAnchor(String world, double x, double y, double z, float yaw) {

    public static NpcAnchor of(Location location) {
        return new NpcAnchor(location.getWorld().getName(),
                location.getBlockX() + 0.5, location.getY(), location.getBlockZ() + 0.5, location.getYaw());
    }

    /** Null while the world isn't loaded. */
    public Location toLocation() {
        World loaded = Bukkit.getWorld(world);
        return loaded == null ? null : new Location(loaded, x, y, z, yaw, 0);
    }

    public int chunkX() {
        return (int) Math.floor(x) >> 4;
    }

    public int chunkZ() {
        return (int) Math.floor(z) >> 4;
    }
}
