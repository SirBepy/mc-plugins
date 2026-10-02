package dev.sirbepy.mc.tag.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HelmetDestinationTest {

    @Test
    void prefersInventoryWhenItHasSpace() {
        assertEquals(HelmetDestination.INVENTORY, HelmetDestination.decide(true, true));
        assertEquals(HelmetDestination.INVENTORY, HelmetDestination.decide(true, false));
    }

    @Test
    void fallsBackToEnderChestWhenInventoryIsFull() {
        assertEquals(HelmetDestination.ENDER_CHEST, HelmetDestination.decide(false, true));
    }

    @Test
    void dropsAtFeetWhenBothAreFull() {
        assertEquals(HelmetDestination.DROP, HelmetDestination.decide(false, false));
    }
}
