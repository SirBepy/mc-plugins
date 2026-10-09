package dev.sirbepy.mc.endfight.cult;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpawnSpotTest {

    @Test
    void aClearSpotIsKept() {
        assertEquals(60, SpawnSpot.feetY(60, y -> y < 60));
    }

    @Test
    void groundHigherThanThePostLiftsTheMobOntoIt() {
        assertEquals(63, SpawnSpot.feetY(60, y -> y < 63));
    }

    @Test
    void aOneBlockGapIsNotEnoughRoom() {
        // ground up to 61, air at 62, a block at 63, open from 64
        assertEquals(64, SpawnSpot.feetY(60, y -> y <= 61 || y == 63));
    }

    @Test
    void solidAllTheWayUpFallsBackToThePost() {
        assertEquals(60, SpawnSpot.feetY(60, y -> true));
    }
}
