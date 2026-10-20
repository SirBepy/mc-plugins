package dev.sirbepy.mc.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ItemEmojisTest {

    @Test
    void differentProductsGetDifferentEmojis() {
        assertNotEquals(ItemEmojis.of("carrot"), ItemEmojis.of("diamond"));
        assertEquals("💎", ItemEmojis.of("diamond"));
    }

    @Test
    void logsAndSeedsFallBackBySuffix() {
        assertEquals("🪵", ItemEmojis.of("birch_log"));
        assertEquals("🌱", ItemEmojis.of("wheat_seeds"));
    }

    @Test
    void unknownItemsGetTheMoneyBag() {
        assertEquals(ItemEmojis.FALLBACK, ItemEmojis.of("something_new"));
    }
}
