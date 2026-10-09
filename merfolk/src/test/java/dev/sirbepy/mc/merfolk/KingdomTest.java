package dev.sirbepy.mc.merfolk;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdomTest {

    private static final Instant GRANT = Instant.parse("2026-10-09T14:38:00Z");
    private static final Instant DEADLINE = GRANT.plus(Duration.ofDays(14));

    private long now = GRANT.toEpochMilli();
    private int saves;

    private Kingdom kingdom(Kingdom.State state) {
        return new Kingdom(DEADLINE, state, () -> now, () -> saves++);
    }

    @Test
    void specialistsLastUntilTheDeadlineThenRevertOnce() {
        Kingdom kingdom = kingdom(Kingdom.State.PENDING);
        now = DEADLINE.toEpochMilli() - 1;
        assertFalse(kingdom.expireIfDue());
        assertTrue(kingdom.specialistsAllowed());

        now = DEADLINE.toEpochMilli();
        assertTrue(kingdom.expireIfDue());
        assertFalse(kingdom.specialistsAllowed());
        assertFalse(kingdom.expireIfDue(), "reverting is a one-off");
        assertEquals(1, saves);
    }

    @Test
    void aBuiltKingdomNeverExpires() {
        Kingdom kingdom = kingdom(Kingdom.State.PENDING);
        assertTrue(kingdom.markBuilt());
        now = DEADLINE.plus(Duration.ofDays(30)).toEpochMilli();

        assertFalse(kingdom.expireIfDue());
        assertTrue(kingdom.specialistsAllowed());
        assertEquals(Kingdom.State.BUILT, kingdom.state());
    }

    @Test
    void playerClaudeCanRevertEarly() {
        Kingdom kingdom = kingdom(Kingdom.State.PENDING);
        assertTrue(kingdom.revert());
        assertFalse(kingdom.revert());
        assertFalse(kingdom.specialistsAllowed());
    }

    @Test
    void aMistakeCanBeUndoneBackToPending() {
        Kingdom kingdom = kingdom(Kingdom.State.REVERTED);
        assertTrue(kingdom.reopen());
        assertTrue(kingdom.specialistsAllowed());
        assertFalse(kingdom.reopen());
    }

    @Test
    void statusShowsTheTimeLeft() {
        now = DEADLINE.minus(Duration.ofDays(3).plusHours(5)).toEpochMilli();
        assertTrue(kingdom(Kingdom.State.PENDING).describe().startsWith("Kingdom pending: 3d 5h left"));
    }
}
