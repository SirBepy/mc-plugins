package dev.sirbepy.mc.endfight.cult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure: when a wave's health threshold is newly crossed, and how big that wave is. No world
 * access, no randomness - a dragon's current/max health and "already crossed" bookkeeping are read
 * by the caller ({@code CultWaves}) and handed in as plain numbers.
 */
public final class WaveMath {

    private WaveMath() {
    }

    /** One entry in a wave's composition: {@code base} mobs of {@code type}, plus one more for
     * every {@code perNPlayers} players in range (so "+1 per player" is {@code perNPlayers == 1},
     * "+1 per 3 players" is {@code perNPlayers == 3}). */
    public record WaveEntry(String type, int base, int perNPlayers) {

        int count(int players) {
            if (perNPlayers <= 0) {
                return base;
            }
            return base + players / perNPlayers;
        }
    }

    /**
     * Which of {@code thresholds} (fractions of max health, e.g. 0.75/0.5/0.25) this hit newly
     * crossed - the hit's starting fraction was above a threshold and its ending fraction is at or
     * below it - returned as indices into {@code thresholds}, skipping any index already in
     * {@code alreadyCrossed}. A dragon that heals back up and dips under the same threshold again
     * never refires it: the caller never removes an index from {@code alreadyCrossed} once it's
     * been returned, so the threshold stays spent for that dragon's whole fight.
     */
    public static List<Integer> newlyCrossedIndices(double healthBefore, double healthAfter, double maxHealth,
                                                      List<Double> thresholds, Set<Integer> alreadyCrossed) {
        List<Integer> out = new ArrayList<>();
        if (maxHealth <= 0) {
            return out;
        }
        double before = healthBefore / maxHealth;
        double after = healthAfter / maxHealth;
        for (int i = 0; i < thresholds.size(); i++) {
            if (alreadyCrossed.contains(i)) {
                continue;
            }
            double threshold = thresholds.get(i);
            if (before > threshold && after <= threshold) {
                out.add(i);
            }
        }
        return out;
    }

    /** The mob types to spawn for one wave, scaled by {@code players} and capped so
     * {@code currentAlive} plus the spawn never exceeds {@code maxAlive} (extra mobs past the cap
     * are simply dropped, in composition order). */
    public static List<String> spawnList(List<WaveEntry> entries, int players, int currentAlive, int maxAlive) {
        List<String> out = new ArrayList<>();
        int budget = Math.max(0, maxAlive - currentAlive);
        for (WaveEntry entry : entries) {
            int count = entry.count(players);
            for (int i = 0; i < count && out.size() < budget; i++) {
                out.add(entry.type());
            }
            if (out.size() >= budget) {
                break;
            }
        }
        return out;
    }
}
