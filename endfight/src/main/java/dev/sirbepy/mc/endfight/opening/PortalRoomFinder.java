package dev.sirbepy.mc.endfight.opening;

import dev.sirbepy.mc.endfight.arena.BlockLookup;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Finds a safe place to stand right beside an End portal frame ring, from blocks alone: used both
 * by {@code /warp end} (inside the stronghold's portal room) and to lift a player back out of a
 * filled portal while the End is still closed, since the stronghold's portal sits over lava.
 */
public final class PortalRoomFinder {

    /** Where to stand (block coordinates of the feet) and the yaw that faces the portal. */
    public record Spot(int x, int y, int z, float yaw) {
    }

    /** The frame ring plus the 3x3 portal is 5x5; standing anywhere inside it means standing on a
     * frame or in the portal. */
    private static final double RING_HALF = 2.5;
    private static final int SEARCH = 7;

    private PortalRoomFinder() {
    }

    /** The spot nearest the portal, level with the frames where possible, or empty if the box holds
     * fewer than 4 frames or nowhere safe to stand. */
    public static Optional<Spot> find(BlockLookup blocks, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long sumX = 0;
        long sumZ = 0;
        int count = 0;
        Map<Integer, Integer> levels = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (blocks.at(x, y, z) == Material.END_PORTAL_FRAME) {
                        sumX += x;
                        sumZ += z;
                        count++;
                        levels.merge(y, 1, Integer::sum);
                    }
                }
            }
        }
        if (count < 4) {
            return Optional.empty();
        }
        double cx = (double) sumX / count + 0.5;
        double cz = (double) sumZ / count + 0.5;
        int frameY = levels.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();

        Spot best = null;
        double bestScore = Double.MAX_VALUE;
        int baseX = (int) Math.floor(cx);
        int baseZ = (int) Math.floor(cz);
        for (int x = baseX - SEARCH; x <= baseX + SEARCH; x++) {
            for (int z = baseZ - SEARCH; z <= baseZ + SEARCH; z++) {
                double dx = x + 0.5 - cx;
                double dz = z + 0.5 - cz;
                if (Math.abs(dx) <= RING_HALF && Math.abs(dz) <= RING_HALF) {
                    continue;
                }
                for (int y = frameY - 3; y <= frameY + 1; y++) {
                    if (!standable(blocks, x, y, z)) {
                        continue;
                    }
                    double score = dx * dx + dz * dz + 4.0 * Math.abs(y - frameY);
                    if (score < bestScore) {
                        bestScore = score;
                        // Minecraft yaw: 0 faces +Z, 90 faces -X.
                        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
                        best = new Spot(x, y, z, yaw);
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** A spot of {@code size} blocks around a point, e.g. the player's own position in a portal. */
    public static Optional<Spot> findAround(BlockLookup blocks, int x, int y, int z, int size) {
        return find(blocks, x - size, y - size, z - size, x + size, y + size, z + size);
    }

    private static boolean standable(BlockLookup blocks, int x, int y, int z) {
        return open(blocks.at(x, y, z)) && open(blocks.at(x, y + 1, z)) && floor(blocks.at(x, y - 1, z));
    }

    private static boolean open(Material material) {
        if (material.isAir()) {
            return true;
        }
        return !material.isSolid() && material != Material.LAVA && material != Material.WATER
                && material != Material.END_PORTAL && material != Material.FIRE;
    }

    private static boolean floor(Material material) {
        return material.isSolid() && material != Material.END_PORTAL_FRAME && material != Material.MAGMA_BLOCK;
    }
}
