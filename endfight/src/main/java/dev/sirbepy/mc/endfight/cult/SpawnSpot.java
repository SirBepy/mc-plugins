package dev.sirbepy.mc.endfight.cult;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.function.IntPredicate;

/**
 * Where a cultist's feet go. Posts and gate spawn points sit at the fortress's one surface height,
 * but the island's real ground under them can be a few blocks higher, and a mob spawned inside
 * end stone suffocates; so it rises to the first spot with two free blocks.
 */
final class SpawnSpot {

    static final int MAX_RISE = 6;

    private SpawnSpot() {
    }

    /** The lowest y from {@code y} up to {@code y + MAX_RISE} with room for a mob's feet and head;
     * {@code y} itself if there is none. */
    static int feetY(int y, IntPredicate solidAt) {
        for (int candidate = y; candidate <= y + MAX_RISE; candidate++) {
            if (!solidAt.test(candidate) && !solidAt.test(candidate + 1)) {
                return candidate;
            }
        }
        return y;
    }

    static Location at(World world, int x, int y, int z) {
        int feet = feetY(y, candidate -> world.getBlockAt(x, candidate, z).isSolid());
        return new Location(world, x + 0.5, feet, z + 0.5);
    }
}
