package dev.sirbepy.mc.minibosses.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

/**
 * One Guardian's arena: a block-aligned box in one world. Two opposite corner blocks are both
 * inside it, so a 30x30 build given as its corner blocks is a 30x30 box.
 */
public record Arena(String guardianId, World world, BoundingBox box) {

    /** The box spanning two corner blocks, both included. */
    public static Arena ofCorners(String guardianId, World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Arena(guardianId, world, new BoundingBox(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, Math.max(z1, z2) + 1));
    }

    public boolean contains(Location at) {
        return at != null && world.equals(at.getWorld()) && box.contains(at.toVector());
    }

    /**
     * Where the Guardian rises: the middle of the box, on the first spot from the bottom with two
     * free blocks to stand in, so a floor given as the lowest corner is stood on rather than in.
     */
    public Location center() {
        int x = (int) Math.floor(box.getCenterX());
        int z = (int) Math.floor(box.getCenterZ());
        for (int y = (int) box.getMinY(); y < box.getMaxY() - 1; y++) {
            Block feet = world.getBlockAt(x, y, z);
            if (!feet.getType().isSolid() && !feet.getRelative(0, 1, 0).getType().isSolid()
                    && feet.getRelative(0, -1, 0).getType().isSolid()) {
                return feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        return new Location(world, box.getCenterX(), box.getMinY() + 1, box.getCenterZ());
    }

    public String describe() {
        return guardianId + " in " + world.getName() + ": " + (int) box.getMinX() + " " + (int) box.getMinY() + " "
                + (int) box.getMinZ() + " to " + ((int) box.getMaxX() - 1) + " " + ((int) box.getMaxY() - 1) + " "
                + ((int) box.getMaxZ() - 1) + " (" + (int) box.getWidthX() + "x" + (int) box.getWidthZ() + ", "
                + (int) box.getHeight() + " high)";
    }
}
