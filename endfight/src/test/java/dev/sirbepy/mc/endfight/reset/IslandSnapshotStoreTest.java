package dev.sirbepy.mc.endfight.reset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandSnapshotStoreTest {

    @TempDir
    File tempDir;

    private static final SnapshotBounds BOUNDS = new SnapshotBounds(-3, 3, -1, 1, -3, 3);

    private static String uniformBlock(int x, int y, int z) {
        return "minecraft:air";
    }

    @Test
    void doesNotExistUntilSaved() throws IOException {
        IslandSnapshotStore store = new IslandSnapshotStore(new File(tempDir, "island-snapshot.bin"), Logger.getLogger("test"));
        assertFalse(store.exists());

        store.save(BOUNDS, IslandSnapshotStoreTest::uniformBlock);

        assertTrue(store.exists());
    }

    @Test
    void savedSnapshotLoadsBackWithTheSameBoundsAndBlocks() throws IOException {
        IslandSnapshotStore store = new IslandSnapshotStore(new File(tempDir, "island-snapshot.bin"), Logger.getLogger("test"));
        store.save(BOUNDS, (x, y, z) -> x == 0 && y == 0 && z == 0 ? "minecraft:obsidian" : "minecraft:air");

        Optional<IslandSnapshot> loaded = store.load();

        assertTrue(loaded.isPresent());
        assertEquals(BOUNDS, loaded.get().bounds());
        assertEquals("minecraft:obsidian", loaded.get().blockDataAt(0, 0, 0));
        assertEquals("minecraft:air", loaded.get().blockDataAt(1, 0, 0));
    }

    @Test
    void compressedFileIsSmallerThanTheRawEncodedData() throws IOException {
        // A big, perfectly uniform box compresses hard - sanity check the GZIP wrapping is real.
        SnapshotBounds bigUniform = new SnapshotBounds(-20, 20, 0, 5, -20, 20);
        IslandSnapshotStore store = new IslandSnapshotStore(new File(tempDir, "island-snapshot.bin"), Logger.getLogger("test"));

        store.save(bigUniform, IslandSnapshotStoreTest::uniformBlock);

        assertTrue(store.fileSizeBytes() < bigUniform.volume(), "a uniform box's compressed file should be far smaller than one byte per block");
    }

    @Test
    void loadOfAMissingFileIsEmptyNotAnException() {
        IslandSnapshotStore store = new IslandSnapshotStore(new File(tempDir, "nope.bin"), Logger.getLogger("test"));

        assertTrue(store.load().isEmpty());
    }

    @Test
    void loadOfACorruptFileIsEmptyAndLogsRatherThanThrowing() throws IOException {
        File file = new File(tempDir, "island-snapshot.bin");
        try (var out = new java.io.FileOutputStream(file)) {
            out.write(new byte[] {1, 2, 3, 4});
        }
        IslandSnapshotStore store = new IslandSnapshotStore(file, Logger.getLogger("test"));

        assertTrue(store.load().isEmpty());
    }
}
