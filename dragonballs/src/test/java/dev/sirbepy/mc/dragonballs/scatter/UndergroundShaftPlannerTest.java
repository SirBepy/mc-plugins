package dev.sirbepy.mc.dragonballs.scatter;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UndergroundShaftPlannerTest {

    @Test
    void depthControlsHowManyLayersDownTheChamberSits() {
        for (int depth = 10; depth <= 30; depth++) {
            UndergroundShaftPlanner.ShaftShape shape = UndergroundShaftPlanner.plan(new Random(1), depth);
            Set<String> cleared = toKeySet(shape.clearOffsets());

            // The chamber's top layer is "depth" blocks below the ground block (dy = 0); the layer
            // just above it (dy = -depth + 1) is still plain shaft, not chamber.
            assertTrue(cleared.contains(key(0, -depth, 0)), "chamber top missing at depth " + depth);
            assertTrue(cleared.contains(key(0, -depth + 1, 0)), "shaft bottom missing at depth " + depth);
        }
    }

    @Test
    void shaftIsOneConnectedOpenColumnFromSurfaceToChamberFloor() {
        UndergroundShaftPlanner.ShaftShape shape = UndergroundShaftPlanner.plan(new Random(42), 20);
        Set<String> cleared = toKeySet(shape.clearOffsets());

        // The fixed 2x2 core is present at every shaft layer (0 down to the chamber's top), and the
        // chamber's 3x3 footprint contains that core, so the whole column is connected by construction.
        for (int dy = 0; dy > -20; dy--) {
            assertTrue(cleared.contains(key(0, dy, 0)), "core missing at dy=" + dy);
            assertTrue(cleared.contains(key(1, dy, 0)), "core missing at dy=" + dy);
            assertTrue(cleared.contains(key(0, dy, 1)), "core missing at dy=" + dy);
            assertTrue(cleared.contains(key(1, dy, 1)), "core missing at dy=" + dy);
        }
    }

    @Test
    void chamberIsRoughlyThreeByThreeByTwoWithTheHeadStandingOnAnUnclearedFloor() {
        UndergroundShaftPlanner.ShaftShape shape = UndergroundShaftPlanner.plan(new Random(5), 15);
        Set<String> cleared = toKeySet(shape.clearOffsets());

        int chamberTop = -15;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                assertTrue(cleared.contains(key(dx, chamberTop, dz)));
                assertTrue(cleared.contains(key(dx, chamberTop - 1, dz)));
            }
        }
        // The floor itself (one below the chamber) must NOT be cleared - the head stands on it.
        int[] floor = shape.floorOffset();
        assertTrue(!cleared.contains(key(floor[0], floor[1], floor[2])));
        assertEquals(floor[1] + 1, shape.headOffset()[1]);
    }

    @Test
    void rimSitsAtSurfaceLevelJustOutsideTheCore() {
        UndergroundShaftPlanner.ShaftShape shape = UndergroundShaftPlanner.plan(new Random(9), 12);

        assertTrue(!shape.rimOffsets().isEmpty());
        for (int[] offset : shape.rimOffsets()) {
            boolean inCore = (offset[0] == 0 || offset[0] == 1) && (offset[1] == 0 || offset[1] == 1);
            assertTrue(!inCore, "rim offset overlaps the core: " + offset[0] + "," + offset[1]);
        }
    }

    private static Set<String> toKeySet(List<int[]> offsets) {
        Set<String> keys = new HashSet<>();
        for (int[] offset : offsets) {
            keys.add(key(offset[0], offset[1], offset[2]));
        }
        return keys;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
