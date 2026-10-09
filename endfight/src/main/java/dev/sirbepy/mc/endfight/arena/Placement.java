package dev.sirbepy.mc.endfight.arena;

import java.util.List;

/**
 * One block the arena wants at (x, y, z). {@code blockData} is a full {@code minecraft:id[props]}
 * string ready for {@code Bukkit.createBlockData}. {@code bannerPatterns} is non-empty only for a
 * banner block, applied to its {@code Banner} block state after placement (see module docstring on
 * why a plain BlockData string can't express banner pattern layers).
 */
public record Placement(int x, int y, int z, String blockData, List<BannerPattern> bannerPatterns) {

    public Placement(int x, int y, int z, String blockData) {
        this(x, y, z, blockData, List.of());
    }
}
