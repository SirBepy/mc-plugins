package dev.sirbepy.mc.endfight.arena;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies an {@link ArenaLayout} to the real End world: force-loads the needed chunks with a
 * plugin chunk ticket, places every block with physics off, applies banner patterns, then releases
 * the chunks. The geometry it applies is covered directly by {@code ArenaLayoutTest}; this class
 * itself is exercised end-to-end (against a MockBukkit {@code WorldMock}) by
 * {@code ArenaServiceTest}.
 */
public final class ArenaBuilder {

    /** Covers the pillar ring (radius 42) and the fortress (radius ~22) with margin. */
    private static final int RADIUS_BLOCKS = 60;
    private static final int RADIUS_CHUNKS = (RADIUS_BLOCKS >> 4) + 1;

    private final Plugin plugin;

    public ArenaBuilder(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Reads the 10 pillars back from the world. Caller must hold the chunk tickets (see
     * {@link #loadArenaChunks}/{@link #unloadArenaChunks}) for the duration. Clamps to the
     * world's own height bounds (rather than {@link PillarScanner}'s generous 0..256 scan range)
     * so a shorter world - a MockBukkit {@code WorldMock} in tests, or a differently configured
     * real one - never gets asked for an out-of-range block. */
    public List<Pillar> scan(World end) {
        return PillarScanner.scanAll(lookup(end));
    }

    private static BlockLookup lookup(World end) {
        int minY = end.getMinHeight();
        int maxY = end.getMaxHeight();
        return (x, y, z) -> (y < minY || y >= maxY) ? org.bukkit.Material.AIR : end.getBlockAt(x, y, z).getType();
    }

    /** Scans the pillars, builds the layout from them and places every block. The fortress and
     * altar stand on {@code knownTerrain} when an earlier build already read it (a rebuild would
     * otherwise read our own foundations as the ground); otherwise the island's ground is read now,
     * falling back to the pillars' average ground if no end stone turns up. */
    public BuildResult build(World end, Terrain knownTerrain) {
        loadArenaChunks(end);
        try {
            List<Pillar> pillars = scan(end);
            Terrain terrain = knownTerrain != null ? knownTerrain
                    : TerrainScanner.scan(lookup(end)).orElseGet(() -> {
                        int average = averageSurfaceY(pillars);
                        return new Terrain(average, average);
                    });
            ArenaLayout layout = ArenaLayout.build(pillars, terrain);
            apply(end, layout);
            return new BuildResult(pillars, terrain, layout);
        } finally {
            unloadArenaChunks(end);
        }
    }

    static int averageSurfaceY(List<Pillar> pillars) {
        if (pillars.isEmpty()) {
            return 65;
        }
        int sum = 0;
        for (Pillar p : pillars) {
            sum += p.surfaceY();
        }
        return sum / pillars.size();
    }

    public record BuildResult(List<Pillar> pillars, Terrain terrain, ArenaLayout layout) {
    }

    /** Sets every tracked arena block back to air - testing/repair only. */
    public void clear(World end, ArenaRegistry registry) {
        loadArenaChunks(end);
        try {
            for (long packed : registry.arenaBlockKeys()) {
                int[] xyz = ArenaRegistry.unpack(packed);
                end.getBlockAt(xyz[0], xyz[1], xyz[2]).setType(org.bukkit.Material.AIR, false);
            }
        } finally {
            unloadArenaChunks(end);
        }
    }

    private void apply(World end, ArenaLayout layout) {
        for (Placement placement : layout.placements()) {
            try {
                placeOne(end, placement);
            } catch (RuntimeException e) {
                // One bad/unsupported blockstate string (a future Paper version renaming a
                // property, or - as found while testing against MockBukkit's own block data
                // registry - a gap in a test double's data) should never abort the whole arena.
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Could not place " + placement.blockData() + " at " + placement.x() + ","
                                + placement.y() + "," + placement.z(), e);
            }
        }
    }

    private void placeOne(World end, Placement placement) {
        Block block = end.getBlockAt(placement.x(), placement.y(), placement.z());
        block.setBlockData(Bukkit.createBlockData(placement.blockData()), false);
        if (!placement.bannerPatterns().isEmpty() && block.getState() instanceof Banner banner) {
            List<Pattern> patterns = new ArrayList<>(placement.bannerPatterns().size());
            for (BannerPattern bp : placement.bannerPatterns()) {
                PatternType type = org.bukkit.Registry.BANNER_PATTERN.get(
                        org.bukkit.NamespacedKey.minecraft(bp.patternKey()));
                if (type != null) {
                    patterns.add(new Pattern(org.bukkit.DyeColor.valueOf(bp.dyeColor().toUpperCase(java.util.Locale.ROOT)), type));
                }
            }
            banner.setPatterns(patterns);
            banner.update(true, false);
        }
    }

    private void loadArenaChunks(World end) {
        for (int cx = -RADIUS_CHUNKS; cx <= RADIUS_CHUNKS; cx++) {
            for (int cz = -RADIUS_CHUNKS; cz <= RADIUS_CHUNKS; cz++) {
                try {
                    end.addPluginChunkTicket(cx, cz, plugin);
                    end.getChunkAt(cx, cz);
                } catch (RuntimeException e) {
                    // A test double (or a future API change) that doesn't implement ticketing is
                    // not fatal here - getBlockAt below still works either way.
                    plugin.getLogger().log(java.util.logging.Level.FINE, "Could not force-load chunk " + cx + "," + cz, e);
                }
            }
        }
    }

    private void unloadArenaChunks(World end) {
        for (int cx = -RADIUS_CHUNKS; cx <= RADIUS_CHUNKS; cx++) {
            for (int cz = -RADIUS_CHUNKS; cz <= RADIUS_CHUNKS; cz++) {
                try {
                    end.removePluginChunkTicket(cx, cz, plugin);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(java.util.logging.Level.FINE, "Could not release chunk ticket " + cx + "," + cz, e);
                }
            }
        }
    }
}
