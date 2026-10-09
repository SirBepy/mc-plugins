package dev.sirbepy.mc.endfight.reset;

/**
 * The inclusive box a snapshot covers. Joe's spec: x -64..64, z -64..64, the world's own min
 * height up to 160 (the tallest pillar+cage tops out at 107, so 160 leaves headroom).
 */
public record SnapshotBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {

    public SnapshotBounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("A snapshot box's min must not exceed its max: " + minX + ".." + maxX
                    + ", " + minY + ".." + maxY + ", " + minZ + ".." + maxZ);
        }
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) width() * height() * depth();
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Row-major index of (x,y,z) within the box: x outer, y middle, z inner - must match between
     * {@link IslandSnapshotCodec#encode} and every reader. */
    public long indexOf(int x, int y, int z) {
        long dx = x - minX;
        long dy = y - minY;
        long dz = z - minZ;
        return (dx * height() + dy) * depth() + dz;
    }
}
