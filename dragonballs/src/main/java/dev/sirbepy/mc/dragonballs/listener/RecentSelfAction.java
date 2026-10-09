package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.ball.BallSet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers, per (set, star), who last did some action to a ball and when, so the same player
 * doing the matching follow-up action within 30 s isn't treated as news. One instance per action
 * (a drop, a place): {@link BallPickupListener} suppresses its own "found" broadcast for a self
 * re-pickup of a drop, {@link BallPlaceBreakListener} does the same for a self re-pickup of a
 * place, and {@link HeldBallCheck} feeds the same drop instance when it silently adopts a stray
 * for its former holder, so that holder picking it back up stays silent too.
 */
final class RecentSelfAction {

    private static final long WINDOW_MILLIS = 30_000;

    private record Key(BallSet set, int star) {
    }

    private final Map<Key, UUID> actor = new HashMap<>();
    private final Map<Key, Long> at = new HashMap<>();

    void record(BallSet set, int star, UUID uuid) {
        Key key = new Key(set, star);
        actor.put(key, uuid);
        at.put(key, System.currentTimeMillis());
    }

    boolean isRecent(BallSet set, int star, UUID uuid) {
        Key key = new Key(set, star);
        UUID who = actor.get(key);
        Long when = at.get(key);
        return who != null && who.equals(uuid) && when != null
                && System.currentTimeMillis() - when < WINDOW_MILLIS;
    }
}
