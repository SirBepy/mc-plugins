package dev.sirbepy.mc.core;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZonesTest {

    private static final ZoneId FALLBACK = ZoneId.of("Europe/Zagreb");

    @Test
    void nullFallsBack() {
        assertEquals(FALLBACK, Zones.parse(null, FALLBACK));
    }

    @Test
    void blankFallsBack() {
        assertEquals(FALLBACK, Zones.parse("   ", FALLBACK));
    }

    @Test
    void badIdFallsBack() {
        assertEquals(FALLBACK, Zones.parse("not/a/zone", FALLBACK));
    }

    @Test
    void goodIdParses() {
        assertEquals(ZoneId.of("America/New_York"), Zones.parse("America/New_York", FALLBACK));
    }
}
