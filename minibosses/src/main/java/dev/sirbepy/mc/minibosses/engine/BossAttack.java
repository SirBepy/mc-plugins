package dev.sirbepy.mc.minibosses.engine;

import java.util.function.Function;

/**
 * One attack in a boss's repertoire. {@link #start} builds a {@link RunningAttack} against a fight;
 * the attack itself holds no per-fight state, so one instance can sit in the pools of every phase.
 */
public interface BossAttack {

    /** Stable name, used to avoid repeating the same attack and to key its cooldown. */
    String id();

    /** Ticks after this attack starts before it may be picked again; 0 for none. */
    default int cooldownTicks() {
        return 0;
    }

    RunningAttack start(FightContext ctx);

    static BossAttack of(String id, Function<FightContext, RunningAttack> factory) {
        return of(id, 0, factory);
    }

    static BossAttack of(String id, int cooldownTicks, Function<FightContext, RunningAttack> factory) {
        return new BossAttack() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public int cooldownTicks() {
                return cooldownTicks;
            }

            @Override
            public RunningAttack start(FightContext ctx) {
                return factory.apply(ctx);
            }
        };
    }
}
