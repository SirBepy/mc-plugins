package dev.sirbepy.mc.claudebridge.cooldown;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory, never-persisted last-use clock per player (a restart clears everyone's cooldown,
 * which is fine: it's an anti-spam throttle, not a quota). Clock-injectable so the 20-second
 * default is testable without real sleeps, matching Tag's AfkTracker.
 */
public final class CooldownTracker {

    private final LongSupplier clock;
    private final Map<UUID, Long> lastUseMillis = new ConcurrentHashMap<>();

    public CooldownTracker(LongSupplier clock) {
        this.clock = clock;
    }

    /** Seconds still remaining before {@code player} may use the command again; 0 if ready now. */
    public long secondsRemaining(UUID player, long cooldownSeconds) {
        Long last = lastUseMillis.get(player);
        if (last == null) {
            return 0L;
        }
        long remainingMillis = cooldownSeconds * 1000L - (clock.getAsLong() - last);
        if (remainingMillis <= 0) {
            return 0L;
        }
        // Round up: a player mid-second into the wait should still see at least 1s left, not 0.
        return (remainingMillis + 999) / 1000;
    }

    public void recordUse(UUID player) {
        lastUseMillis.put(player, clock.getAsLong());
    }
}
