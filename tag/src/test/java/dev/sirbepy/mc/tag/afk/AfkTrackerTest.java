package dev.sirbepy.mc.tag.afk;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A fake, manually-advanced clock stands in for real `afk.*-seconds` durations, which can run to 600s+. */
class AfkTrackerTest {

    private final AtomicLong now = new AtomicLong(0L);
    private final AfkTracker tracker = new AfkTracker(now::get, 1000L);
    private final UUID player = UUID.randomUUID();

    @Test
    void beforeAnyActivityThePlayerIsAfkWithZeroIdleReportedAsNotAfkOnlyAfterMarking() {
        // Never seen: idleMillis reports 0 (no data), which is below the threshold.
        assertFalse(tracker.isAfk(player));
    }

    @Test
    void stillUnderThresholdIsNotAfk() {
        tracker.markActive(player);
        now.addAndGet(999L);
        assertFalse(tracker.isAfk(player));
    }

    @Test
    void atOrPastThresholdIsAfk() {
        tracker.markActive(player);
        now.addAndGet(1000L);
        assertTrue(tracker.isAfk(player));
    }

    @Test
    void markActiveResetsTheIdleClock() {
        tracker.markActive(player);
        now.addAndGet(1000L);
        assertTrue(tracker.isAfk(player));

        tracker.markActive(player);
        assertFalse(tracker.isAfk(player));
    }

    @Test
    void removeForgetsThePlayerSoIdleMillisFallsBackToZero() {
        tracker.markActive(player);
        now.addAndGet(1000L);
        tracker.remove(player);
        assertFalse(tracker.isAfk(player), "no leftover entry must survive quit");
    }

    @Test
    void setThresholdMillisTakesEffectImmediatelyForExistingActivity() {
        tracker.markActive(player);
        now.addAndGet(500L);
        assertFalse(tracker.isAfk(player));

        tracker.setThresholdMillis(400L);
        assertTrue(tracker.isAfk(player), "a reload that lowers the threshold must apply right away");
    }

    @Test
    void warnOnceIfNotAlreadyFiresOnlyOncePerIdleStretch() {
        assertTrue(tracker.warnOnceIfNotAlready(player), "first call in a stretch must warn");
        assertFalse(tracker.warnOnceIfNotAlready(player), "a repeat in the same stretch must stay silent");

        tracker.markActive(player);
        assertTrue(tracker.warnOnceIfNotAlready(player), "becoming active must reset the warned flag");
    }
}
