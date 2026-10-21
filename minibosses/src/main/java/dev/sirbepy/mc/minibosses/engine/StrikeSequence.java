package dev.sirbepy.mc.minibosses.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * A {@link RunningAttack} made of {@link TelegraphedStrike}s launched at fixed ticks: a single slam
 * is one step at tick 0, a barrage is several steps spread over the attack. Finishes when the last
 * strike has landed; cancelling stops every strike still warning.
 */
public final class StrikeSequence implements RunningAttack {

    /** Launches one strike {@code atTick} ticks after the sequence starts. */
    public record Step(int atTick, Function<FightContext, TelegraphedStrike> launch) {
    }

    private final FightContext ctx;
    private final List<Step> pending;
    private final List<TelegraphedStrike> active = new ArrayList<>();
    private final Set<UUID> targets;
    private int age;
    private boolean cancelled;

    public StrikeSequence(FightContext ctx, List<Step> steps) {
        this(ctx, steps, Set.of());
    }

    public StrikeSequence(FightContext ctx, List<Step> steps, Set<UUID> targets) {
        this.ctx = ctx;
        this.pending = new ArrayList<>(steps);
        this.targets = Set.copyOf(targets);
        launchDue();
    }

    @Override
    public boolean tick() {
        if (cancelled) {
            return true;
        }
        age++;
        launchDue();
        active.removeIf(TelegraphedStrike::tick);
        return pending.isEmpty() && active.isEmpty();
    }

    private void launchDue() {
        pending.removeIf(step -> {
            if (step.atTick() > age) {
                return false;
            }
            active.add(step.launch().apply(ctx));
            return true;
        });
    }

    @Override
    public void cancel() {
        cancelled = true;
        active.forEach(TelegraphedStrike::cancel);
        active.clear();
        pending.clear();
    }

    @Override
    public Set<UUID> targets() {
        return targets;
    }
}
