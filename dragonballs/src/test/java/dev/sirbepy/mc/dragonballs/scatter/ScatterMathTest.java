package dev.sirbepy.mc.dragonballs.scatter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScatterMathTest {

    @Test
    void annulusOffsetStaysWithinTheRequestedDistanceBand() {
        Random random = new Random(42);
        for (int i = 0; i < 5000; i++) {
            double[] offset = ScatterMath.annulusOffset(random, 1000, 3000);
            double distance = Math.hypot(offset[0], offset[1]);
            assertTrue(distance >= 999.999 && distance <= 3000.001,
                    "distance " + distance + " out of [1000, 3000]");
        }
    }

    @Test
    void annulusOffsetCoversBothNearAndFarEdges() {
        Random random = new Random(7);
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (int i = 0; i < 20000; i++) {
            double[] offset = ScatterMath.annulusOffset(random, 1000, 3000);
            double distance = Math.hypot(offset[0], offset[1]);
            min = Math.min(min, distance);
            max = Math.max(max, distance);
        }
        assertTrue(min < 1100, "never sampled close to the inner edge: min=" + min);
        assertTrue(max > 2900, "never sampled close to the outer edge: max=" + max);
    }

    @Test
    void withinPregenBoundAcceptsInsideAndRejectsOutside() {
        assertTrue(ScatterMath.withinPregenBound(4900, -4900, 4900));
        assertTrue(ScatterMath.withinPregenBound(0, 0, 4900));
        assertFalse(ScatterMath.withinPregenBound(4901, 0, 4900));
        assertFalse(ScatterMath.withinPregenBound(0, -5000, 4900));
    }

    @Test
    void distanceIsPlainEuclidean() {
        assertEquals(5.0, ScatterMath.distance(0, 0, 3, 4), 1e-9);
    }

    @Test
    void farEnoughFromAllRequiresEveryPointToClearMinSpacing() {
        List<double[]> placed = List.of(new double[]{0, 0}, new double[]{1000, 1000});
        assertTrue(ScatterMath.farEnoughFromAll(500, 0, placed, 200));
        assertFalse(ScatterMath.farEnoughFromAll(50, 0, placed, 200));
        assertFalse(ScatterMath.farEnoughFromAll(1050, 1050, placed, 200));
    }

    @Test
    void farEnoughFromAllIsVacuouslyTrueWithNothingPlacedYet() {
        assertTrue(ScatterMath.farEnoughFromAll(0, 0, List.of(), 200));
    }
}
