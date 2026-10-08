package dev.sirbepy.mc.dragonballs.radar;

import dev.sirbepy.mc.dragonballs.ball.BallSet;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which ball each player has picked to track with each set's radar (sneak + right-click), when
 * they've picked one at all; no entry means "the nearest awake ball", the radar's default.
 * In memory only: a restart, a rejoin or the picked ball going out of reach puts the radar back
 * on the nearest, which costs the holder at most a few clicks.
 */
public final class RadarTracking {

    private final Map<UUID, Map<BallSet, Integer>> picked = new HashMap<>();

    /**
     * The next step of the cycle, given the stars the radar can see right now, nearest first:
     * nearest (no pick) -> the nearest ball pinned -> the next nearest -> ... -> the furthest ->
     * back to nearest. Returns the newly picked star, or {@code null} once back on nearest.
     */
    public Integer cycle(UUID player, BallSet set, List<Integer> starsNearestFirst) {
        Integer current = picked(player, set);
        int index = current == null ? -1 : starsNearestFirst.indexOf(current);
        Integer next = index + 1 < starsNearestFirst.size() ? starsNearestFirst.get(index + 1) : null;
        if (next == null) {
            clear(player, set);
        } else {
            picked.computeIfAbsent(player, id -> new EnumMap<>(BallSet.class)).put(set, next);
        }
        return next;
    }

    public Integer picked(UUID player, BallSet set) {
        Map<BallSet, Integer> bySet = picked.get(player);
        return bySet == null ? null : bySet.get(set);
    }

    public void clear(UUID player, BallSet set) {
        Map<BallSet, Integer> bySet = picked.get(player);
        if (bySet != null) {
            bySet.remove(set);
            if (bySet.isEmpty()) {
                picked.remove(player);
            }
        }
    }

    public void forget(UUID player) {
        picked.remove(player);
    }
}
