package dev.sirbepy.mc.endfight.reset;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestorePlanTest {

    private static final SnapshotBounds BOUNDS = new SnapshotBounds(-1, 1, 0, 0, -1, 1);

    private static IslandSnapshot snapshotOf(BlockDataLookup lookup) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IslandSnapshotCodec.encode(BOUNDS, lookup, out);
        return IslandSnapshotCodec.decode(new ByteArrayInputStream(out.toByteArray()));
    }

    @Test
    void onlyListsBlocksThatActuallyDiffer() throws IOException {
        IslandSnapshot snapshot = snapshotOf((x, y, z) -> "minecraft:end_stone");

        // The current world matches the snapshot everywhere except one block.
        Map<String, String> current = new HashMap<>();
        for (int x = BOUNDS.minX(); x <= BOUNDS.maxX(); x++) {
            for (int z = BOUNDS.minZ(); z <= BOUNDS.maxZ(); z++) {
                current.put(x + "," + z, "minecraft:end_stone");
            }
        }
        current.put("1,1", "minecraft:dirt"); // a player dug here and left dirt
        BlockDataLookup currentLookup = (x, y, z) -> current.get(x + "," + z);

        List<ChangedBlock> changes = RestorePlan.diff(snapshot, currentLookup);

        assertEquals(1, changes.size());
        ChangedBlock change = changes.get(0);
        assertEquals(1, change.x());
        assertEquals(1, change.z());
        assertEquals("minecraft:end_stone", change.targetBlockData());
    }

    @Test
    void anIdenticalWorldProducesNoChanges() throws IOException {
        IslandSnapshot snapshot = snapshotOf((x, y, z) -> "minecraft:obsidian");

        List<ChangedBlock> changes = RestorePlan.diff(snapshot, (x, y, z) -> "minecraft:obsidian");

        assertTrue(changes.isEmpty());
    }

    @Test
    void everyBlockDifferentProducesEveryBlockAsAChange() throws IOException {
        IslandSnapshot snapshot = snapshotOf((x, y, z) -> "minecraft:obsidian");

        List<ChangedBlock> changes = RestorePlan.diff(snapshot, (x, y, z) -> "minecraft:air");

        assertEquals((int) BOUNDS.volume(), changes.size());
    }
}
