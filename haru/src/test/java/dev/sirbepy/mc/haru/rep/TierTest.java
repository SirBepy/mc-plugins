package dev.sirbepy.mc.haru.rep;

import org.junit.jupiter.api.Test;

import dev.sirbepy.mc.haru.shop.catalog.EverydayWares;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierTest {

    @Test
    void reputationMapsToTheHighestTierReached() {
        assertEquals(Tier.STRANGER, Tier.of(0));
        assertEquals(Tier.STRANGER, Tier.of(99));
        assertEquals(Tier.ACQUAINTANCE, Tier.of(100));
        assertEquals(Tier.KINDRED, Tier.of(2499));
        assertEquals(Tier.SAKURA_BOUND, Tier.of(2500));
        assertEquals(Tier.SAKURA_BOUND, Tier.of(100_000));
    }

    @Test
    void discountsGrowWithTrustButNeverMakeAnythingFree() {
        assertEquals(37, Tier.STRANGER.discounted(37));
        assertEquals(34, Tier.SAKURA_BOUND.discounted(37));
        assertEquals(1, Tier.SAKURA_BOUND.discounted(1));
        assertEquals(0, Tier.SAKURA_BOUND.discounted(0));
    }

    @Test
    void everyEverydayWareStaysWithinTwentyFivePercentOfVanillaAtMaxTrust() {
        // Haru's base prices sit about 15% under vanilla, so the max-trust price must stay >= 0.75/0.85 of it.
        for (var offer : EverydayWares.offers()) {
            int base = offer.emeralds();
            int best = Tier.SAKURA_BOUND.discounted(base);
            assertTrue(best >= base * 0.75 / 0.85 - 1e-9, offer.id() + ": " + best + " of " + base);
        }
    }

    @Test
    void nextStopsAtTheTop() {
        assertEquals(Tier.FRIEND, Tier.ACQUAINTANCE.next());
        assertEquals(Tier.SAKURA_BOUND, Tier.SAKURA_BOUND.next());
        assertTrue(Tier.SAKURA_BOUND.isMax());
    }
}
