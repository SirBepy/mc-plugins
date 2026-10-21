package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.BossAttack;
import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.RunningAttack;

/**
 * A Stonebreaker Slam that, once it has landed, may turn straight into a Thorn Sweep. It is one
 * attack as far as the brain is concerned: the body stays held through both blows and the gap
 * before the next attack starts only after the chain. The chain is a coin flip, so a player cannot
 * count on the sweep either coming or not.
 */
public final class SlamSweepCombo implements BossAttack {

    public static final String ID = "slam_sweep_combo";

    private final StonebreakerSlam slam;
    private final ThornSweep sweep;

    public SlamSweepCombo(StonebreakerSlam slam, ThornSweep sweep) {
        this.slam = slam;
        this.sweep = sweep;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public RunningAttack start(FightContext ctx) {
        return Runs.held(ctx, c -> Runs.then(slam.begin(c),
                () -> c.rng().nextDouble() < SentinelTuning.COMBO_CHAIN_CHANCE ? sweep.begin(c) : null));
    }
}
