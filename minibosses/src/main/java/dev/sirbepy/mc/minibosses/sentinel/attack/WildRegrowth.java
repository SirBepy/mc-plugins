package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.BossAttack;
import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.RunningAttack;
import dev.sirbepy.mc.minibosses.engine.Shape;
import dev.sirbepy.mc.minibosses.sentinel.attack.SentinelAnchors.Kind;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Last phase only. The Sentinel plants itself and green beams link it to healing blossoms; each
 * second it takes back 1% of its maximum health for every blossom still standing, for 8, 10 or 12
 * seconds by tier. It does nothing else while rooted, and the heal is always answerable: destroying
 * every blossom ends the channel on the spot and leaves the boss staggered and open. A channel that
 * runs its course withers the blossoms that are left.
 */
public final class WildRegrowth implements BossAttack {

    public static final String ID = "wild_regrowth";

    private static final Color RING = Color.fromRGB(70, 200, 70);
    /** Spacing of the dust along the ring, in blocks. */
    private static final double RING_SPACING = 0.6;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int cooldownTicks() {
        return SentinelTuning.REGROWTH_COOLDOWN_TICKS;
    }

    /** A boss near full health has nothing to regrow, and it never roots itself over a Heartguard shield. */
    @Override
    public boolean canStart(FightContext ctx) {
        return ctx.healthFraction() <= SentinelTuning.REGROWTH_MAX_START_HEALTH && !SentinelDefence.of(ctx).shielded();
    }

    @Override
    public RunningAttack start(FightContext ctx) {
        return Runs.held(ctx, this::begin);
    }

    private RunningAttack begin(FightContext ctx) {
        int channel = SentinelTuning.regrowthChannelTicks(ctx.tier());
        SentinelAnchors anchors = SentinelAnchors.of(ctx);
        ctx.animator().play("rooted", channel);
        ctx.effects().sound(ctx.boss().getLocation(), Sound.BLOCK_ROOTED_DIRT_PLACE, 2.0f, 0.6f);
        ctx.effects().sound(ctx.boss().getLocation(), Sound.BLOCK_AZALEA_PLACE, 2.0f, 0.8f);
        anchors.spawn(Kind.REGROWTH, SentinelTuning.regrowthAnchors(ctx.tier()));
        return new RunningAttack() {
            private int age;

            @Override
            public boolean tick() {
                age++;
                int live = anchors.live(Kind.REGROWTH);
                if (live == 0) {
                    SentinelDefence.of(ctx).stagger();
                    return true;
                }
                if (age % SentinelTuning.REGROWTH_HEAL_EVERY_TICKS == 0) {
                    heal(ctx, live);
                }
                if (age % SentinelTuning.REGROWTH_RING_EVERY_TICKS == 0) {
                    ring(ctx);
                }
                if (age >= channel) {
                    anchors.shatter(Kind.REGROWTH);
                    return true;
                }
                return false;
            }

            @Override
            public void cancel() {
                anchors.shatter(Kind.REGROWTH);
            }
        };
    }

    private void heal(FightContext ctx, int live) {
        LivingEntity boss = ctx.boss();
        AttributeInstance max = boss.getAttribute(Attribute.MAX_HEALTH);
        if (max == null || boss.isDead() || !boss.isValid()) {
            return;
        }
        ctx.effects().heal(boss, max.getValue() * SentinelTuning.REGROWTH_HEAL_FRACTION * live);
        Location at = boss.getLocation().add(0, 1.0, 0);
        ctx.effects().particle(at.getWorld(), Particle.HAPPY_VILLAGER, at.getX(), at.getY(), at.getZ(), 10, 1.2);
    }

    /** A ring of green dust on the ground around the boss for the whole channel: the read for "it is healing". */
    private void ring(FightContext ctx) {
        Location centre = AttackKit.feet(ctx);
        for (Vector point : new Shape.Circle(centre, SentinelTuning.REGROWTH_RING_RADIUS).outline(RING_SPACING)) {
            Location at = new Location(centre.getWorld(), point.getX(), point.getY() + 0.2, point.getZ());
            if (ctx.area().contains(at)) {
                ctx.effects().dust(at.getWorld(), at.getX(), at.getY(), at.getZ(), RING, 1.0f);
            }
        }
    }
}
