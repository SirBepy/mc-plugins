package dev.sirbepy.mc.endfight.reset;

/**
 * A decoded whole-island snapshot: the box it covers, the palette of distinct {@code BlockData}
 * strings, and one palette index per block (kept as {@code short} regardless of the on-disk index
 * width - real islands never approach 65536 distinct block states). Immutable once decoded.
 */
public final class IslandSnapshot {

    private final SnapshotBounds bounds;
    private final String[] palette;
    private final short[] indices;

    IslandSnapshot(SnapshotBounds bounds, String[] palette, short[] indices) {
        if ((long) indices.length != bounds.volume()) {
            throw new IllegalArgumentException("Snapshot index count " + indices.length
                    + " does not match its bounds' volume " + bounds.volume());
        }
        this.bounds = bounds;
        this.palette = palette;
        this.indices = indices;
    }

    public SnapshotBounds bounds() {
        return bounds;
    }

    public int paletteSize() {
        return palette.length;
    }

    /** {@code null} outside the snapshot's own box - callers must check {@link SnapshotBounds#contains}. */
    public String blockDataAt(int x, int y, int z) {
        if (!bounds.contains(x, y, z)) {
            return null;
        }
        int idx = indices[(int) bounds.indexOf(x, y, z)] & 0xFFFF;
        return palette[idx];
    }

    /** Streams every block in the box without materialising a huge intermediate list. */
    public void forEach(BlockVisitor visitor) {
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    int idx = indices[(int) bounds.indexOf(x, y, z)] & 0xFFFF;
                    visitor.visit(x, y, z, palette[idx]);
                }
            }
        }
    }
}
