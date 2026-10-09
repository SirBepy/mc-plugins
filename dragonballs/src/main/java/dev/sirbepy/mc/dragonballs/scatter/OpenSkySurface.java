package dev.sirbepy.mc.dragonballs.scatter;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * The open-to-the-sky surface spot in one column: the highest block by
 * {@link HeightMap#MOTION_BLOCKING_NO_LEAVES}, only if that matches
 * {@link HeightMap#MOTION_BLOCKING} too (so a leaf canopy, or a trunk's own top, is never taken for
 * ground) and the column stays inside the world's height bounds. Shared by
 * {@link dev.sirbepy.mc.dragonballs.placed.TownEviction} (an evicted ball's landing spot) and
 * {@link ScatterFallback} (the scatter's plain-ground fallback): both need the same "nothing but
 * sky above it" rule, but each then applies its own floor-material acceptance on top of this spot
 * (eviction wants any dry solid ground, the Overworld fallback wants {@link SurfaceClassifier} SOLID,
 * the End fallback wants {@code END_STONE} only).
 */
public final class OpenSkySurface {

    private OpenSkySurface() {
    }

    /** The column's candidate floor: its highest sky-open block, and that block's own material. */
    public record Spot(int floorY, Material floorMaterial) {
    }

    /** Null if the column has a leaf canopy (or a trunk top) over its highest solid block, or if
     * that floor sits too close to the world's height limits for a ball to rest above it. */
    public static Spot find(World world, int x, int z) {
        int floorY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (floorY < world.getMinHeight() || floorY + 1 >= world.getMaxHeight()
                || world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING) != floorY) {
            return null;
        }
        return new Spot(floorY, world.getBlockAt(x, floorY, z).getType());
    }
}
