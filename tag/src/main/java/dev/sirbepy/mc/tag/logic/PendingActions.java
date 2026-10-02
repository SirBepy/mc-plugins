package dev.sirbepy.mc.tag.logic;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Pure pop-and-consume over a pending-actions map, kept separate from state I/O for testing. */
public final class PendingActions {

    private PendingActions() {
    }

    /** Removes and returns the pending action for {@code player}, if any. */
    public static Optional<PendingAction> take(Map<UUID, PendingAction> pending, UUID player) {
        return Optional.ofNullable(pending.remove(player));
    }
}
