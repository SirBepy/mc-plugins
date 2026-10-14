package dev.sirbepy.mc.endfight.cult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Pure: when a wave's health threshold is newly crossed, and what that wave spawns. No world
 * access, no randomness - a dragon's current/max health, the fighter count and the top damage
 * dealers are read by the caller ({@code CultWaves}) and handed in as plain values.
 */
public final class WaveMath {

    private WaveMath() {
    }

    /** Where a wave mob comes from (Joe, 2026-10-09). */
    public enum From {
        /** Marches out of the fortress gates (the illager cult). */
        GATES,
        /** Dropped off the dragon: Nether troops fall with slow falling, paratroopers glide. */
        DRAGON,
        /** Spawned over each of the top dragon damage dealers and sent after that player (phantoms). */
        DAMAGERS;

        static From parse(Object raw) {
            if (raw == null) {
                return GATES;
            }
            try {
                return valueOf(raw.toString().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return GATES;
            }
        }
    }

    /**
     * One entry in a wave: {@code base} mobs of {@code type}, plus one more for every
     * {@code perNPlayers} fighters (so "+1 per player" is {@code perNPlayers == 1}). A
     * {@link From#DAMAGERS} entry instead puts {@code base} mobs on each of the top
     * {@code topDamagers} dragon damage dealers.
     */
    public record WaveEntry(String type, int base, int perNPlayers, From from, int topDamagers) {

        public WaveEntry(String type, int base, int perNPlayers) {
            this(type, base, perNPlayers, From.GATES, 0);
        }

        int count(int players) {
            if (perNPlayers <= 0) {
                return base;
            }
            return base + players / perNPlayers;
        }
    }

    /** One wave: fires once per dragon when its health first drops to {@code at} of its max. */
    public record Wave(double at, String announce, List<WaveEntry> entries) {
    }

    /** One mob to spawn; {@code target} is the player a {@link From#DAMAGERS} mob is sent after. */
    public record PlannedSpawn(String type, From from, UUID target) {
    }

    /** {@code spawns} fit under the cap this tick; {@code overflow} is the rest of the same wave,
     * in entry order, for the caller to retry once room frees up rather than lose it for good. */
    public record PlanResult(List<PlannedSpawn> spawns, List<PlannedSpawn> overflow) {
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

    /**
     * What one wave spawns, in entry order: {@code players} fighters scale the gate and dragon
     * entries, and a damagers entry puts its mobs on each of the first {@code topDamagers} of
     * {@code damagers} (already sorted, most damage first). Capped so {@code currentAlive} plus the
     * spawn never exceeds {@code maxAlive}; mobs past the cap are left for the caller to retry
     * (see {@link #planWithOverflow}).
     */
    public static List<PlannedSpawn> plan(List<WaveEntry> entries, int players, List<UUID> damagers,
                                          int currentAlive, int maxAlive) {
        return planWithOverflow(entries, players, damagers, currentAlive, maxAlive).spawns();
    }

    /**
     * Same as {@link #plan}, but also returns the entries that didn't fit under the cap instead of
     * dropping them: a late wave planned while the cult is crowded would otherwise permanently lose
     * mobs that never get a later chance to spawn once earlier ones die off.
     */
    public static PlanResult planWithOverflow(List<WaveEntry> entries, int players, List<UUID> damagers,
                                          int currentAlive, int maxAlive) {
        List<PlannedSpawn> out = new ArrayList<>();
        List<PlannedSpawn> overflow = new ArrayList<>();
        int budget = Math.max(0, maxAlive - currentAlive);
        for (WaveEntry entry : entries) {
            if (entry.from() == From.DAMAGERS) {
                int targets = Math.min(entry.topDamagers(), damagers.size());
                for (int t = 0; t < targets; t++) {
                    for (int i = 0; i < entry.base(); i++) {
                        PlannedSpawn spawn = new PlannedSpawn(entry.type(), From.DAMAGERS, damagers.get(t));
                        (out.size() < budget ? out : overflow).add(spawn);
                    }
                }
            } else {
                int count = entry.count(players);
                for (int i = 0; i < count; i++) {
                    PlannedSpawn spawn = new PlannedSpawn(entry.type(), entry.from(), null);
                    (out.size() < budget ? out : overflow).add(spawn);
                }
            }
        }
        return new PlanResult(out, overflow);
    }

    /** How many queued mobs to spawn per tick so {@code queued} of them come out over about
     * {@code spreadTicks} ticks - never fewer than one a tick. */
    public static int perTick(int queued, int spreadTicks) {
        if (queued <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(queued / (double) Math.max(1, spreadTicks)));
    }
}
