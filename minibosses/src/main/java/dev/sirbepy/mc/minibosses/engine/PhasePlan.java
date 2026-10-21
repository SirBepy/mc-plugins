package dev.sirbepy.mc.minibosses.engine;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * What a boss does at each stage of its health: ordered phases, each with a weighted pool of
 * attacks and the gap between attacks. A phase starts when health falls to its threshold
 * (default 70% then 35%, so three phases). A tier gets its own plan, which is how Normal, Heroic
 * and Mythic differ in pools, gaps and warning times and not only in health.
 */
public final class PhasePlan {

    public static final List<Double> DEFAULT_THRESHOLDS = List.of(0.70, 0.35);

    /** Pause before the first attack, so players can read the boss and move before it acts. */
    public static final int DEFAULT_OPENING_GAP_TICKS = 20;

    /** An attack with its relative chance of being picked within one phase. */
    public record Entry(BossAttack attack, int weight) {
        public Entry {
            if (weight <= 0) {
                throw new IllegalArgumentException("weight must be positive: " + attack.id());
            }
        }
    }

    /**
     * One stage of the fight. {@code onEnter} runs once when the fight moves into this phase (the
     * roar, the arena changing); it does not run for the opening phase. An empty {@code pool} is a
     * phase with no attacks yet: the boss just walks and melees through it, and its transition
     * hook still fires.
     */
    public record Phase(String name, int gapTicks, List<Entry> pool, Consumer<FightContext> onEnter) {
        public Phase {
            pool = List.copyOf(pool);
        }

        public Phase(String name, int gapTicks, List<Entry> pool) {
            this(name, gapTicks, pool, ctx -> {
            });
        }
    }

    private final List<Double> thresholds;
    private final int openingGapTicks;
    private final List<Phase> phases;

    public PhasePlan(List<Phase> phases) {
        this(DEFAULT_THRESHOLDS, DEFAULT_OPENING_GAP_TICKS, phases);
    }

    /** {@code thresholds} are descending health fractions; there must be one more phase than thresholds. */
    public PhasePlan(List<Double> thresholds, int openingGapTicks, List<Phase> phases) {
        if (phases.size() != thresholds.size() + 1) {
            throw new IllegalArgumentException(thresholds.size() + " thresholds need " + (thresholds.size() + 1)
                    + " phases, got " + phases.size());
        }
        this.thresholds = List.copyOf(thresholds);
        this.openingGapTicks = openingGapTicks;
        this.phases = List.copyOf(phases);
    }

    public int phaseCount() {
        return phases.size();
    }

    public Phase phase(int index) {
        return phases.get(index);
    }

    public int openingGapTicks() {
        return openingGapTicks;
    }

    /** The phase a boss at {@code healthFraction} belongs in: one per threshold it has fallen to or below. */
    public int phaseFor(double healthFraction) {
        int index = 0;
        for (double threshold : thresholds) {
            if (healthFraction <= threshold) {
                index++;
            }
        }
        return index;
    }

    /**
     * Picks the next attack of {@code phaseIndex}: weighted random, never {@code lastId} again unless
     * it is the phase's only attack, and never one {@code onCooldown} reports. Empty when nothing is
     * eligible, in which case the boss simply waits a tick and asks again.
     */
    public Optional<BossAttack> pick(int phaseIndex, Random rng, String lastId, Predicate<BossAttack> onCooldown) {
        List<Entry> pool = phases.get(phaseIndex).pool();
        List<Entry> ready = pool.stream().filter(e -> !onCooldown.test(e.attack())).toList();
        // A lone attack has to repeat; with a real pool, waiting beats repeating the last one.
        List<Entry> candidates = pool.size() == 1 ? ready
                : ready.stream().filter(e -> !e.attack().id().equals(lastId)).toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        int total = candidates.stream().mapToInt(Entry::weight).sum();
        int roll = rng.nextInt(total);
        for (Entry entry : candidates) {
            roll -= entry.weight();
            if (roll < 0) {
                return Optional.of(entry.attack());
            }
        }
        return Optional.of(candidates.get(candidates.size() - 1).attack()); // unreachable: the weights sum to total
    }
}
