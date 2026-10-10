package dev.sirbepy.mc.merfolk;

import dev.sirbepy.mc.merfolk.WaterPath.Cell;
import dev.sirbepy.mc.merfolk.WaterPath.Step;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterPathTest {

    /** A sea: solid floor at y 0 and below, water y 1-4, air above, plus solid and open blocks set by hand. */
    private static final class Sea implements WaterPath.Terrain {
        int surface = 4;
        final Set<List<Integer>> solid = new HashSet<>();
        final Set<List<Integer>> open = new HashSet<>();

        @Override
        public Cell at(int x, int y, int z) {
            List<Integer> at = List.of(x, y, z);
            if (y <= 0 || solid.contains(at)) return Cell.SOLID;
            if (open.contains(at)) return Cell.OPEN;
            return y <= surface ? Cell.WATER : Cell.OPEN;
        }

        Sea fill(int x1, int y1, int z1, int x2, int y2, int z2, Set<List<Integer>> into) {
            for (int x = x1; x <= x2; x++) {
                for (int y = y1; y <= y2; y++) {
                    for (int z = z1; z <= z2; z++) into.add(List.of(x, y, z));
                }
            }
            return this;
        }

        Sea wall(int x1, int y1, int z1, int x2, int y2, int z2) {
            return fill(x1, y1, z1, x2, y2, z2, solid);
        }
    }

    private static List<Step> find(Sea sea, int sx, int sz, int tx, int tz) {
        return WaterPath.find(sea, sx, 1, sz, tx + 0.5, 1, tz + 0.5, 1.5, 2000);
    }

    private static Step last(List<Step> steps) {
        return steps.get(steps.size() - 1);
    }

    private static void assertNeverInsideSolid(Sea sea, List<Step> steps) {
        for (Step step : steps) {
            assertNotEquals(Cell.SOLID, sea.at(step.x(), step.y(), step.z()), "feet in a block at " + step);
            assertNotEquals(Cell.SOLID, sea.at(step.x(), step.y() + 1, step.z()), "head in a block at " + step);
        }
    }

    @Test
    void inOpenWaterItWalksTheFloorStraightThere() {
        Sea sea = new Sea();
        List<Step> steps = find(sea, 0, 0, 10, 0);

        assertTrue(steps.size() <= 10, "about one step per block: " + steps);
        assertTrue(steps.stream().allMatch(s -> s.floor() && s.y() == 1), "on the floor the whole way: " + steps);
        assertTrue(Math.abs(last(steps).x() + 0.5 - 10.5) <= 1.5);
    }

    @Test
    void aWallToTheSurfaceIsWalkedAroundThroughItsGap() {
        // A wall across x = 5 from z -6 to 6, up out of the water, with a gap at z 6..7.
        Sea sea = new Sea().wall(5, 1, -6, 5, 6, 5);
        List<Step> steps = find(sea, 0, 0, 10, 0);

        assertNeverInsideSolid(sea, steps);
        assertTrue(Math.abs(last(steps).x() + 0.5 - 10.5) <= 1.5, "got there: " + last(steps));
        assertTrue(steps.stream().anyMatch(s -> s.x() == 5 && s.z() >= 6), "through the gap: " + steps);
        assertTrue(steps.stream().allMatch(Step::floor), "the gap is shorter than swimming: " + steps);
    }

    @Test
    void aSunkenWallWithNoWayAroundIsSwumOverWithTheHeadUnderwater() {
        // Water up to y 6; a wall up to y 3 across the whole sea between x 4 and 5.
        Sea sea = new Sea();
        sea.surface = 6;
        sea.wall(4, 1, -50, 5, 3, 50);
        List<Step> steps = find(sea, 0, 0, 10, 0);

        assertNeverInsideSolid(sea, steps);
        assertTrue(Math.abs(last(steps).x() + 0.5 - 10.5) <= 1.5, "got there: " + last(steps));
        assertTrue(steps.stream().anyMatch(s -> s.y() >= 4), "went up over the wall: " + steps);
        for (Step step : steps) {
            if (!step.floor()) assertEquals(Cell.WATER, sea.at(step.x(), step.y() + 1, step.z()), "head out of the water at " + step);
        }
        assertTrue(last(steps).floor() && last(steps).y() == 1, "and back down to the floor: " + last(steps));
    }

    @Test
    void itNeverSwimsUpToTheSurfaceOrOverAWallThatBreaksIt() {
        // The wall reaches the surface (y 4) everywhere: no way over (the head would leave the water) or around.
        Sea sea = new Sea().wall(5, 1, -50, 5, 4, 50);
        List<Step> steps = find(sea, 0, 0, 10, 0);

        assertNeverInsideSolid(sea, steps);
        assertTrue(steps.stream().allMatch(s -> s.x() < 5), "stayed on its own side: " + steps);
        assertFalse(steps.isEmpty(), "but went as close as it could");
        assertEquals(4, last(steps).x());
    }

    @Test
    void itClimbsOutOntoAShoreOverAOneBlockLedge() {
        // Land from x 6 on: solid up to y 4 (the water's top), so its surface (feet at y 5) is 1 above the
        // water but 4 above the floor. The sea floor rises in steps to a 1-block ledge at x 5.
        Sea sea = new Sea().wall(6, 1, -50, 20, 4, 50).wall(3, 1, -50, 5, 1, 50).wall(4, 2, -50, 5, 2, 50)
                .wall(5, 3, -50, 5, 3, 50);
        List<Step> steps = WaterPath.find(sea, 0, 1, 0, 8.5, 5, 0.5, 1.5, 2000);

        assertNeverInsideSolid(sea, steps);
        Step end = last(steps);
        assertEquals(5, end.y(), "standing on the shore: " + end);
        assertEquals(Cell.OPEN, sea.at(end.x(), end.y(), end.z()));
        for (int i = 1; i < steps.size(); i++) {
            assertTrue(Math.abs(steps.get(i).y() - steps.get(i - 1).y()) <= 1, "one block at a time: " + steps);
        }
    }

    @Test
    void aShoreTwoBlocksAboveShallowWaterIsOutOfReach() {
        // 1 block of water over the floor; land 2 blocks tall from x 6 on. No swimming up in 1 block of water.
        Sea sea = new Sea();
        sea.surface = 1;
        sea.wall(6, 1, -50, 20, 2, 50);
        List<Step> steps = WaterPath.find(sea, 0, 1, 0, 8.5, 3, 0.5, 1.5, 2000);

        assertTrue(steps.stream().allMatch(s -> s.x() < 6), "never got onto it: " + steps);
    }

    @Test
    void outOfBudgetItStillHeadsTheRightWay() {
        Sea sea = new Sea().wall(5, 1, -50, 5, 4, 50);
        List<Step> steps = WaterPath.find(sea, 0, 1, 0, 30.5, 1, 0.5, 1.5, 20);

        assertFalse(steps.isEmpty());
        assertTrue(last(steps).x() > 0, "closer than the start: " + last(steps));
    }

    @Test
    void fitsNeedsTwoClearBlocksAndGroundOrDeepWater() {
        Sea sea = new Sea();
        sea.open.add(List.of(0, 6, 0));
        WaterPath path = new WaterPath(sea);
        assertTrue(path.fits(0, 1, 0), "on the floor");
        assertTrue(path.fits(0, 2, 0), "mid-water, head under");
        assertFalse(path.fits(0, 4, 0), "head above the surface, not on the floor");
        assertFalse(path.fits(0, 6, 0), "in the air");
        sea.wall(1, 2, 0, 1, 2, 0);
        assertFalse(path.fits(1, 1, 0), "head in a block");
    }
}
