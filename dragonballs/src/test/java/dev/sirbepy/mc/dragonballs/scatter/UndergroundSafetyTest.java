package dev.sirbepy.mc.dragonballs.scatter;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UndergroundSafetyTest {

    @Test
    void safeWhenNothingIsBlockedAndTheFloorClearsMinHeightByEnough() {
        UndergroundShaftPlanner.ShaftShape shape = UndergroundShaftPlanner.plan(new Random(1), 15);

        assertTrue(UndergroundSafety.isSafe(shape, -64, 6, offset -> false));
    }

    @Test
    void rejectsAShaftThatTouchesLiquidOrBedrock() {
        UndergroundShaftPlanner.ShaftShape shape = UndergroundShaftPlanner.plan(new Random(1), 15);
        int[] someClearedOffset = shape.clearOffsets().get(0);

        boolean safe = UndergroundSafety.isSafe(shape, -64, 6,
                offset -> offset[0] == someClearedOffset[0] && offset[1] == someClearedOffset[1]
                        && offset[2] == someClearedOffset[2]);

        assertFalse(safe, "a blocked offset inside the shaft itself must reject the shape");
    }

    @Test
    void rejectsAShaftTouchingABlockDirectlyAdjacentToTheCarvedColumn() {
        UndergroundShaftPlanner.ShaftShape shape = UndergroundShaftPlanner.plan(new Random(1), 15);
        int[] cleared = shape.clearOffsets().get(0);
        int[] neighbour = {cleared[0] + 1, cleared[1], cleared[2]};

        boolean safe = UndergroundSafety.isSafe(shape, -64, 6,
                offset -> offset[0] == neighbour[0] && offset[1] == neighbour[1] && offset[2] == neighbour[2]);

        assertFalse(safe, "a blocked face-neighbour of a carved block must reject the shape too");
    }

    @Test
    void rejectsAShaftWhoseFloorComesTooCloseToTheWorldsMinHeight() {
        UndergroundShaftPlanner.ShaftShape shape = UndergroundShaftPlanner.plan(new Random(1), 30);
        int floorY = shape.floorOffset()[1];

        // minHeight placed exactly at the clearance boundary minus one block, so the floor is one
        // block too shallow.
        int minHeight = floorY - 5;

        assertFalse(UndergroundSafety.isSafe(shape, minHeight, 6, offset -> false));
    }
}
