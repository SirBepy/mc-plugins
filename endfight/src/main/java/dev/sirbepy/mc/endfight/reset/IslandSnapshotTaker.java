package dev.sirbepy.mc.endfight.reset;

import dev.sirbepy.mc.endfight.arena.ChunkTickets;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads the whole main-island box straight off the live world and hands it to
 * {@link IslandSnapshotStore}, with the chunks force-loaded for the duration (same ticket helper
 * {@link dev.sirbepy.mc.endfight.arena.ArenaBuilder} uses). */
public final class IslandSnapshotTaker implements SnapshotCapture {

    /** Covers x -64..64, z -64..64 - one block past the box itself, so the ticket radius always
     * includes every chunk the box touches. */
    private static final int RADIUS_BLOCKS = 65;
    private static final int BOX_HALF = 64;
    /** The tallest pillar+cage tops out at 107 (103 + 4); 160 leaves headroom. */
    private static final int MAX_CAPTURE_Y = 160;

    private final Plugin plugin;
    private final IslandSnapshotStore store;
    private final Logger logger;

    public IslandSnapshotTaker(Plugin plugin, IslandSnapshotStore store) {
        this.plugin = plugin;
        this.store = store;
        this.logger = plugin.getLogger();
    }

    public static SnapshotBounds boundsFor(World world) {
        int minY = world.getMinHeight();
        int maxY = Math.min(MAX_CAPTURE_Y, world.getMaxHeight() - 1);
        return new SnapshotBounds(-BOX_HALF, BOX_HALF, minY, Math.max(minY, maxY), -BOX_HALF, BOX_HALF);
    }

    /** Captures {@code end} and writes it to {@link IslandSnapshotStore}; logs size/timing either
     * way, and never throws - a snapshot failure should never take the server down with it. */
    @Override
    public SnapshotStats capture(World end) {
        SnapshotBounds bounds = boundsFor(end);
        ChunkTickets.load(end, plugin, RADIUS_BLOCKS);
        try {
            BlockDataLookup lookup = (x, y, z) -> end.getBlockAt(x, y, z).getBlockData().getAsString();
            SnapshotStats stats = store.save(bounds, lookup);
            logger.info("Island snapshot captured: " + stats.blockCount() + " blocks in " + stats.millis()
                    + "ms, " + stats.fileSizeBytes() + " bytes compressed.");
            return stats;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not capture the island snapshot", e);
            return new SnapshotStats(bounds.volume(), 0, 0);
        } finally {
            ChunkTickets.unload(end, plugin, RADIUS_BLOCKS);
        }
    }
}
