package dev.sirbepy.mc.minibosses.engine;

import dev.sirbepy.mc.minibosses.arena.Arena;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.Random;

/**
 * The box a Guardian fight is allowed to touch: players are only fighters inside it, and no
 * telegraph draws or hits outside it. Built from the Guardian's {@link Arena}, or {@link #around}
 * a spawn point for a Guardian fought where no arena is registered yet.
 */
public record FightArea(World world, BoundingBox box) {

    /** Depth below the spawn point that {@link #around} still counts as the floor of the fight. */
    private static final int FLOOR_DEPTH = 2;

    /** Keeps a clamped point strictly inside the half-open box, whose max edge is outside. */
    private static final double EDGE_EPSILON = 1.0e-3;

    /** How far {@link #surfaceY} looks above and below the reference height for the ground. */
    private static final int SURFACE_UP = 2;
    private static final int SURFACE_DOWN = 4;

    public static FightArea of(Arena arena) {
        return new FightArea(arena.world(), arena.box().clone());
    }

    /**
     * A square {@code 2 * halfWidth} wide centred on {@code center}, running from {@link #FLOOR_DEPTH}
     * blocks below it to {@code height} blocks above it.
     */
    public static FightArea around(Location center, int halfWidth, int height) {
        BoundingBox box = new BoundingBox(center.getX() - halfWidth, center.getY() - FLOOR_DEPTH,
                center.getZ() - halfWidth, center.getX() + halfWidth, center.getY() + height,
                center.getZ() + halfWidth);
        return new FightArea(center.getWorld(), box);
    }

    public boolean contains(Location at) {
        return at != null && world.equals(at.getWorld()) && box.contains(at.getX(), at.getY(), at.getZ());
    }

    /** The nearest point inside the area; a point already inside comes back unchanged. */
    public Location clamp(Location at) {
        return new Location(world,
                clamp(at.getX(), box.getMinX(), box.getMaxX()),
                clamp(at.getY(), box.getMinY(), box.getMaxY()),
                clamp(at.getZ(), box.getMinZ(), box.getMaxZ()), at.getYaw(), at.getPitch());
    }

    /**
     * A random spot within {@code radius} blocks (on the ground plane) of {@code near}, standing on the
     * ground there, and never outside the area: a spot that falls outside is pulled to the nearest
     * edge instead of re-rolled, so the result never depends on luck.
     */
    public Location randomGroundPoint(Random rng, Location near, double radius) {
        double angle = rng.nextDouble() * Math.PI * 2;
        double distance = Math.sqrt(rng.nextDouble()) * radius; // sqrt: uniform over the disc, not bunched at the centre
        double x = clamp(near.getX() + Math.cos(angle) * distance, box.getMinX(), box.getMaxX());
        double z = clamp(near.getZ() + Math.sin(angle) * distance, box.getMinZ(), box.getMaxZ());
        double y = clamp(surfaceY(x, z, near.getY()), box.getMinY(), box.getMaxY());
        return new Location(world, x, y, z);
    }

    /**
     * Height of the ground at {@code x, z}: the top of the highest solid block within a few blocks of
     * {@code refY}, or {@code refY} itself over a void or an unloaded chunk (asking an unloaded chunk
     * for a block would load it on the main thread).
     */
    public double surfaceY(double x, double z, double refY) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return refY;
        }
        int top = (int) Math.floor(refY) + SURFACE_UP;
        for (int y = top; y >= top - SURFACE_UP - SURFACE_DOWN; y--) {
            Block block = world.getBlockAt(bx, y, bz);
            if (block.getType().isSolid()) {
                return y + 1;
            }
        }
        return refY;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max - EDGE_EPSILON, value));
    }
}
