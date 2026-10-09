package dev.sirbepy.mc.endfight.arena;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/** An in-memory {@link BlockLookup} for {@code PillarScannerTest}, no {@code World} needed. */
final class FakeBlockLookup implements BlockLookup {

    private final Map<Long, Material> blocks = new HashMap<>();

    void set(int x, int y, int z, Material material) {
        blocks.put(key(x, y, z), material);
    }

    @Override
    public Material at(int x, int y, int z) {
        return blocks.getOrDefault(key(x, y, z), Material.AIR);
    }

    private static long key(int x, int y, int z) {
        return (((long) x) << 40) ^ (((long) y) << 20) ^ (long) z;
    }
}
