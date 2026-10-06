package dev.sirbepy.mc.dragonballs.scatter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoiAcceptanceTest {

    @Test
    void acceptsAPointInsideTheAnnulusPregenBoundAndSpacing() {
        assertTrue(PoiAcceptance.accepts(1500, 0, 0, 0, 1000, 3000, 4900, List.of(), 200));
    }

    @Test
    void rejectsAPointInsideTheMinRadius() {
        assertFalse(PoiAcceptance.accepts(500, 0, 0, 0, 1000, 3000, 4900, List.of(), 200));
    }

    @Test
    void rejectsAPointOutsideTheMaxRadius() {
        assertFalse(PoiAcceptance.accepts(5000, 0, 0, 0, 1000, 3000, 4900, List.of(), 200));
    }

    @Test
    void rejectsAPointOutsideThePregenBound() {
        assertFalse(PoiAcceptance.accepts(6000, 0, 0, 0, 1000, 3000, 4900, List.of(), 200));
    }

    @Test
    void rejectsAPointTooCloseToAnotherBall() {
        List<double[]> avoid = List.of(new double[]{1500, 50});

        assertFalse(PoiAcceptance.accepts(1500, 0, 0, 0, 1000, 3000, 4900, avoid, 200));
    }
}
