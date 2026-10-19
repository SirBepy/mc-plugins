package dev.sirbepy.mc.market;

import java.math.BigDecimal;

/**
 * Whether a rolled price is a "great deal": how close it sits to the top of its spread band, as a
 * 0..1 percentile. {@link Market#factor} rolls uniformly in {@code [1 - spread, 1 + spread]}, so a
 * rolled price's own ratio to its base reconstructs that same factor, within cent rounding.
 */
final class DealAlerts {

    // A percentile exactly on the threshold can land a float epsilon below it (e.g. 0.9499999999999997
    // for a true 0.95), which would wrongly miss a roll meant to qualify.
    private static final double EPSILON = 1e-9;

    private DealAlerts() {
    }

    static boolean isGreatDeal(BigDecimal basePrice, BigDecimal rolledPrice, double spread, double threshold) {
        if (spread <= 0 || basePrice.signum() <= 0) {
            return false;
        }
        double ratio = rolledPrice.doubleValue() / basePrice.doubleValue();
        double percentile = (ratio - (1 - spread)) / (2 * spread);
        return percentile >= threshold - EPSILON;
    }
}
