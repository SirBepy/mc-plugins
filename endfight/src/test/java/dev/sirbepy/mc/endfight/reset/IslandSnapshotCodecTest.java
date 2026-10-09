package dev.sirbepy.mc.endfight.reset;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IslandSnapshotCodecTest {

    private static final SnapshotBounds SMALL_BOUNDS = new SnapshotBounds(-2, 2, -1, 1, -2, 2);

    private static String blockAt(int x, int y, int z) {
        if (x == 0 && y == 0 && z == 0) {
            return "minecraft:obsidian";
        }
        if (y == -1) {
            return "minecraft:end_stone";
        }
        return "minecraft:air";
    }

    @Test
    void roundTripsBoundsPaletteAndEveryBlock() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IslandSnapshotCodec.encode(SMALL_BOUNDS, IslandSnapshotCodecTest::blockAt, out);

        IslandSnapshot decoded = IslandSnapshotCodec.decode(new ByteArrayInputStream(out.toByteArray()));

        assertEquals(SMALL_BOUNDS, decoded.bounds());
        assertEquals(3, decoded.paletteSize(), "air, end_stone, obsidian");
        for (int x = SMALL_BOUNDS.minX(); x <= SMALL_BOUNDS.maxX(); x++) {
            for (int y = SMALL_BOUNDS.minY(); y <= SMALL_BOUNDS.maxY(); y++) {
                for (int z = SMALL_BOUNDS.minZ(); z <= SMALL_BOUNDS.maxZ(); z++) {
                    assertEquals(blockAt(x, y, z), decoded.blockDataAt(x, y, z), "mismatch at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void decodeRejectsATruncatedFile() {
        byte[] tooShort = {1, 2, 3};
        assertThrows(IOException.class, () -> IslandSnapshotCodec.decode(new ByteArrayInputStream(tooShort)));
    }

    @Test
    void decodeRejectsABadMagicNumber() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        data.writeInt(0xDEADBEEF);
        data.writeInt(1);
        assertThrows(IOException.class, () -> IslandSnapshotCodec.decode(new ByteArrayInputStream(out.toByteArray())));
    }

    @Test
    void decodeRejectsAnUnsupportedVersion() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IslandSnapshotCodec.encode(SMALL_BOUNDS, IslandSnapshotCodecTest::blockAt, out);
        byte[] bytes = out.toByteArray();
        // Version is the second 4-byte int, right after the magic number.
        bytes[4] = 0;
        bytes[5] = 0;
        bytes[6] = 0;
        bytes[7] = 99;
        assertThrows(IOException.class, () -> IslandSnapshotCodec.decode(new ByteArrayInputStream(bytes)));
    }

    @Test
    void widePaletteUsesTwoByteIndicesAndStillRoundTrips() throws IOException {
        // 300 distinct block states forces the 2-byte index path.
        SnapshotBounds bounds = new SnapshotBounds(0, 299, 0, 0, 0, 0);
        BlockDataLookup lookup = (x, y, z) -> "minecraft:state_" + x;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IslandSnapshotCodec.encode(bounds, lookup, out);
        IslandSnapshot decoded = IslandSnapshotCodec.decode(new ByteArrayInputStream(out.toByteArray()));

        assertEquals(300, decoded.paletteSize());
        for (int x = 0; x < 300; x++) {
            assertEquals("minecraft:state_" + x, decoded.blockDataAt(x, 0, 0));
        }
    }
}
