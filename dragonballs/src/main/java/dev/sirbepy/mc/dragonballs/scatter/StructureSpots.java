package dev.sirbepy.mc.dragonballs.scatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Where inside one structure piece a ball can sit: an open block with a floor under it and room for
 * a player to stand there too, so a ball is never wedged into a one-block gap nobody can walk up to.
 * The standing block may be one above the piece's top (its roof), so rooftops count alongside rooms
 * and streets. Pure (blocks come from a {@link BlockProbe}) so it's testable without a server.
 */
public final class StructureSpots {

    /** What {@link #standable} needs to know about the world at a block position. */
    public interface BlockProbe {
        /** Plain or cave air: a ball can go here, a player can stand here. */
        boolean isOpen(int x, int y, int z);

        /** A solid, non-hazardous block a ball can rest on. */
        boolean isFloor(int x, int y, int z);
    }

    private StructureSpots() {
    }

    /** Every {x, y, z} inside the box (bounds inclusive) where a ball can rest; y is the ball's own
     * block, which may be {@code maxY + 1} (standing on the piece's roof). */
    public static List<int[]> standable(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                        BlockProbe probe) {
        List<int[]> spots = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY + 1; y <= maxY + 1; y++) {
                    if (probe.isFloor(x, y - 1, z) && probe.isOpen(x, y, z) && probe.isOpen(x, y + 1, z)) {
                        spots.add(new int[]{x, y, z});
                    }
                }
            }
        }
        return spots;
    }
}
