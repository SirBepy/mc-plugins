package dev.sirbepy.mc.claudebridge.cooldown;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CooldownTrackerTest {

    @Test
    void neverUsedPlayerHasNoCooldown() {
        CooldownTracker tracker = new CooldownTracker(() -> 0L);
        assertEquals(0L, tracker.secondsRemaining(UUID.randomUUID(), 20));
    }

    @Test
    void immediatelyAfterUseFullCooldownRemains() {
        AtomicLong clock = new AtomicLong(0);
        CooldownTracker tracker = new CooldownTracker(clock::get);
        UUID player = UUID.randomUUID();

        tracker.recordUse(player);

        assertEquals(20L, tracker.secondsRemaining(player, 20));
    }

    @Test
    void cooldownCountsDownAsTimePasses() {
        AtomicLong clock = new AtomicLong(0);
        CooldownTracker tracker = new CooldownTracker(clock::get);
        UUID player = UUID.randomUUID();

        tracker.recordUse(player);
        clock.set(12_000L);

        assertEquals(8L, tracker.secondsRemaining(player, 20));
    }

    @Test
    void cooldownExpiresExactlyAtTheConfiguredSeconds() {
        AtomicLong clock = new AtomicLong(0);
        CooldownTracker tracker = new CooldownTracker(clock::get);
        UUID player = UUID.randomUUID();

        tracker.recordUse(player);
        clock.set(20_000L);

        assertEquals(0L, tracker.secondsRemaining(player, 20));
    }

    @Test
    void partialSecondRemainingRoundsUpSoAPlayerNeverSeesZeroTooEarly() {
        AtomicLong clock = new AtomicLong(0);
        CooldownTracker tracker = new CooldownTracker(clock::get);
        UUID player = UUID.randomUUID();

        tracker.recordUse(player);
        clock.set(19_100L);

        assertEquals(1L, tracker.secondsRemaining(player, 20));
    }

    @Test
    void cooldownIsPerPlayer() {
        AtomicLong clock = new AtomicLong(0);
        CooldownTracker tracker = new CooldownTracker(clock::get);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        tracker.recordUse(first);

        assertEquals(20L, tracker.secondsRemaining(first, 20));
        assertEquals(0L, tracker.secondsRemaining(second, 20));
    }
}
