package dev.sirbepy.mc.claudebridge.heartbeat;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Tracks the last time the player-claude daemon (on Joe's PC) pushed a console heartbeat, so
 * {@code /shenron} can tell a player up front whether anyone is actually listening. Updated from
 * the console-command thread (see HeartbeatCommand) and read from the player-command thread, so
 * the single timestamp is an AtomicLong rather than a plain field.
 *
 * <p>Before any heartbeat has ever arrived (fresh plugin boot, possibly after a restart that
 * happened while the daemon itself was down), {@link #isOnline} reports false rather than
 * defaulting to "online" - a just-restarted live server must not tell a player "sent!" when the
 * daemon was actually off the whole time before that restart.
 */
public final class HeartbeatTracker {

    private static final long NEVER = Long.MIN_VALUE;

    private final LongSupplier clock;
    private final AtomicLong lastHeartbeatMillis = new AtomicLong(NEVER);

    public HeartbeatTracker(LongSupplier clock) {
        this.clock = clock;
    }

    public void recordHeartbeat() {
        lastHeartbeatMillis.set(clock.getAsLong());
    }

    public boolean isOnline(long thresholdSeconds) {
        long last = lastHeartbeatMillis.get();
        if (last == NEVER) {
            return false;
        }
        return clock.getAsLong() - last <= thresholdSeconds * 1000L;
    }
}
