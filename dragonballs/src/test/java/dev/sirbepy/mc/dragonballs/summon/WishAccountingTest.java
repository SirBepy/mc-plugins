package dev.sirbepy.mc.dragonballs.summon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WishAccountingTest {

    @Test
    void grantingLessThanAllLeavesTheRestAndDoesNotEndTheSession() {
        WishAccounting.GrantResult result = WishAccounting.grant(3, 1);
        assertTrue(result.accepted());
        assertEquals(2, result.remainingAfter());
        assertFalse(result.sessionEnds());
    }

    @Test
    void grantingExactlyTheLastWishEndsTheSession() {
        WishAccounting.GrantResult result = WishAccounting.grant(1, 1);
        assertTrue(result.accepted());
        assertEquals(0, result.remainingAfter());
        assertTrue(result.sessionEnds());
    }

    @Test
    void overGrantIsRejected() {
        WishAccounting.GrantResult result = WishAccounting.grant(2, 3);
        assertFalse(result.accepted());
        assertEquals(2, result.remainingAfter(), "a rejected grant must not change the count");
        assertFalse(result.sessionEnds());
    }

    @Test
    void zeroOrNegativeCountIsRejected() {
        assertFalse(WishAccounting.grant(3, 0).accepted());
        assertFalse(WishAccounting.grant(3, -1).accepted());
    }
}
