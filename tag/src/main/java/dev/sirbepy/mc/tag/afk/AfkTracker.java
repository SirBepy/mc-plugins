package dev.sirbepy.mc.tag.afk;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory, never-persisted last-activity clock per player. A restart starts empty and every
 * player is marked active again on join (see TagService#onJoin), so a relogging IT is never
 * instantly treated as idle and offline time never counts as idle.
 *
 * Bukkit-free and clock-injectable so idle/threshold/warning behavior is unit testable without a
 * server or real wall-clock sleeps.
 */
public final class AfkTracker {

    private final LongSupplier clock;
    private final Map<UUID, Long> lastActivityMillis = new ConcurrentHashMap<>();
    // Separate from lastActivityMillis: this is "have we already sent the one warning for the
    // current idle stretch", not an activity timestamp.
    private final Set<UUID> warned = ConcurrentHashMap.newKeySet();
    private volatile long thresholdMillis;

    public AfkTracker(LongSupplier clock, long thresholdMillis) {
        this.clock = clock;
        this.thresholdMillis = thresholdMillis;
    }

    /** Re-read on every `/tag reload` so a changed `afk.threshold-seconds` takes effect immediately. */
    public void setThresholdMillis(long thresholdMillis) {
        this.thresholdMillis = thresholdMillis;
    }

    public void markActive(UUID player) {
        lastActivityMillis.put(player, clock.getAsLong());
        warned.remove(player);
    }

    /** Call on quit: no leak, and avoids a stale entry answering for a different play session. */
    public void remove(UUID player) {
        lastActivityMillis.remove(player);
        warned.remove(player);
    }

    public long idleMillis(UUID player) {
        Long last = lastActivityMillis.get(player);
        return last == null ? 0L : Math.max(0L, clock.getAsLong() - last);
    }

    public boolean isAfk(UUID player) {
        return idleMillis(player) >= thresholdMillis;
    }

    /** True the first call since the player's last {@link #markActive}; false every call after. */
    public boolean warnOnceIfNotAlready(UUID player) {
        return warned.add(player);
    }
}
