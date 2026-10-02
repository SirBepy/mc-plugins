package dev.sirbepy.mc.tag.state;

import dev.sirbepy.mc.tag.logic.PendingAction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything Tag needs to survive a restart. Never trust player PDC as the source of truth here:
 * offline PDC is read-only, so all of this lives in {@code state.yml} instead.
 */
public final class TagState {

    public UUID currentIt;
    public UUID lastTagger;
    public long tagbackExpiryMillis;
    public final Map<UUID, PlayerStats> stats = new HashMap<>();
    public final Set<UUID> optedOut = new HashSet<>();
    public final Map<UUID, PendingAction> pending = new HashMap<>();

    public PlayerStats statsFor(UUID player) {
        return stats.computeIfAbsent(player, ignored -> new PlayerStats());
    }
}
