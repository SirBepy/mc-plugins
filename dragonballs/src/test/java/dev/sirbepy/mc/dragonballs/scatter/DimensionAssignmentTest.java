package dev.sirbepy.mc.dragonballs.scatter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionAssignmentTest {

    @Test
    void splitsSevenStarsIntoTheRequestedGroupSizes() {
        DimensionAssignment assignment = DimensionAssignment.assign(new Random(1), 2, 1);
        assertEquals(2, assignment.nether().size());
        assertEquals(1, assignment.end().size());
        assertEquals(4, assignment.overworld().size());
    }

    @Test
    void everyStarAppearsExactlyOnce() {
        DimensionAssignment assignment = DimensionAssignment.assign(new Random(2), 2, 1);
        List<Integer> all = Stream.of(assignment.overworld(), assignment.nether(), assignment.end())
                .flatMap(List::stream)
                .sorted()
                .collect(Collectors.toList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), all);
    }

    @Test
    void zeroCountsKeepEverythingInTheOverworld() {
        DimensionAssignment assignment = DimensionAssignment.assign(new Random(3), 0, 0);
        assertTrue(assignment.nether().isEmpty());
        assertTrue(assignment.end().isEmpty());
        assertEquals(7, assignment.overworld().size());
    }

    @Test
    void countsLargerThanSevenAreClampedRatherThanOverflowing() {
        DimensionAssignment assignment = DimensionAssignment.assign(new Random(4), 10, 10);
        assertEquals(7, assignment.nether().size());
        assertEquals(0, assignment.end().size());
        assertTrue(assignment.overworld().isEmpty());
    }
}
