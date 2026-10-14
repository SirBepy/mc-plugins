package dev.sirbepy.mc.minibosses.sentinel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Per-player cooldown for the Sentinel Seed's summon, in the same "refuse and keep the record"
 * shape as {@code guild.listener.FarmGuard}'s chunk-kill window, just keyed by player instead of
 * chunk and tracking one timestamp instead of a sliding count - a summon is a single rare event
 * to gate, not a rate to smooth over a window.
 */
public final class SummonCooldown {

    private final long windowMillis;
    private final LongSupplier now;
    private final Map<UUID, Long> lastSummon = new HashMap<>();

    public SummonCooldown(long windowMillis, LongSupplier now) {
        this.windowMillis = windowMillis;
        this.now = now;
    }

    /** True and records the attempt when {@code player} is off cooldown; false and leaves the
     * record untouched when still on cooldown, so {@link #remainingMillis} still reads correctly. */
    public boolean tryConsume(UUID player) {
        long time = now.getAsLong();
        Long last = lastSummon.get(player);
        if (last != null && time - last < windowMillis) {
            return false;
        }
        lastSummon.put(player, time);
        return true;
    }

    /** Milliseconds left before {@code player} can summon again; 0 if already off cooldown or never summoned. */
    public long remainingMillis(UUID player) {
        Long last = lastSummon.get(player);
        if (last == null) {
            return 0;
        }
        long elapsed = now.getAsLong() - last;
        return Math.max(0, windowMillis - elapsed);
    }
}
