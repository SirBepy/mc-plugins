package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.RunningAttack;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Small {@link RunningAttack} wrappers the Sentinel's attacks are assembled from. */
final class Runs {

    private Runs() {
    }

    /**
     * Holds the body still for the whole of the attack {@code begin} builds, and lets go when it
     * finishes or is cancelled. A {@code begin} that throws lets go too.
     */
    static RunningAttack held(FightContext ctx, Function<FightContext, RunningAttack> begin) {
        BodyHold hold = BodyHold.acquire(ctx);
        RunningAttack inner;
        try {
            inner = begin.apply(ctx);
        } catch (RuntimeException e) {
            hold.release();
            throw e;
        }
        return new RunningAttack() {
            @Override
            public boolean tick() {
                boolean done = inner.tick();
                if (done) {
                    hold.release();
                }
                return done;
            }

            @Override
            public void cancel() {
                inner.cancel();
                hold.release();
            }

            @Override
            public Set<UUID> targets() {
                return inner.targets();
            }
        };
    }

    /** {@code inner}, then {@code ticks} more ticks before the attack counts as finished (the follow-through of a blow). */
    static RunningAttack recover(RunningAttack inner, int ticks) {
        return new RunningAttack() {
            private boolean landed;
            private int left = ticks;

            @Override
            public boolean tick() {
                if (!landed) {
                    landed = inner.tick();
                    return false;
                }
                return --left <= 0;
            }

            @Override
            public void cancel() {
                inner.cancel();
            }

            @Override
            public Set<UUID> targets() {
                return inner.targets();
            }
        };
    }

    /** {@code inner} with {@code beforeTick} called first each tick, given how many ticks have passed (1 on the first). */
    static RunningAttack withTick(RunningAttack inner, IntConsumer beforeTick) {
        return new RunningAttack() {
            private int age;

            @Override
            public boolean tick() {
                beforeTick.accept(++age);
                return inner.tick();
            }

            @Override
            public void cancel() {
                inner.cancel();
            }

            @Override
            public Set<UUID> targets() {
                return inner.targets();
            }
        };
    }

    /** {@code first}, then whatever {@code next} builds once it is done; a null from {@code next} ends the attack there. */
    static RunningAttack then(RunningAttack first, Supplier<RunningAttack> next) {
        return new RunningAttack() {
            private RunningAttack current = first;
            private boolean chained;

            @Override
            public boolean tick() {
                if (!current.tick()) {
                    return false;
                }
                if (chained) {
                    return true;
                }
                RunningAttack following = next.get();
                if (following == null) {
                    return true;
                }
                chained = true;
                current = following;
                return false;
            }

            @Override
            public void cancel() {
                current.cancel();
            }

            @Override
            public Set<UUID> targets() {
                return current.targets();
            }
        };
    }
}
