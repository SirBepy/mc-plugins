package dev.sirbepy.mc.endfight.reset;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure (encode from a {@link BlockDataLookup}, decode to an {@link IslandSnapshot}) binary
 * format for the whole-island snapshot: a version header, the box bounds, a palette of distinct
 * {@code BlockData} strings, then one palette index per block (1 byte if the palette fits in 256
 * entries, 2 otherwise - real islands see a few hundred distinct block states at most, so this
 * keeps the common case small without a hard cap). No server/world access here at all; the GZIP
 * wrapping and file I/O live in {@link IslandSnapshotStore}.
 */
public final class IslandSnapshotCodec {

    static final int MAGIC = 0x45465331; // "island snapshot", version-independent marker
    static final int VERSION = 1;

    private IslandSnapshotCodec() {
    }

    /** Visits every block in {@code bounds} (x outer, y middle, z inner - see
     * {@link SnapshotBounds#indexOf}) through {@code lookup} and writes the whole box. */
    public static void encode(SnapshotBounds bounds, BlockDataLookup lookup, OutputStream rawOut) throws IOException {
        long volume = bounds.volume();
        if (volume > Integer.MAX_VALUE) {
            throw new IOException("Snapshot box too large to encode: " + volume + " blocks");
        }
        int count = (int) volume;
        int[] indices = new int[count];
        Map<String, Integer> paletteIndex = new LinkedHashMap<>();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    String data = lookup.blockDataAt(x, y, z);
                    if (data == null) {
                        data = "minecraft:air";
                    }
                    int idx = paletteIndex.computeIfAbsent(data, k -> paletteIndex.size());
                    indices[(int) bounds.indexOf(x, y, z)] = idx;
                }
            }
        }

        DataOutputStream out = new DataOutputStream(rawOut);
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(bounds.minX());
        out.writeInt(bounds.maxX());
        out.writeInt(bounds.minY());
        out.writeInt(bounds.maxY());
        out.writeInt(bounds.minZ());
        out.writeInt(bounds.maxZ());

        String[] palette = new String[paletteIndex.size()];
        for (Map.Entry<String, Integer> entry : paletteIndex.entrySet()) {
            palette[entry.getValue()] = entry.getKey();
        }
        out.writeInt(palette.length);
        for (String s : palette) {
            out.writeUTF(s);
        }

        boolean wide = palette.length > 256;
        out.writeByte(wide ? 2 : 1);
        for (int idx : indices) {
            if (wide) {
                out.writeShort(idx);
            } else {
                out.writeByte(idx);
            }
        }
        out.flush();
    }

    /** Rejects anything that isn't exactly this format: a short/truncated stream throws
     * (every {@code DataInputStream} read past EOF throws {@link java.io.EOFException}, itself an
     * {@link IOException}), as does a bad magic number, an unsupported version, or a palette index
     * that points outside the palette. */
    public static IslandSnapshot decode(InputStream rawIn) throws IOException {
        DataInputStream in = new DataInputStream(rawIn);
        int magic = in.readInt();
        if (magic != MAGIC) {
            throw new IOException("Not an island snapshot file (bad magic " + magic + ")");
        }
        int version = in.readInt();
        if (version != VERSION) {
            throw new IOException("Unsupported island snapshot version: " + version);
        }
        int minX = in.readInt();
        int maxX = in.readInt();
        int minY = in.readInt();
        int maxY = in.readInt();
        int minZ = in.readInt();
        int maxZ = in.readInt();
        SnapshotBounds bounds = new SnapshotBounds(minX, maxX, minY, maxY, minZ, maxZ);

        int paletteSize = in.readInt();
        if (paletteSize < 0) {
            throw new IOException("Corrupt island snapshot: negative palette size " + paletteSize);
        }
        String[] palette = new String[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            palette[i] = in.readUTF();
        }

        int indexWidth = in.readUnsignedByte();
        if (indexWidth != 1 && indexWidth != 2) {
            throw new IOException("Corrupt island snapshot: bad index width " + indexWidth);
        }

        long volume = bounds.volume();
        if (volume > Integer.MAX_VALUE) {
            throw new IOException("Snapshot box too large to decode: " + volume + " blocks");
        }
        short[] indices = new short[(int) volume];
        for (int i = 0; i < indices.length; i++) {
            int idx = indexWidth == 2 ? in.readUnsignedShort() : in.readUnsignedByte();
            if (idx >= paletteSize) {
                throw new IOException("Corrupt island snapshot: palette index " + idx + " out of range (palette has "
                        + paletteSize + " entries)");
            }
            indices[i] = (short) idx;
        }
        return new IslandSnapshot(bounds, palette, indices);
    }
}
