package dev.sirbepy.mc.dragonballs.scatter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScatterPlannerTest {

    @Test
    void findCandidateRespectsBoundAndSpacingTogether() {
        Random random = new Random(1);
        List<double[]> avoid = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            double[] candidate = ScatterPlanner.findCandidate(random, 500, -800, 1000, 3000, 4900, avoid, 200, 5000);
            assertNotNull(candidate, "ball " + i + " never found a spot");
            assertTrue(ScatterMath.withinPregenBound(candidate[0], candidate[1], 4900));
            assertTrue(ScatterMath.farEnoughFromAll(candidate[0], candidate[1], avoid, 200));
            double distance = ScatterMath.distance(candidate[0], candidate[1], 500, -800);
            assertTrue(distance >= 999 && distance <= 3001);
            avoid.add(candidate);
        }
    }

    @Test
    void findCandidateGivesUpAfterMaxAttemptsRatherThanLoopingForever() {
        Random random = new Random(2);
        // An impossibly tight pregen bound around a far-off center can never be satisfied.
        double[] candidate = ScatterPlanner.findCandidate(random, 100_000, 100_000, 1000, 3000, 10, List.of(), 200, 50);
        assertNull(candidate);
    }

    @Test
    void spiralOffsetsStartAtOriginAndExpandOutward() {
        List<int[]> offsets = ScatterPlanner.spiralOffsets(2);
        assertEquals(0, offsets.get(0)[0]);
        assertEquals(0, offsets.get(0)[1]);
        // Ring 1 (8 cells) then ring 2 (16 cells): 1 + 8 + 16 = 25 total offsets.
        assertEquals(25, offsets.size());
        for (int[] offset : offsets) {
            int chebyshev = Math.max(Math.abs(offset[0]), Math.abs(offset[1]));
            assertTrue(chebyshev <= 2, "offset (" + offset[0] + "," + offset[1] + ") outside radius 2");
        }
    }
}
