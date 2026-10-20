package dev.sirbepy.mc.claudebridge.heartbeat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartbeatTrackerTest {

    @Test
    void neverReceivedAHeartbeatIsOffline() {
        AtomicLong clock = new AtomicLong(0L);
        HeartbeatTracker tracker = new HeartbeatTracker(clock::get);

        assertFalse(tracker.isOnline(45));
    }

    @Test
    void withinThresholdIsOnline() {
        AtomicLong clock = new AtomicLong(0L);
        HeartbeatTracker tracker = new HeartbeatTracker(clock::get);

        tracker.recordHeartbeat();
        clock.set(44_000L);

        assertTrue(tracker.isOnline(45));
    }

    @Test
    void pastThresholdIsOffline() {
        AtomicLong clock = new AtomicLong(0L);
        HeartbeatTracker tracker = new HeartbeatTracker(clock::get);

        tracker.recordHeartbeat();
        clock.set(46_000L);

        assertFalse(tracker.isOnline(45));
    }

    @Test
    void exactlyAtThresholdIsStillOnline() {
        AtomicLong clock = new AtomicLong(0L);
        HeartbeatTracker tracker = new HeartbeatTracker(clock::get);

        tracker.recordHeartbeat();
        clock.set(45_000L);

        assertTrue(tracker.isOnline(45));
    }

    @Test
    void aFreshHeartbeatRecoversFromOffline() {
        AtomicLong clock = new AtomicLong(0L);
        HeartbeatTracker tracker = new HeartbeatTracker(clock::get);

        tracker.recordHeartbeat();
        clock.set(100_000L);
        assertFalse(tracker.isOnline(45));

        tracker.recordHeartbeat();
        assertTrue(tracker.isOnline(45));
    }
}
