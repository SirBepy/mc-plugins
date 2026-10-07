package dev.sirbepy.mc.guild.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** A spot in a world: where a staff member stands, or a corner of the guarded hall. */
public record Place(String world, double x, double y, double z, float yaw) {

    public static Place of(Location at) {
        return new Place(at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), at.getYaw());
    }

    /** Null while the world isn't loaded. */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, 0);
    }

    public String describe() {
        return world + " " + x + " " + y + " " + z + " yaw " + yaw;
    }

    /** The box the hall guard protects, inclusive, in one world. */
    public record Box(String world, int x0, int y0, int z0, int x1, int y1, int z1) {

        public static Box of(String world, int ax, int ay, int az, int bx, int by, int bz) {
            return new Box(world, Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz),
                    Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz));
        }

        public boolean contains(Location at) {
            return at.getWorld() != null && at.getWorld().getName().equals(world)
                    && at.getBlockX() >= x0 && at.getBlockX() <= x1 && at.getBlockY() >= y0 && at.getBlockY() <= y1
                    && at.getBlockZ() >= z0 && at.getBlockZ() <= z1;
        }
    }
}
