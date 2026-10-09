package dev.sirbepy.mc.guild.listener;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Remembers which ore blocks a player placed, so Mine progress can skip them: without this, Silk
 * Touch lets one ore be mined, placed and broken again with a plain pickaxe forever. A mark lives in
 * its chunk's PersistentDataContainer as one packed long per block, so it survives restarts; only ore
 * (and Ancient Debris) is tracked, so a build site full of stone and dirt never grows the container.
 * A piston push drops the mark instead of following it to its new spot, possibly in another chunk:
 * that bookkeeping is a lot for a loophole that already needs a redstone contraption to use, so a
 * pushed ore just reverts to counting normally once it settles.
 */
final class MineGuard {

    private static final NamespacedKey PLACED_ORE = new NamespacedKey("guild", "placed-ore");

    private MineGuard() {
    }

    /** The ore types Mine contracts target: every vanilla *_ORE plus Ancient Debris. */
    static boolean isOre(Material type) {
        return type == Material.ANCIENT_DEBRIS || type.name().endsWith("_ORE");
    }

    /** Marks a just-placed ore so breaking it later doesn't count toward Mine. */
    static void mark(Block block) {
        if (!isOre(block.getType())) {
            return;
        }
        Chunk chunk = block.getChunk();
        Set<Long> positions = positions(chunk);
        positions.add(pack(block));
        save(chunk, positions);
    }

    /** True when this block was placed by a player and never broken (or pushed) since. */
    static boolean isPlayerPlaced(Block block) {
        // Every block break on the server lands here, so skip the container read for anything
        // that can never carry a mark.
        return isOre(block.getType()) && positions(block.getChunk()).contains(pack(block));
    }

    /** Clears a block's mark, whether or not it had one; called on every break so marks never leak. */
    static void unmark(Block block) {
        if (!isOre(block.getType())) {
            return;
        }
        Chunk chunk = block.getChunk();
        Set<Long> positions = positions(chunk);
        if (positions.remove(pack(block))) {
            save(chunk, positions);
        }
    }

    private static Set<Long> positions(Chunk chunk) {
        long[] stored = chunk.getPersistentDataContainer()
                .getOrDefault(PLACED_ORE, PersistentDataType.LONG_ARRAY, new long[0]);
        Set<Long> positions = new LinkedHashSet<>(stored.length);
        for (long value : stored) {
            positions.add(value);
        }
        return positions;
    }

    private static void save(Chunk chunk, Set<Long> positions) {
        PersistentDataContainer data = chunk.getPersistentDataContainer();
        if (positions.isEmpty()) {
            data.remove(PLACED_ORE);
            return;
        }
        long[] array = new long[positions.size()];
        int i = 0;
        for (long value : positions) {
            array[i++] = value;
        }
        data.set(PLACED_ORE, PersistentDataType.LONG_ARRAY, array);
    }

    /** Packs a block's position within its chunk: 4 bits x, 4 bits z, the rest y (small, shifted up). */
    private static long pack(Block block) {
        long x = block.getX() & 0xF;
        long z = block.getZ() & 0xF;
        long y = block.getY();
        return x | (z << 4) | (y << 8);
    }
}
