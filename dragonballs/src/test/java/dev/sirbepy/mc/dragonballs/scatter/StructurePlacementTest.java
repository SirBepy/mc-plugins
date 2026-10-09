package dev.sirbepy.mc.dragonballs.scatter;

import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructurePlacementTest {

    // Joe, 2026-10-09: "underwater structures work too" - a ball and its headroom may sit in water,
    // since ocean ruins and shipwrecks are mostly flooded with few or no air pockets.
    @Test
    void aBallMayRestInWater() {
        assertTrue(StructurePlacement.isOpenForBall(Material.WATER));
    }

    @Test
    void aBallStillNeverRestsInLava() {
        assertFalse(StructurePlacement.isOpenForBall(Material.LAVA), "a nether structure's lava room is never sane");
    }

    @Test
    void plainAirAndCaveAirStayOpen() {
        assertTrue(StructurePlacement.isOpenForBall(Material.AIR));
        assertTrue(StructurePlacement.isOpenForBall(Material.CAVE_AIR));
        assertFalse(StructurePlacement.isOpenForBall(Material.STONE));
    }

    @Test
    void aFloorIsSolidAndNotHazardous() {
        assertTrue(StructurePlacement.isFloorForBall(Material.SAND), "a flooded structure's seabed floor");
        assertFalse(StructurePlacement.isFloorForBall(Material.MAGMA_BLOCK));
        assertFalse(StructurePlacement.isFloorForBall(Material.WATER), "water is never a floor, only ever open space");
    }

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
