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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * The boss turns to the nearest fighter, holds that heading and sweeps its claw across a cone in
 * front of it. The cone is warned for the whole wind-up; stepping behind the boss or out of the arc
 * avoids it. Whoever is caught is thrown sideways along the swing, and poisoned on Heroic and Mythic.
 */
public final class ThornSweep implements BossAttack {

    public static final String ID = "thorn_sweep";

    private static final Color COLOR = Color.fromRGB(70, 190, 60);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public RunningAttack start(FightContext ctx) {
        return Runs.held(ctx, this::begin);
    }

    /** The sweep without the body hold, so the combo can run it inside its own. */
    RunningAttack begin(FightContext ctx) {
        Tier tier = ctx.tier();
        int warn = SentinelTuning.sweepWarnTicks(tier);
        double base = AttackKit.baseDamage(ctx);
        float yaw = AttackKit.faceNearestFighter(ctx);
        Location origin = AttackKit.feet(ctx);
        origin.setYaw(yaw);
        Shape.Sector cone = new Shape.Sector(origin, yaw, SentinelTuning.SWEEP_HALF_ANGLE_DEGREES,
                SentinelTuning.SWEEP_RADIUS * RigMath.tierSize(tier));
        Vector swing = swingDirection(yaw);

        ctx.animator().play("sweep_windup", warn);
        RunningAttack strike = new StrikeSequence(ctx, List.of(new StrikeSequence.Step(0, c -> TelegraphedStrike.start(c,
                Telegraph.of(cone, warn).withColor(COLOR).withSounds(Sound.ENTITY_RAVAGER_ATTACK,
                        Sound.ENTITY_PLAYER_ATTACK_SWEEP),
                hit -> resolve(c, hit, base, swing)))));
        // Held every tick: nothing else may turn the body while the cone is on the ground.
        return Runs.withTick(Runs.recover(strike, SentinelTuning.SWEEP_RECOVERY_TICKS),
                age -> AttackKit.face(ctx, yaw));
    }

    /** Left claw to right: the boss's right-hand side, which for yaw 0 (facing +Z) is -X. */
    private static Vector swingDirection(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.cos(radians), 0, -Math.sin(radians));
    }

    private void resolve(FightContext ctx, List<Player> hit, double base, Vector swing) {
        ctx.animator().play("sweep_hit", SentinelTuning.SWEEP_RECOVERY_TICKS);
        for (Player player : hit) {
            ctx.damage(player, base * SentinelTuning.SWEEP_DAMAGE_MULTIPLE);
            AttackKit.push(player, swing, SentinelTuning.SWEEP_KNOCKBACK_HORIZONTAL,
                    SentinelTuning.SWEEP_KNOCKBACK_VERTICAL);
            if (ctx.tier() != Tier.NORMAL) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, SentinelTuning.SWEEP_POISON_TICKS, 0));
            }
        }
    }
}
