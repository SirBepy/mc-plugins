package dev.sirbepy.mc.dragonballs.scatter;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructurePlacementTest {

    @Test
    void keepsABoxThatContainsABall() {
        BoundingBox mansion = new BoundingBox(0, 0, 0, 100, 50, 100);

        List<BoundingBox> occupied = StructurePlacement.occupiedFootprints(List.of(new double[]{50, 50}),
                List.of(mansion));

        assertEquals(List.of(mansion), occupied);
    }

    @Test
    void dropsABoxThatContainsNoBall() {
        BoundingBox empty = new BoundingBox(0, 0, 0, 100, 50, 100);

        List<BoundingBox> occupied = StructurePlacement.occupiedFootprints(List.of(new double[]{500, 500}),
                List.of(empty));

        assertTrue(occupied.isEmpty(), occupied.toString());
    }

    @Test
    void aBallOnTheBoxEdgeCountsAsInside() {
        BoundingBox box = new BoundingBox(0, 0, 0, 100, 50, 100);

        List<BoundingBox> occupied = StructurePlacement.occupiedFootprints(List.of(new double[]{100, 0}),
                List.of(box));

        assertEquals(List.of(box), occupied);
    }
}
