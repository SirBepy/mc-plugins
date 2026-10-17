package dev.sirbepy.mc.treemod;

import java.util.HashMap;
import java.util.Map;

/**
 * The latest HUD snapshot plus what the overlay needs between snapshots: the server sends whole
 * seconds of cooldown about once a second, so this counts down smoothly from when each arrived, and
 * remembers the first value of every cooldown as its full length for the sweep (the server never
 * sends the length itself, and talents change it per player).
 */
final class HudState {

    private HudData data;
    private long receivedAt;
    /** Each slot's cooldown length in ms, from the first snapshot of that cooldown; gone once it ends. */
    private final Map<String, Long> totals = new HashMap<>();

    void accept(HudData next, long now) {
        data = next;
        receivedAt = now;
        for (HudData.Ability a : next.abilities()) {
            long left = a.cd() * 1000L;
            if (left <= 0) totals.remove(a.slot());
            else totals.merge(a.slot(), left, Math::max);
        }
    }

    /** Null until the server sent one; the overlay draws nothing then (another server, or an old plugin). */
    HudData data() {
        return data;
    }

    void clear() {
        data = null;
        totals.clear();
    }

    long remainingMillis(HudData.Ability a, long now) {
        return Math.max(0, a.cd() * 1000L - (now - receivedAt));
    }

    /** How much of the cooldown is left, 1 = just started, 0 = ready. */
    float sweep(HudData.Ability a, long now) {
        Long total = totals.get(a.slot());
        if (total == null || total <= 0) return 0;
        return Math.min(1f, remainingMillis(a, now) / (float) total);
    }
}
