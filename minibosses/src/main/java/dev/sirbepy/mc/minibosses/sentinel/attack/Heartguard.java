package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.BossAttack;
import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.RunningAttack;
import org.bukkit.Sound;

/**
 * The Sentinel opens its chest and the heart glows. A moment later glowing crystals rise around it
 * and, while any of them stands, it takes a quarter of the damage: the players have to pick the
 * crystals over the boss. Breaking them all staggers it and leaves it open for a few seconds; if they
 * outlast the shield (20, 18, 16 seconds) the crystals shatter and nothing is gained.
 *
 * <p>The cast is short and the boss goes back to its normal attacks afterwards; the shield itself
 * lives on in {@link SentinelDefence}, so the pressure is the shield and not a long channel.
 */
public final class Heartguard implements BossAttack {

    public static final String ID = "heartguard";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int cooldownTicks() {
        return SentinelTuning.HEARTGUARD_COOLDOWN_TICKS;
    }

    /** One shield at a time: not while a previous one is still up. */
    @Override
    public boolean canStart(FightContext ctx) {
        return !SentinelDefence.of(ctx).shielded();
    }

    @Override
    public RunningAttack start(FightContext ctx) {
        return Runs.held(ctx, this::begin);
    }

    private RunningAttack begin(FightContext ctx) {
        ctx.animator().play("heartguard", SentinelTuning.HEARTGUARD_CAST_TICKS);
        ctx.effects().sound(ctx.boss().getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.5f);
        return new RunningAttack() {
            private int age;

            @Override
            public boolean tick() {
                // The anchors rise once the chest is open, so a cast cut short leaves nothing behind.
                if (++age == SentinelTuning.HEARTGUARD_OPEN_TICKS) {
                    SentinelDefence.of(ctx).raiseShield();
                }
                return age >= SentinelTuning.HEARTGUARD_CAST_TICKS;
            }

            @Override
            public void cancel() {
                // Anchors already up belong to the shield, not to the cast.
            }
        };
    }
}
