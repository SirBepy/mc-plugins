package dev.sirbepy.mc.tweaks.restart;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestartCountdownTest {

    private static final long HOUR = Duration.ofHours(1).toMillis();
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private long now = 1_000_000L;
    private final RestartCountdown countdown = new RestartCountdown(() -> now);

    @Test
    void noPendingRestartDoesNothing() {
        RestartCountdown.DelayResult result = countdown.delay(ALICE);
        assertFalse(result.pending());
        assertFalse(countdown.isRunning());
    }

    @Test
    void startEmitsThe120sWarningImmediately() {
        List<RestartCountdown.Event> events = countdown.start("tok1", "jar update");
        assertEquals(List.of(new RestartCountdown.Event(RestartCountdown.EventType.WARNING, 120)), events);
        assertTrue(countdown.isRunning());
        assertEquals(120, countdown.remainingSeconds());
    }

    @Test
    void warningsFireAtExactlyTheRightSecondsAndNowhereElse() {
        countdown.start("tok1", "jar update");
        int[] expectedWarnings = {60, 30, 10, 5, 1};
        int nextWarningIndex = 0;
        for (int second = 119; second >= 1; second--) {
            List<RestartCountdown.Event> events = countdown.tick();
            boolean shouldWarn = nextWarningIndex < expectedWarnings.length && second == expectedWarnings[nextWarningIndex];
            if (shouldWarn) {
                assertEquals(List.of(new RestartCountdown.Event(RestartCountdown.EventType.WARNING, second)), events, "at " + second + "s");
                nextWarningIndex++;
            } else {
                assertTrue(events.isEmpty(), "unexpected event at " + second + "s: " + events);
            }
        }
        assertEquals(expectedWarnings.length, nextWarningIndex, "every expected warning fired");
    }

    @Test
    void reachingZeroWithNoDelaysFiresGoExactlyOnce() {
        countdown.start("tok1", "jar update");
        for (int i = 0; i < 119; i++) countdown.tick();
        List<RestartCountdown.Event> last = countdown.tick();
        assertEquals(List.of(new RestartCountdown.Event(RestartCountdown.EventType.GO, 0)), last);
        assertFalse(countdown.isRunning(), "a fired countdown stops, so GO can't repeat on a later tick");
        assertTrue(countdown.tick().isEmpty());
    }

    @Test
    void aSingleActiveDelayBlocksGoAndHoldsAtZero() {
        countdown.start("tok1", "jar update");
        countdown.delay(ALICE);
        for (int i = 0; i < 120; i++) countdown.tick();
        assertTrue(countdown.isWaitingOnDelays());
        assertTrue(countdown.isRunning(), "held, not fired: a delay blocks GO rather than cancelling the countdown");
    }

    @Test
    void delayExpiringAfterAnHourLetsTheHeldRestartReArm() {
        countdown.start("tok1", "jar update");
        countdown.delay(ALICE);
        for (int i = 0; i < 120; i++) countdown.tick();
        assertTrue(countdown.isWaitingOnDelays());

        now += HOUR - 1_000; // not expired yet
        assertTrue(countdown.tick().isEmpty());

        now += 1_000; // now expired
        List<RestartCountdown.Event> events = countdown.tick();
        assertEquals(List.of(
                new RestartCountdown.Event(RestartCountdown.EventType.REARMED, 120),
                new RestartCountdown.Event(RestartCountdown.EventType.WARNING, 120)), events);
        assertFalse(countdown.isWaitingOnDelays());
        assertEquals(120, countdown.remainingSeconds());
    }

    @Test
    void reRunningDontRestartResetsThatPlayersOwnHourAndReportsItWasRenewed() {
        countdown.start("tok1", "jar update");
        RestartCountdown.DelayResult first = countdown.delay(ALICE);
        assertFalse(first.renewed());

        now += HOUR - 1_000;
        RestartCountdown.DelayResult second = countdown.delay(ALICE);
        assertTrue(second.renewed());

        now += HOUR - 1_000; // would have expired under the FIRST delay, but the re-run reset it
        assertTrue(countdown.activeDelays().containsKey(ALICE));
    }

    @Test
    void withTwoPlayersDelayingTheLatestExpiryWins() {
        countdown.start("tok1", "jar update");
        countdown.delay(ALICE);
        long aliceExpiry = now + HOUR;
        now += 30 * 60_000; // 30 min later
        countdown.delay(BOB); // Bob's hour ends 30 min after Alice's
        long bobExpiry = now + HOUR;

        for (int i = 0; i < 120; i++) countdown.tick();
        assertTrue(countdown.isWaitingOnDelays());

        now = aliceExpiry + 1; // Alice's hour elapsed, Bob's hasn't
        assertTrue(countdown.tick().isEmpty(), "still waiting on Bob");
        assertFalse(countdown.activeDelays().containsKey(ALICE));
        assertTrue(countdown.activeDelays().containsKey(BOB));

        now = bobExpiry + 1; // Bob's hour elapses too
        List<RestartCountdown.Event> events = countdown.tick();
        assertEquals(RestartCountdown.EventType.REARMED, events.get(0).type());
    }

    @Test
    void aPlayerLeavingClearsOnlyTheirOwnDelay() {
        countdown.start("tok1", "jar update");
        countdown.delay(ALICE);
        countdown.delay(BOB);
        for (int i = 0; i < 120; i++) countdown.tick();
        assertTrue(countdown.isWaitingOnDelays());

        assertTrue(countdown.clearDelay(ALICE));
        assertTrue(countdown.tick().isEmpty(), "Bob is still delaying it");
        assertFalse(countdown.clearDelay(ALICE), "already cleared");

        assertTrue(countdown.clearDelay(BOB));
        List<RestartCountdown.Event> events = countdown.tick();
        assertEquals(RestartCountdown.EventType.REARMED, events.get(0).type());
    }

    @Test
    void cancelStopsEverythingAndClearsDelays() {
        countdown.start("tok1", "jar update");
        countdown.delay(ALICE);
        countdown.cancel();
        assertFalse(countdown.isRunning());
        assertTrue(countdown.activeDelays().isEmpty());
        assertFalse(countdown.delay(ALICE).pending(), "nothing pending to delay anymore");
    }
}
