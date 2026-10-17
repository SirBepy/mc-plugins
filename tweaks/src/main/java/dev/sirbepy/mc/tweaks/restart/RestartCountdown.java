package dev.sirbepy.mc.tweaks.restart;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Pure timing/state machine behind the restart-ASAP countdown: no Bukkit types, driven by a caller
 * calling {@link #tick()} once per second and reacting to the returned events. A {@code /dont-restart}
 * delay only matters at the instant the countdown would reach zero: registering one mid-countdown
 * doesn't pause the numbers, it just blocks the {@code GO} event until every active delay has
 * cleared (by its own 1h timeout or that player's {@link #clearDelay}), at which point the countdown
 * re-arms at a fresh {@link #START_SECONDS} so players get warned again.
 */
public final class RestartCountdown {

    public static final int START_SECONDS = 120;
    private static final int[] WARNING_SECONDS = {120, 60, 30, 10, 5, 1};
    private static final Duration DELAY_DURATION = Duration.ofHours(1);

    public enum EventType {
        /** Broadcast a countdown warning at {@link Event#remainingSeconds()}. */
        WARNING,
        /** No active delays and the countdown hit zero: run save-all flush and log the go marker. */
        GO,
        /** The countdown hit zero but at least one delay is still active; it is holding. */
        DELAYED,
        /** The last delay cleared while holding at zero; the countdown restarted at {@link #START_SECONDS}. */
        REARMED,
    }

    public record Event(EventType type, int remainingSeconds) {
    }

    private final LongSupplier clockMillis;
    private final Map<UUID, Long> delayUntilMillis = new LinkedHashMap<>();

    private boolean running;
    private String token;
    private String reason;
    private int remainingSeconds;
    private boolean waitingOnDelays;

    public RestartCountdown(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isWaitingOnDelays() {
        return waitingOnDelays;
    }

    public String token() {
        return token;
    }

    public String reason() {
        return reason;
    }

    public int remainingSeconds() {
        return remainingSeconds;
    }

    /** Active delays, pruned of anything already past its hour. Iteration order is oldest delay first. */
    public Map<UUID, Long> activeDelays() {
        pruneExpired();
        return new LinkedHashMap<>(delayUntilMillis);
    }

    /** Starts (or restarts, for a new reason) a fresh 120s countdown. Clears any delays from a prior run. */
    public List<Event> start(String token, String reason) {
        this.running = true;
        this.token = token;
        this.reason = reason;
        this.remainingSeconds = START_SECONDS;
        this.waitingOnDelays = false;
        this.delayUntilMillis.clear();
        return List.of(new Event(EventType.WARNING, remainingSeconds));
    }

    public void cancel() {
        running = false;
        waitingOnDelays = false;
        delayUntilMillis.clear();
    }

    /** Result of a {@code /dont-restart} run: whether a countdown was even pending, and whether this player already had an active delay. */
    public record DelayResult(boolean pending, boolean renewed) {
    }

    public DelayResult delay(UUID player) {
        if (!running) {
            return new DelayResult(false, false);
        }
        pruneExpired();
        boolean renewed = delayUntilMillis.containsKey(player);
        delayUntilMillis.put(player, clockMillis.getAsLong() + DELAY_DURATION.toMillis());
        return new DelayResult(true, renewed);
    }

    /** Called when a delaying player disconnects: their delay no longer holds the restart back. */
    public boolean clearDelay(UUID player) {
        return delayUntilMillis.remove(player) != null;
    }

    /** Called once per second by the caller's scheduler. Returns the events to broadcast/act on this tick. */
    public List<Event> tick() {
        if (!running) {
            return List.of();
        }
        pruneExpired();
        List<Event> events = new ArrayList<>();
        if (waitingOnDelays) {
            if (delayUntilMillis.isEmpty()) {
                remainingSeconds = START_SECONDS;
                waitingOnDelays = false;
                events.add(new Event(EventType.REARMED, remainingSeconds));
                events.add(new Event(EventType.WARNING, remainingSeconds));
            }
            return events;
        }
        remainingSeconds--;
        for (int warning : WARNING_SECONDS) {
            if (remainingSeconds == warning) {
                events.add(new Event(EventType.WARNING, warning));
            }
        }
        if (remainingSeconds <= 0) {
            if (delayUntilMillis.isEmpty()) {
                events.add(new Event(EventType.GO, 0));
                running = false;
            } else {
                waitingOnDelays = true;
                events.add(new Event(EventType.DELAYED, 0));
            }
        }
        return events;
    }

    private void pruneExpired() {
        long now = clockMillis.getAsLong();
        delayUntilMillis.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
