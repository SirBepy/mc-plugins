package dev.sirbepy.mc.market;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DealAlertsTest {

    private static final BigDecimal BASE = new BigDecimal("100.00");
    private static final double SPREAD = 0.2;
    private static final double THRESHOLD = 0.95;

    @Test
    void topOfTheSpreadIsAGreatDeal() {
        // factor 1.2 (the top of [0.8, 1.2]) is percentile 1.0.
        assertTrue(DealAlerts.isGreatDeal(BASE, new BigDecimal("120.00"), SPREAD, THRESHOLD));
    }

    @Test
    void justBelowThresholdIsNotAGreatDeal() {
        // percentile 0.9 (factor 1.16): below the 0.95 threshold.
        assertFalse(DealAlerts.isGreatDeal(BASE, new BigDecimal("116.00"), SPREAD, THRESHOLD));
    }

    @Test
    void exactlyAtThresholdIsAGreatDeal() {
        // percentile 0.95 (factor 1.18).
        assertTrue(DealAlerts.isGreatDeal(BASE, new BigDecimal("118.00"), SPREAD, THRESHOLD));
    }

    @Test
    void baseOrBottomPriceIsNotAGreatDeal() {
        assertFalse(DealAlerts.isGreatDeal(BASE, BASE, SPREAD, THRESHOLD));
        assertFalse(DealAlerts.isGreatDeal(BASE, new BigDecimal("80.00"), SPREAD, THRESHOLD));
    }

    @Test
    void zeroSpreadNeverFires() {
        assertFalse(DealAlerts.isGreatDeal(BASE, BASE, 0.0, THRESHOLD));
    }
}
