package dev.sirbepy.mc.dragonballs.scatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetherScaleTest {

    @Test
    void scalesCoordinatesByOneEighth() {
        assertEquals(500.0, NetherScale.toNether(4000), 1e-9);
        assertEquals(-108.5, NetherScale.toNether(-868), 1e-9);
        assertEquals(0.0, NetherScale.toNether(0), 1e-9);
    }

    @Test
    void scalesRadiiByOneEighthAndTruncates() {
        assertEquals(125, NetherScale.toNetherRadius(1000));
        assertEquals(562, NetherScale.toNetherRadius(4500));
        assertEquals(0, NetherScale.toNetherRadius(7));
    }

    @Test
    void stretchesNetherRadiiByTheMultiplier() {
        assertEquals(250, NetherScale.toNetherRadius(1000, 2.0));
        assertEquals(750, NetherScale.toNetherRadius(3000, 2.0));
        assertEquals(1125, NetherScale.toNetherRadius(4500, 2.0));
        assertEquals(125, NetherScale.toNetherRadius(1000, 1.0));
    }

    @Test
    void theDoubledTopTierStillFitsTheNetherPregenSquare() {
        // 2000-4500 doubled is 500-1125; the centre is pulled to +/-75 so 75 + 1125 never passes 1200.
        int max = NetherScale.toNetherRadius(4500, 2.0);
        assertEquals(75.0, NetherScale.clampCenter(900, 1200, max), 1e-9);
        assertEquals(1200.0, NetherScale.clampCenter(900, 1200, max) + max, 1e-9);
    }

    @Test
    void clampsACentreSoTheWholeAnnulusFitsTheSquare() {
        assertEquals(825.0, NetherScale.clampCenter(1050, 1200, 375), 1e-9);
        assertEquals(-825.0, NetherScale.clampCenter(-1100, 1200, 375), 1e-9);
        assertEquals(53.0, NetherScale.clampCenter(53, 1200, 375), 1e-9);
        assertEquals(0.0, NetherScale.clampCenter(400, 300, 562), 1e-9);
    }

    @Test
    void scalesBallSpacingToo() {
        // 200 overworld blocks of spacing would be 1600 in the Nether: 7 balls never fit an annulus
        // only 125-375 wide.
        assertEquals(25, NetherScale.toNetherRadius(200));
    }
}
