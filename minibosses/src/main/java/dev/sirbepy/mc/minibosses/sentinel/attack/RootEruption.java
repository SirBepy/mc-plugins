package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.BossAttack;
import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.RunningAttack;
import dev.sirbepy.mc.minibosses.engine.Shape;
import dev.sirbepy.mc.minibosses.engine.StrikeSequence;
import dev.sirbepy.mc.minibosses.engine.Telegraph;
import dev.sirbepy.mc.minibosses.engine.TelegraphedStrike;
import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Roots burst out of the ground. The spots are chosen under the fighters at the moment the warning
 * starts and never follow them, so walking away dodges; each one is warned for the whole window and
 * hurts and slows whoever is still in it. Normal marks up to two fighters, Heroic up to three, and
 * Mythic marks everyone and adds extra spots near them, so there is no standing still in a safe gap.
 */
public final class RootEruption implements BossAttack {

    public static final String ID = "root_eruption";

    private static final Color SPOT_COLOR = Color.fromRGB(125, 85, 40);
    private static final Color LEAF_COLOR = Color.fromRGB(60, 150, 50);
    /** Ticks between rising-particle puffs over each spot while it warns. */
    private static final int RISE_EVERY_TICKS = 4;
    /** Height of the burst column at the moment the roots land, in blocks. */
    private static final double BURST_HEIGHT = 2.6;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public RunningAttack start(FightContext ctx) {
        return Runs.held(ctx, this::begin);
    }

    private RunningAttack begin(FightContext ctx) {
        Tier tier = ctx.tier();
        int warn = SentinelTuning.rootWarnTicks(tier);
        double base = AttackKit.baseDamage(ctx);

        List<Player> fighters = new ArrayList<>(ctx.fighters());
        Collections.shuffle(fighters, ctx.rng());
        List<Player> marked = fighters.subList(0, Math.min(fighters.size(), SentinelTuning.rootTargets(tier)));
        List<Location> spots = new ArrayList<>();
        Set<UUID> targets = new HashSet<>();
        for (Player player : marked) {
            Location at = player.getLocation();
            spots.add(AttackKit.ground(ctx, at.getX(), at.getZ(), at.getY()));
            targets.add(player.getUniqueId());
        }
        if (tier == Tier.MYTHIC && !fighters.isEmpty()) {
            for (int i = 0; i < SentinelTuning.ROOT_EXTRA_SPOTS_MYTHIC; i++) {
                Player near = fighters.get(ctx.rng().nextInt(fighters.size()));
                spots.add(ctx.area().randomGroundPoint(ctx.rng(), near.getLocation(),
                        SentinelTuning.ROOT_EXTRA_SPOT_SPREAD));
            }
        }

        ctx.animator().play("rooted", warn + SentinelTuning.ROOT_RECOVERY_TICKS);
        ctx.effects().sound(ctx.boss().getLocation(), Sound.BLOCK_ROOTED_DIRT_BREAK, 2.0f, 0.5f);

        // One player standing in two overlapping spots is hurt once, not once per spot.
        Set<UUID> alreadyHit = new HashSet<>();
        List<StrikeSequence.Step> steps = new ArrayList<>();
        for (Location spot : spots) {
            steps.add(new StrikeSequence.Step(0, c -> TelegraphedStrike.start(c,
                    Telegraph.of(new Shape.Circle(spot, SentinelTuning.ROOT_RADIUS), warn).withColor(SPOT_COLOR)
                            .withSounds(null, null),
                    hit -> erupt(c, spot, hit, alreadyHit, base))));
        }
        RunningAttack strikes = new StrikeSequence(ctx, steps, targets);
        return Runs.recover(Runs.withTick(strikes, age -> rise(ctx, spots, age, warn)),
                SentinelTuning.ROOT_RECOVERY_TICKS);
    }

    /** Dust and spores drifting up from every spot that has not landed yet. */
    private void rise(FightContext ctx, List<Location> spots, int age, int warn) {
        if (age >= warn || age % RISE_EVERY_TICKS != 0) {
            return;
        }
        double lift = 0.2 + (age % 16) * 0.08;
        for (Location spot : spots) {
            if (!ctx.area().contains(spot)) {
                continue;
            }
            ctx.effects().dust(spot.getWorld(), spot.getX(), spot.getY() + lift, spot.getZ(), SPOT_COLOR, 1.1f);
            ctx.effects().dust(spot.getWorld(), spot.getX() + 0.4, spot.getY() + lift, spot.getZ() - 0.4, LEAF_COLOR, 0.9f);
            ctx.effects().particle(spot.getWorld(), Particle.COMPOSTER, spot.getX(), spot.getY() + 0.3, spot.getZ(), 3,
                    SentinelTuning.ROOT_RADIUS * 0.4);
        }
    }

    private void erupt(FightContext ctx, Location spot, List<Player> hit, Set<UUID> alreadyHit, double base) {
        ctx.effects().sound(spot, Sound.BLOCK_ROOTED_DIRT_BREAK, 2.0f, 0.7f);
        burst(ctx, spot);
        for (Player player : hit) {
            if (!alreadyHit.add(player.getUniqueId())) {
                continue;
            }
            ctx.damage(player, base * SentinelTuning.ROOT_DAMAGE_MULTIPLE);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SentinelTuning.ROOT_SLOW_TICKS,
                    SentinelTuning.ROOT_SLOW_AMPLIFIER));
        }
    }

    /** Roots shooting up: a column of dust with flung dirt at its foot. Particles only, no blocks are placed. */
    private void burst(FightContext ctx, Location spot) {
        if (!ctx.area().contains(spot)) {
            return;
        }
        ctx.effects().blockCrack(spot.getWorld(), Material.ROOTED_DIRT, spot.getX(), spot.getY() + 0.2, spot.getZ(), 24,
                SentinelTuning.ROOT_RADIUS * 0.5);
        for (double h = 0.2; h <= BURST_HEIGHT; h += 0.4) {
            double sway = 0.25 * Math.sin(h * 3);
            ctx.effects().dust(spot.getWorld(), spot.getX() + sway, spot.getY() + h, spot.getZ() - sway, SPOT_COLOR, 1.3f);
            ctx.effects().dust(spot.getWorld(), spot.getX() - sway, spot.getY() + h, spot.getZ() + sway, LEAF_COLOR, 1.0f);
        }
    }
}
