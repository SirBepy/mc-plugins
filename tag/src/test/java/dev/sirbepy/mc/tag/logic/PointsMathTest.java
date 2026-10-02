package dev.sirbepy.mc.tag.logic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PointsMathTest {

    private static final double DELTA = 1e-9;

    @ParameterizedTest
    @CsvSource({
            "0, 0.0",
            "1, 1.0",
            "2, 1.5",
            "4, 2.0",
            "8, 2.5",
    })
    void logCurveMatchesPlanExamples(int n, double expected) {
        assertEquals(expected, PointsMath.multiplier(n, PointsCurve.LOG, 0.5), DELTA);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0.0",
            "1, 1.0",
            "2, 1.5",
            "4, 2.5",
            "8, 4.5",
    })
    void linearCurveMatchesPlanFormula(int n, double expected) {
        assertEquals(expected, PointsMath.multiplier(n, PointsCurve.LINEAR, 0.5), DELTA);
    }

    @Test
    void onlineItGainsBaseTimesMultiplier() {
        assertEquals(2.0, PointsMath.tickGain(true, 1.0, 0.5, 2.0), DELTA);
    }

    @Test
    void offlineItGainsOfflineFactorTimesBaseTimesMultiplier() {
        assertEquals(1.0, PointsMath.tickGain(false, 1.0, 0.5, 2.0), DELTA);
    }

    @Test
    void zeroOtherPlayersGivesZeroGainRegardlessOfOnlineStatus() {
        double multiplier = PointsMath.multiplier(0, PointsCurve.LOG, 0.5);
        assertEquals(0.0, PointsMath.tickGain(true, 1.0, 0.5, multiplier), DELTA);
        assertEquals(0.0, PointsMath.tickGain(false, 1.0, 0.5, multiplier), DELTA);
    }
}
