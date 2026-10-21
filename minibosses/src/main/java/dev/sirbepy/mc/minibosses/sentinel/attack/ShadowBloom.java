package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.BossAttack;
import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.RunningAttack;
import dev.sirbepy.mc.minibosses.engine.Shape;
import dev.sirbepy.mc.minibosses.engine.StrikeSequence;
import dev.sirbepy.mc.minibosses.engine.Telegraph;
import dev.sirbepy.mc.minibosses.engine.TelegraphedStrike;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mythic only. The Sentinel's heart flares and darkness gathers in a wide circle around it, warned
 * by dark dust and a heartbeat for the whole wind-up. Whoever is still inside when it blooms goes
 * dark (no damage); leaving the circle in time avoids it. Slow to come round again.
 */
public final class ShadowBloom implements BossAttack {

    public static final String ID = "shadow_bloom";

    private static final Color DARK = Color.fromRGB(22, 14, 30);
    private static final Color HEART_GLOW = Color.fromRGB(120, 30, 160);
    /** Ticks between heartbeats while the circle warns. */
    private static final int HEARTBEAT_EVERY_TICKS = 10;
    private static final int INK_EVERY_TICKS = 3;
    private static final int INK_PER_PUFF = 5;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int cooldownTicks() {
        return SentinelTuning.BLOOM_COOLDOWN_TICKS;
    }

    @Override
    public RunningAttack start(FightContext ctx) {
        return Runs.held(ctx, this::begin);
    }

    private RunningAttack begin(FightContext ctx) {
        int warn = SentinelTuning.BLOOM_WARN_TICKS;
        Location centre = AttackKit.feet(ctx);
        ctx.animator().play("heartguard", warn + SentinelTuning.BLOOM_RECOVERY_TICKS);

        RunningAttack strike = new StrikeSequence(ctx, List.of(new StrikeSequence.Step(0, c -> TelegraphedStrike.start(c,
                Telegraph.of(new Shape.Circle(centre, SentinelTuning.BLOOM_RADIUS), warn).withColor(DARK)
                        .withSounds(Sound.ENTITY_WARDEN_HEARTBEAT, Sound.BLOCK_SCULK_CATALYST_BLOOM),
                hit -> bloom(c, hit)))));
        return Runs.recover(Runs.withTick(strike, age -> gather(ctx, centre, age, warn)),
                SentinelTuning.BLOOM_RECOVERY_TICKS);
    }

    /** Heartbeat and drifting ink while the circle warns, and a purple glint at the heart. */
    private void gather(FightContext ctx, Location centre, int age, int warn) {
        if (age >= warn) {
            return;
        }
        if (age % HEARTBEAT_EVERY_TICKS == 0) {
            ctx.effects().sound(centre, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.0f);
        }
        if (age % INK_EVERY_TICKS == 0) {
            // Cosmetic scatter has its own random source so it never shifts the fight's attack picks.
            ThreadLocalRandom scatter = ThreadLocalRandom.current();
            for (int i = 0; i < INK_PER_PUFF; i++) {
                double angle = scatter.nextDouble() * Math.PI * 2;
                double reach = Math.sqrt(scatter.nextDouble()) * SentinelTuning.BLOOM_RADIUS;
                Location at = new Location(centre.getWorld(), centre.getX() + Math.cos(angle) * reach, centre.getY() + 0.4,
                        centre.getZ() + Math.sin(angle) * reach);
                if (ctx.area().contains(at)) {
                    ctx.effects().particle(at.getWorld(), Particle.SQUID_INK, at.getX(), at.getY(), at.getZ(), 1, 0.2);
                }
            }
        }
        AttackKit.body(ctx).ifPresent(body -> {
            Location heart = body.rig().anchor("heart");
            ctx.effects().dust(heart.getWorld(), heart.getX(), heart.getY(), heart.getZ(), HEART_GLOW, 1.4f);
        });
    }

    private void bloom(FightContext ctx, List<Player> hit) {
        for (Player player : hit) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, SentinelTuning.BLOOM_DARKNESS_TICKS, 0));
        }
    }
}
