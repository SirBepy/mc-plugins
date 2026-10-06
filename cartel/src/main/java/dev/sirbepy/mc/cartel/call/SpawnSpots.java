package dev.sirbepy.mc.cartel.call;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Finds somewhere a cousin can stand at a set distance from someone. */
public final class SpawnSpots {

    private static final int DIRECTIONS = 16;
    private static final int VERTICAL_REACH = 8;

    private SpawnSpots() {
    }

    /**
     * Up to {@code count} standable spots about {@code distance} blocks (horizontally) from
     * {@code center}, trying the exact distance all the way round before stepping in or out by one
     * block at a time (never closer than half the distance). Empty if there's nowhere to stand.
     */
    public static List<Location> around(Location center, int distance, int count, Random random) {
        List<Location> found = new ArrayList<>();
        double start = random.nextDouble() * Math.PI * 2;
        for (int step = 0; step <= distance / 2 && found.size() < count; step++) {
            for (int radius : step == 0 ? new int[]{distance} : new int[]{distance - step, distance + step}) {
                for (int i = 0; i < DIRECTIONS && found.size() < count; i++) {
                    double angle = start + i * (Math.PI * 2 / DIRECTIONS);
                    int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
                    int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
                    standable(center.getWorld(), x, center.getBlockY(), z)
                            .filter(spot -> found.stream().noneMatch(f -> f.distanceSquared(spot) < 1))
                            .ifPresent(found::add);
                }
            }
        }
        return found;
    }

    /** The standable block nearest to {@code y} in the column, within {@link #VERTICAL_REACH}. */
    static Optional<Location> standable(World world, int x, int y, int z) {
        for (int dy = 0; dy <= VERTICAL_REACH; dy++) {
            for (int candidate : dy == 0 ? new int[]{y} : new int[]{y + dy, y - dy}) {
                if (canStand(world, x, candidate, z)) {
                    return Optional.of(new Location(world, x + 0.5, candidate, z + 0.5));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean canStand(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return false;
        }
        Block floor = world.getBlockAt(x, y - 1, z);
        return floor.getType().isSolid() && open(world.getBlockAt(x, y, z)) && open(world.getBlockAt(x, y + 1, z));
    }

    private static boolean open(Block block) {
        return !block.getType().isSolid() && !block.isLiquid();
    }
}
