package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.PhasePlan;
import dev.sirbepy.mc.minibosses.engine.PhasePlan.Entry;
import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.List;

/**
 * The Overgrown Sentinel's phase plans, one per tier: three phases (health 100 to 70 to 35 to 0
 * percent) whose pools, gaps and warning times differ by tier, so Heroic and Mythic add patterns
 * and not only health. The attacks hold no per-fight state, so one set of instances serves every plan.
 */
public final class SentinelPlans {

    private static final StonebreakerSlam SLAM = new StonebreakerSlam();
    private static final ThornSweep SWEEP = new ThornSweep();
    private static final RootEruption ROOTS = new RootEruption();
    private static final SlamSweepCombo COMBO = new SlamSweepCombo(SLAM, SWEEP);
    private static final ShadowBloom BLOOM = new ShadowBloom();

    private SentinelPlans() {
    }

    public static PhasePlan forTier(Tier tier) {
        return new PhasePlan(List.of(
                new PhasePlan.Phase("awakened", SentinelTuning.gapTicks(tier, 0), awakened(tier)),
                new PhasePlan.Phase("cracking", SentinelTuning.gapTicks(tier, 1), cracking(tier),
                        SentinelPlans::stagger),
                new PhasePlan.Phase("breaking", SentinelTuning.gapTicks(tier, 2), breaking(tier),
                        SentinelPlans::stagger)));
    }

    /** 100 to 70 percent: the two basic blows, and the Mythic darkness from the start. */
    private static List<Entry> awakened(Tier tier) {
        List<Entry> pool = new ArrayList<>();
        pool.add(new Entry(SLAM, 3));
        pool.add(new Entry(SWEEP, 3));
        if (tier == Tier.MYTHIC) {
            pool.add(new Entry(BLOOM, 2));
        }
        return pool;
    }

    /**
     * 70 to 35 percent: roots join in, and from Heroic up the slam can chain into a sweep.
     * Part 2 plugs Heartguard in here (phase 2 and 3 pools).
     */
    private static List<Entry> cracking(Tier tier) {
        List<Entry> pool = new ArrayList<>();
        pool.add(new Entry(SLAM, 3));
        pool.add(new Entry(SWEEP, 3));
        pool.add(new Entry(ROOTS, 2));
        if (tier != Tier.NORMAL) {
            pool.add(new Entry(COMBO, 2));
        }
        if (tier == Tier.MYTHIC) {
            pool.add(new Entry(BLOOM, 1));
        }
        return pool;
    }

    /**
     * Below 35 percent: the same pool with roots and combos weighted up, and the shorter gaps of
     * {@link SentinelTuning#gapTicks}, so the blows come faster and run together.
     * Part 2 plugs Heartguard and Wild Regrowth in here (Wild Regrowth is phase 3 only).
     */
    private static List<Entry> breaking(Tier tier) {
        List<Entry> pool = new ArrayList<>();
        pool.add(new Entry(SLAM, 3));
        pool.add(new Entry(SWEEP, 3));
        pool.add(new Entry(ROOTS, 4));
        if (tier != Tier.NORMAL) {
            pool.add(new Entry(COMBO, 3));
        }
        if (tier == Tier.MYTHIC) {
            pool.add(new Entry(BLOOM, 2));
        }
        return pool;
    }

    private static void stagger(FightContext ctx) {
        ctx.animator().play("stagger", 20);
        ctx.effects().sound(ctx.boss().getLocation(), Sound.BLOCK_STONE_BREAK, 2.0f, 0.5f);
    }
}
