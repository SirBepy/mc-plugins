package dev.sirbepy.mc.endfight.arena;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Force-loads/releases a square of chunks around 0,0 with a plugin chunk ticket. Shared between
 * {@link ArenaBuilder} (radius ~60, the pillar ring + fortress) and the island reset's own snapshot
 * capture/restore (radius 64, the whole snapshot box) so both go through one tested place rather
 * than two copies of the same ticket bookkeeping.
 */
public final class ChunkTickets {

    private ChunkTickets() {
    }

    public static void load(World world, Plugin plugin, int radiusBlocks) {
        int radiusChunks = toChunkRadius(radiusBlocks);
        for (int cx = -radiusChunks; cx <= radiusChunks; cx++) {
            for (int cz = -radiusChunks; cz <= radiusChunks; cz++) {
                try {
                    world.addPluginChunkTicket(cx, cz, plugin);
                    world.getChunkAt(cx, cz);
                } catch (RuntimeException e) {
                    // A test double (or a future API change) that doesn't implement ticketing is
                    // not fatal here - getBlockAt below still works either way.
                    plugin.getLogger().log(Level.FINE, "Could not force-load chunk " + cx + "," + cz, e);
                }
            }
        }
    }

    public static void unload(World world, Plugin plugin, int radiusBlocks) {
        int radiusChunks = toChunkRadius(radiusBlocks);
        for (int cx = -radiusChunks; cx <= radiusChunks; cx++) {
            for (int cz = -radiusChunks; cz <= radiusChunks; cz++) {
                try {
                    world.removePluginChunkTicket(cx, cz, plugin);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.FINE, "Could not release chunk ticket " + cx + "," + cz, e);
                }
            }
        }
    }

    private static int toChunkRadius(int radiusBlocks) {
        return (radiusBlocks >> 4) + 1;
    }
}
