package dev.sirbepy.mc.endfight.reset;

import org.bukkit.World;

import java.util.logging.Logger;

/**
 * Where {@code ArenaService}'s first-build hook and {@code /endfight snapshot} both end up: take
 * the snapshot if (and only if) one doesn't already exist, or unconditionally for the admin
 * command. Kept separate from {@link IslandSnapshotTaker} so that "should I capture at all" stays
 * a one-line decision a test can drive without a real world.
 */
public final class IslandResetService {

    private final SnapshotCapture taker;
    private final IslandSnapshotStore store;
    private final Logger logger;

    public IslandResetService(SnapshotCapture taker, IslandSnapshotStore store, Logger logger) {
        this.taker = taker;
        this.store = store;
        this.logger = logger;
    }

    /** {@link dev.sirbepy.mc.endfight.arena.ArenaService}'s first-build hook: only the very first
     * build of this install ever reaches here, and only if nobody's taken a snapshot yet (a
     * restart that already has one must never silently overwrite it). */
    public void onFirstBuild(World end) {
        if (store.exists()) {
            logger.info("First arena build complete, but an island snapshot already exists; leaving it alone.");
            return;
        }
        taker.capture(end);
    }

    /** {@code /endfight snapshot}: always retakes, snapshot or not. */
    public SnapshotStats captureNow(World end) {
        return taker.capture(end);
    }

    public IslandSnapshotStore store() {
        return store;
    }
}
