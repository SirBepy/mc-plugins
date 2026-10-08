package dev.sirbepy.mc.dragonballs.scatter;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A tiny house: a 3x3 floor at y 0, walls left out, a 3x3 roof at y 3 (so a 2-high room inside). */
class StructureSpotsTest {

    private static final class Grid implements StructureSpots.BlockProbe {
        final Set<String> solid = new HashSet<>();

        void solid(int x, int y, int z) {
            solid.add(x + "," + y + "," + z);
        }

        @Override
        public boolean isOpen(int x, int y, int z) {
            return !solid.contains(x + "," + y + "," + z);
        }

        @Override
        public boolean isFloor(int x, int y, int z) {
            return solid.contains(x + "," + y + "," + z);
        }
    }

    private static Grid house() {
        Grid grid = new Grid();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                grid.solid(x, 0, z);
                grid.solid(x, 3, z);
            }
        }
        return grid;
    }

    private static Set<String> keys(List<int[]> spots) {
        Set<String> keys = new HashSet<>();
        for (int[] spot : spots) {
            keys.add(spot[0] + "," + spot[1] + "," + spot[2]);
        }
        return keys;
    }

    @Test
    void findsTheRoomFloorAndTheRoofTop() {
        List<int[]> spots = StructureSpots.standable(0, 0, 0, 2, 3, 2, house());

        Set<String> keys = keys(spots);
        assertEquals(18, keys.size(), keys.toString());
        assertTrue(keys.contains("1,1,1"), "on the room's floor");
        assertTrue(keys.contains("1,4,1"), "on the roof, one above the piece's top");
    }

    @Test
    void neverWedgesABallUnderALowCeiling() {
        Grid grid = house();
        // A 1-high crawlspace: floor at y 0, ceiling at y 2. Nobody could stand next to a ball there.
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                grid.solid(x, 2, z);
            }
        }

        List<int[]> spots = StructureSpots.standable(0, 0, 0, 2, 3, 2, grid);

        assertTrue(spots.stream().noneMatch(spot -> spot[1] == 1), keys(spots).toString());
        assertTrue(keys(spots).contains("1,4,1"), "the roof is still fine");
    }

    @Test
    void ignoresFloorsOutsideTheBox() {
        Grid grid = new Grid();
        grid.solid(10, 0, 10);

        assertTrue(StructureSpots.standable(0, 0, 0, 2, 3, 2, grid).isEmpty());
    }
}
