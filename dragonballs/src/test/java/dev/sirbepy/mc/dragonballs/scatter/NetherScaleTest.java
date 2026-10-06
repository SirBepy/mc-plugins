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
}
