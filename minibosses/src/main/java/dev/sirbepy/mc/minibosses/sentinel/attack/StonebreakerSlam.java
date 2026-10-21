package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.BossAttack;
import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.RunningAttack;
import dev.sirbepy.mc.minibosses.engine.Shape;
import dev.sirbepy.mc.minibosses.engine.StrikeSequence;
import dev.sirbepy.mc.minibosses.engine.Telegraph;
import dev.sirbepy.mc.minibosses.engine.TelegraphedStrike;
import dev.sirbepy.mc.minibosses.registry.Tier;
import dev.sirbepy.mc.minibosses.rig.RigMath;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The boss stops, raises its fist and brings it down: a circle on the ground in front of it, warned
 * for the whole wind-up, that hurts and throws whoever is on the ground inside it when it lands.
 * Jumping at the moment of impact avoids it, so does leaving the circle. On Mythic a second ring
 * lands {@link SentinelTuning#RING_WARN_TICKS} ticks after the first impact, catching anyone who
 * ran straight out, and every impact leaves thorn patches behind.
 */
public final class StonebreakerSlam implements BossAttack {

    public static final String ID = "stonebreaker_slam";

    private static final Color CIRCLE_COLOR = Color.fromRGB(215, 120, 40);
    private static final Color RING_COLOR = Color.fromRGB(235, 60, 40);
    /** Pieces of ground flung up at the impact, spread over the circle's width. */
    private static final int CRACK_PARTICLES = 40;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public RunningAttack start(FightContext ctx) {
        return Runs.held(ctx, this::begin);
    }

    /** The slam without the body hold, so the combo can run it inside its own. */
    RunningAttack begin(FightContext ctx) {
        Tier tier = ctx.tier();
        boolean mythic = tier == Tier.MYTHIC;
        int warn = SentinelTuning.slamWarnTicks(tier);
        double radius = SentinelTuning.SLAM_RADIUS * RigMath.tierSize(tier);
        double base = AttackKit.baseDamage(ctx);

        AttackKit.faceNearestFighter(ctx);
        Location centre = AttackKit.slamPoint(ctx);
        ctx.animator().play("slam_windup", warn);
        ctx.effects().sound(centre, Sound.BLOCK_STONE_BREAK, 2.0f, 0.5f);

        List<StrikeSequence.Step> steps = new ArrayList<>();
        steps.add(new StrikeSequence.Step(0, c -> TelegraphedStrike.start(c,
                Telegraph.of(new Shape.Circle(centre, radius), warn).withColor(CIRCLE_COLOR).withGrounded(true)
                        .withSounds(null, null),
                hit -> impact(c, centre, radius, base, hit))));
        if (mythic) {
            // One tick after the first impact: the sequence also ticks a strike on the tick it launches it,
            // so this lands the ring exactly RING_WARN_TICKS after the circle did.
            steps.add(new StrikeSequence.Step(warn + 1, c -> {
                c.effects().sound(centre, Sound.BLOCK_STONE_BREAK, 2.0f, 0.5f);
                return TelegraphedStrike.start(c,
                        Telegraph.of(new Shape.Ring(centre, radius, radius + SentinelTuning.RING_WIDTH),
                                SentinelTuning.RING_WARN_TICKS).withColor(RING_COLOR).withGrounded(true)
                                .withSounds(null, null),
                        hit -> ring(c, centre, base, hit));
            }));
        }
        return Runs.recover(new StrikeSequence(ctx, steps), SentinelTuning.SLAM_RECOVERY_TICKS);
    }

    private void impact(FightContext ctx, Location centre, double radius, double base, List<Player> hit) {
        boolean mythic = ctx.tier() == Tier.MYTHIC;
        // The fist stays down through the Mythic ring's warning too, not only the recovery.
        ctx.animator().play("slam_hit",
                SentinelTuning.SLAM_RECOVERY_TICKS + (mythic ? SentinelTuning.RING_WARN_TICKS : 0));
        ctx.effects().sound(centre, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
        ctx.effects().sound(centre, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.5f);
        crack(ctx, centre, radius);
        for (Player player : hit) {
            ctx.damage(player, base * SentinelTuning.SLAM_DAMAGE_MULTIPLE);
            AttackKit.knockAway(player, centre, ctx.boss().getLocation(), SentinelTuning.SLAM_KNOCKBACK_HORIZONTAL,
                    SentinelTuning.SLAM_KNOCKBACK_VERTICAL);
        }
        if (mythic) {
            ThornPatches.of(ctx).leaveAround(centre);
        }
    }

    private void ring(FightContext ctx, Location centre, double base, List<Player> hit) {
        ctx.effects().sound(centre, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        for (Player player : hit) {
            ctx.damage(player, base * SentinelTuning.RING_DAMAGE_MULTIPLE);
            AttackKit.knockAway(player, centre, ctx.boss().getLocation(), SentinelTuning.SLAM_KNOCKBACK_HORIZONTAL,
                    SentinelTuning.SLAM_KNOCKBACK_VERTICAL);
        }
    }

    /** Ground of the impact flung up across the circle; drawn only where the fight area reaches, and no block changes. */
    private void crack(FightContext ctx, Location centre, double radius) {
        if (!ctx.area().contains(centre)) {
            return;
        }
        ctx.effects().blockCrack(centre.getWorld(), AttackKit.groundMaterial(ctx, centre), centre.getX(),
                centre.getY() + 0.2, centre.getZ(), CRACK_PARTICLES, radius * 0.4);
    }
}
