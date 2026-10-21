package dev.sirbepy.mc.minibosses.engine;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The attack loop of one live boss. {@link GuardianRuntime} ticks it once per server tick: it moves
 * the fight through its phases as health falls, runs the current attack to its end, waits the
 * phase's gap, and picks the next. While nobody eligible is inside the area it starts nothing and
 * drops the attack in progress, so a boss never works through an empty arena.
 *
 * <p>The phase reached is kept in the boss's persistent data, so a brain rebuilt after a restart
 * carries on in the same phase and never replays a transition hook.
 */
public final class BossBrain {

    private final FightContext ctx;
    private final PhasePlan plan;
    private final NamespacedKey phaseKey;
    private final Map<String, Long> cooldownUntil = new HashMap<>();

    private RunningAttack current;
    private String currentId;
    private String lastId;
    private int phase;
    private int gap;
    private long ticks;
    private boolean ended;

    public BossBrain(FightContext ctx, PhasePlan plan) {
        this.ctx = ctx;
        this.plan = plan;
        this.phaseKey = EngineKeys.phase(ctx.plugin());
        Integer stored = ctx.boss().getPersistentDataContainer().get(phaseKey, PersistentDataType.INTEGER);
        this.phase = stored == null ? 0 : Math.max(0, Math.min(stored, plan.phaseCount() - 1));
        this.gap = plan.openingGapTicks();
        ctx.phase(phase);
    }

    public FightContext context() {
        return ctx;
    }

    public int phase() {
        return phase;
    }

    public Optional<String> currentAttackId() {
        return Optional.ofNullable(current == null ? null : currentId);
    }

    public boolean ended() {
        return ended;
    }

    /** Advances the fight one tick; false once the boss is dead or gone and the brain has been ended. */
    public boolean tick() {
        if (ended) {
            return false;
        }
        LivingEntity boss = ctx.boss();
        if (boss.isDead() || !boss.isValid()) {
            end();
            return false;
        }
        ticks++;
        ctx.animator().tick();
        ctx.tickTickers();
        advancePhase();
        if (ctx.fighters().isEmpty()) {
            cancelAttack();
            gap = plan.openingGapTicks(); // a returning fighter gets the same breathing room as the opening
            return true;
        }
        if (ctx.consumeStagger()) {
            // A staggered boss drops what it was winding up and starts nothing until it has recovered.
            if (current != null) {
                cancelAttack();
                gap = plan.phase(phase).gapTicks();
            }
            return true;
        }
        if (current != null) {
            if (current.tick()) {
                finishAttack();
                gap = plan.phase(phase).gapTicks();
            }
            return true;
        }
        if (gap > 0) {
            gap--;
            return true;
        }
        startNext();
        return true;
    }

    /**
     * Moves into every phase the boss's health has reached since last tick, in order, firing each
     * one's hook once. A single hit that skips straight past two thresholds fires both hooks.
     */
    private void advancePhase() {
        int target = plan.phaseFor(ctx.healthFraction());
        PersistentDataContainer pdc = ctx.boss().getPersistentDataContainer();
        while (phase < target) {
            phase++;
            // Stored before the hook runs: a hook that throws must not replay on the next tick or boot.
            pdc.set(phaseKey, PersistentDataType.INTEGER, phase);
            ctx.phase(phase);
            plan.phase(phase).onEnter().accept(ctx);
        }
    }

    private void startNext() {
        Optional<BossAttack> next = plan.pick(phase, ctx.rng(), lastId,
                attack -> cooldownUntil.getOrDefault(attack.id(), 0L) > ticks || !attack.canStart(ctx));
        if (next.isEmpty()) {
            return;
        }
        BossAttack attack = next.get();
        current = attack.start(ctx);
        currentId = attack.id();
        lastId = currentId;
        if (attack.cooldownTicks() > 0) {
            cooldownUntil.put(currentId, ticks + attack.cooldownTicks());
        }
    }

    /** Stops the running attack, if any, and removes what it spawned. */
    private void cancelAttack() {
        if (current != null) {
            current.cancel();
        }
        finishAttack();
    }

    private void finishAttack() {
        current = null;
        ctx.entities().clearAttackScoped();
    }

    /** A fighter has left the server: an attack aimed only at them has nobody left to hit. */
    public void playerLeft(UUID player) {
        if (current != null && !current.targets().isEmpty() && current.targets().equals(Set.of(player))) {
            cancelAttack();
            gap = plan.phase(phase).gapTicks();
        }
    }

    /** Ends the fight's loop for good: cancels the attack and removes every temporary entity. */
    public void end() {
        if (ended) {
            return;
        }
        ended = true;
        cancelAttack();
        ctx.endTickers();
        ctx.entities().clear();
    }
}
