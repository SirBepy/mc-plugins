package dev.sirbepy.mc.dragonballs.summon.curse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurseMathTest {

    @Test
    void isExpiredIsTrueAtAndAfterTheExpiryInstant() {
        assertFalse(CurseMath.isExpired(1000, 999));
        assertTrue(CurseMath.isExpired(1000, 1000));
        assertTrue(CurseMath.isExpired(1000, 1001));
    }

    @Test
    void remainingMillisNeverGoesNegative() {
        assertEquals(500, CurseMath.remainingMillis(1500, 1000));
        assertEquals(0, CurseMath.remainingMillis(1000, 1500));
    }

    @Test
    void expiryFromHoursAddsTheRightNumberOfMillis() {
        long expiry = CurseMath.expiryFromHours(0, 2);
        assertEquals(2 * 3_600_000L, expiry);
    }

    @Test
    void remainingTicksConvertsMillisAt20TicksPerSecond() {
        // 10 seconds = 10,000ms = 200 ticks.
        assertEquals(200, CurseMath.remainingTicks(10_000, 0));
        assertEquals(0, CurseMath.remainingTicks(1000, 2000));
    }
}
