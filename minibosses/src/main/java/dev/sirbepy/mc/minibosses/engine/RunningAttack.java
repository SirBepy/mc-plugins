package dev.sirbepy.mc.minibosses.engine;

import java.util.Set;
import java.util.UUID;

/** One {@link BossAttack} in progress against a fight; the {@link BossBrain} ticks it until it is done. */
public interface RunningAttack {

    /** Advances one server tick; true once the attack has finished. */
    boolean tick();

    /**
     * Stops the attack for good: telegraphs stop without hitting, and anything it spawned outside
     * {@link FightEntities#spawnForAttack} goes too. Called when the fighters all leave, when a
     * targeted player quits and when the fight ends.
     */
    void cancel();

    /**
     * The players this attack is aimed at, or empty when it is aimed at the whole arena. An attack
     * that targets only one player is cancelled when that player quits.
     */
    default Set<UUID> targets() {
        return Set.of();
    }
}
