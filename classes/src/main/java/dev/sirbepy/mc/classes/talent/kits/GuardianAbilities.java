package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shield Bash, Fortress and the three keystone abilities (Bastion, Oath, Leap Smash). Each is built
 * once in {@link GuardianKit}'s constructor and reused for every cast, per {@link Ability}'s contract.
 */
final class GuardianAbilities {

    private GuardianAbilities() {
    }

    static Ability shieldBash(KitContext ctx, GuardianState state) {
        return SimpleAbility.of("Shield Bash", 40,
                p -> "Knock back mobs in front of you, 1 heart and Slowness II for 3 s",
                player -> castShieldBash(ctx, state, player));
    }

    private static boolean castShieldBash(KitContext ctx, GuardianState state, Player player) {
        int interveneRank = ctx.rank(player, "intervene");
        if (interveneRank > 0) {
            double reach = interveneRank == 1 ? 8 : 12;
            Player ally = lookedAtAlly(player, ctx.allies(player, reach), reach);
            if (ally != null) {
                dashToward(player, ally.getLocation(), reach);
                markFallImmune(ctx, state, player, 100);
                ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0));
                ctx.fx().burst(ally, Particle.HEART, 6, 0.3);
                ctx.fx().sound(ally.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.3f);
                return true;
            }
        }
        Location origin = player.getLocation();
        Vector facing = origin.getDirection();
        int shoulderRank = ctx.rank(player, "shoulder_charge");
        if (shoulderRank > 0) {
            double dashDistance = shoulderRank == 1 ? 4 : 6;
            Vector flat = facing.clone().setY(0);
            if (flat.lengthSquared() > 1e-6) {
                flat.normalize();
                player.setVelocity(player.getVelocity().add(flat.clone().multiply(0.8)));
                markFallImmune(ctx, state, player, 100);
                // The dash lands first, so the cone's origin for target-finding moves forward with it.
                origin = origin.clone().add(flat.multiply(dashDistance));
            }
        }
        int bashRank = ctx.rank(player, "bash_mastery");
        double damage = GuardianGeometry.hearts(bashRank == 0 ? 1 : bashRank == 1 ? 2 : 4);
        int tauntRank = ctx.rank(player, "taunting_shout");
        double tauntRadius = tauntRank == 0 ? 0 : tauntRank == 1 ? 6 : 10;
        for (Entity e : origin.getWorld().getNearbyEntities(origin, 4, 4, 4)) {
            if (!(e instanceof LivingEntity target) || !Mobs.hostile(target)) continue;
            if (!GuardianGeometry.inCone(origin, facing, target.getLocation(), 4, 45)) continue;
            boolean boss = Mobs.boss(target);
            ctx.damage(target, damage, player);
            if (!boss) {
                Vector push = target.getLocation().toVector().subtract(origin.toVector());
                push.setY(0.25);
                if (push.lengthSquared() > 1e-6) target.setVelocity(push.normalize().multiply(1.3));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                int bulldozeRank = ctx.rank(player, "bulldoze");
                if (bulldozeRank > 0) {
                    Location before = target.getLocation().clone();
                    ctx.later(() -> checkBulldoze(ctx, player, target, before, bulldozeRank), 3);
                }
            }
            ctx.fx().burst(target, Particle.CRIT, 10, 0.3);
        }
        if (tauntRadius > 0) {
            for (Entity e : origin.getWorld().getNearbyEntities(origin, tauntRadius, tauntRadius, tauntRadius)) {
                if (e instanceof Mob mob && Mobs.hostile(mob) && !Mobs.boss(mob)) mob.setTarget(player);
            }
        }
        ctx.fx().ring(origin, Particle.SWEEP_ATTACK, 1.5, 8);
        ctx.fx().sound(origin, Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
        return true;
    }

    static Ability fortress(KitContext ctx, GuardianState state) {
        return SimpleAbility.ultimate("Fortress",
                p -> "8 s of 60% less damage from mobs (30% from players), no knockback, every mob within 10 blocks targets you",
                player -> castFortress(ctx, state, player));
    }

    private static boolean castFortress(KitContext ctx, GuardianState state, Player player) {
        UUID id = player.getUniqueId();
        long until = ctx.now() + 8_000L;
        state.fortressUntil.put(id, until);
        if (ctx.rank(player, "sacrifice") > 0) {
            for (Player ally : ctx.allies(player, 6)) state.fortressAllyUntil.put(ally.getUniqueId(), until);
        }
        Location origin = player.getLocation();
        for (Entity e : origin.getWorld().getNearbyEntities(origin, 10, 10, 10)) {
            if (e instanceof Mob mob && Mobs.hostile(mob) && !Mobs.boss(mob)) mob.setTarget(player);
        }
        int trampleRank = ctx.rank(player, "trample");
        if (trampleRank > 0) startTrample(ctx, player, until, trampleRank);
        ctx.fx().ring(origin, Particle.END_ROD, 1.5, 16);
        ctx.fx().sound(origin, Sound.ITEM_SHIELD_BLOCK, 1f, 0.6f);
        return true;
    }

    /** Trample: walking into mobs during Fortress knocks them back. Debounced per mob so standing
     * next to one doesn't hit it every tick. */
    private static void startTrample(KitContext ctx, Player player, long until, int rank) {
        double damage = GuardianGeometry.hearts(rank + 1);
        Map<UUID, Long> lastHit = new HashMap<>();
        BukkitTask[] handle = new BukkitTask[1];
        handle[0] = ctx.repeat(() -> {
            if (!player.isOnline() || ctx.now() > until) {
                handle[0].cancel();
                return;
            }
            Location origin = player.getLocation();
            for (Entity e : origin.getWorld().getNearbyEntities(origin, 1.3, 1.3, 1.3)) {
                if (!(e instanceof LivingEntity mob) || !Mobs.hostile(mob)) continue;
                long last = lastHit.getOrDefault(mob.getUniqueId(), 0L);
                if (ctx.now() - last < 1000) continue;
                lastHit.put(mob.getUniqueId(), ctx.now());
                ctx.damage(mob, damage, player);
                if (!Mobs.boss(mob)) {
                    Vector push = mob.getLocation().toVector().subtract(origin.toVector());
                    push.setY(0.2);
                    if (push.lengthSquared() > 1e-6) mob.setVelocity(push.normalize().multiply(1.1));
                }
            }
        }, 4, 4);
        ctx.later(() -> handle[0].cancel(), 160);
    }

    /** Bulldoze's "knocked into a wall" check: a mob whose knockback barely moved it likely hit one. */
    private static void checkBulldoze(KitContext ctx, Player player, LivingEntity target, Location before, int rank) {
        if (!target.isValid()) return;
        if (target.getLocation().distance(before) < 0.6) {
            ctx.damage(target, GuardianGeometry.hearts(rank), player);
        }
    }

    static Ability bastion(KitContext ctx, GuardianState state) {
        return SimpleAbility.of("Bastion", 45,
                p -> "For 6 s every mob projectile within 4 blocks of you is deflected",
                player -> castBastion(ctx, player));
    }

    private static boolean castBastion(KitContext ctx, Player player) {
        ctx.fx().ring(player.getLocation(), Particle.END_ROD, 4, 20);
        ctx.fx().sound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.5f);
        BukkitTask[] handle = new BukkitTask[1];
        handle[0] = ctx.repeat(() -> deflectNearby(player), 2, 3);
        ctx.later(() -> handle[0].cancel(), 120);
        return true;
    }

    private static void deflectNearby(Player player) {
        if (!player.isOnline()) return;
        for (Entity e : player.getLocation().getWorld().getNearbyEntities(player.getLocation(), 4, 4, 4)) {
            if (e instanceof Projectile proj && proj.getShooter() instanceof Entity shooter && Mobs.hostile(shooter)) {
                proj.setVelocity(proj.getVelocity().multiply(-1));
            }
        }
    }

    static Ability oath(KitContext ctx, GuardianState state) {
        return SimpleAbility.of("Oath", 60,
                p -> "Link to the nearest ally for 10 s: you take half of what mobs deal them, both get Regeneration I",
                player -> castOath(ctx, state, player));
    }

    private static boolean castOath(KitContext ctx, GuardianState state, Player player) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player ally : ctx.allies(player, 20)) {
            double d = ally.getLocation().distanceSquared(player.getLocation());
            if (d < best) {
                best = d;
                nearest = ally;
            }
        }
        if (nearest == null) return false;
        long until = ctx.now() + 10_000L;
        state.oathLink.put(player.getUniqueId(), new GuardianState.OathLink(nearest.getUniqueId(), until));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0));
        nearest.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0));
        ctx.fx().line(player.getLocation(), nearest.getLocation(), Particle.END_ROD);
        ctx.fx().sound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
        return true;
    }

    static Ability leapSmash(KitContext ctx, GuardianState state) {
        return SimpleAbility.of("Leap Smash", 45,
                p -> "Leap at the spot you look at; landing deals 4 hearts to mobs within 3 blocks and knocks them up",
                player -> castLeapSmash(ctx, state, player));
    }

    private static boolean castLeapSmash(KitContext ctx, GuardianState state, Player player) {
        Location target = aimPoint(player, 12);
        Vector to = target.toVector().subtract(player.getLocation().toVector());
        double distance = to.length();
        if (distance < 1e-6) return false;
        Vector velocity = to.normalize().multiply(Math.min(distance * 0.4, 2.2));
        velocity.setY(Math.max(velocity.getY(), 0.6));
        player.setVelocity(velocity);
        markFallImmune(ctx, state, player, 100);
        trackLanding(ctx, player, 60, () -> onLeapSmashLand(ctx, player));
        ctx.fx().line(player.getLocation(), target, Particle.CRIT);
        return true;
    }

    private static void onLeapSmashLand(KitContext ctx, Player player) {
        Location origin = player.getLocation();
        for (Entity e : origin.getWorld().getNearbyEntities(origin, 3, 3, 3)) {
            if (e instanceof LivingEntity target && Mobs.hostile(target)) {
                ctx.damage(target, GuardianGeometry.hearts(4), player);
                if (!Mobs.boss(target)) target.setVelocity(target.getVelocity().clone().setY(0.8));
            }
        }
        ctx.fx().ring(origin, Particle.EXPLOSION, 2, 16);
        ctx.fx().sound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
    }

    // Shared helpers.

    static void markFallImmune(KitContext ctx, GuardianState state, Player player, long ticks) {
        state.fallImmuneUntil.put(player.getUniqueId(), ctx.now() + ticks * 50L);
    }

    private static Location aimPoint(Player player, double maxDistance) {
        Location eye = player.getEyeLocation();
        return eye.clone().add(eye.getDirection().multiply(maxDistance));
    }

    private static Player lookedAtAlly(Player player, List<Player> allies, double maxDistance) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player ally : allies) {
            double distance = ally.getLocation().distance(player.getLocation());
            if (GuardianGeometry.inCone(player.getLocation(), player.getLocation().getDirection(), ally.getLocation(), maxDistance, 20)
                    && distance < bestDistance) {
                best = ally;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void dashToward(Player player, Location target, double maxDistance) {
        Vector to = target.toVector().subtract(player.getLocation().toVector());
        if (to.lengthSquared() < 1e-6) return;
        double distance = Math.min(to.length(), maxDistance);
        player.setVelocity(to.normalize().multiply(Math.min(distance * 0.3, 2.0)));
    }

    private static void trackLanding(KitContext ctx, Player player, long maxTicks, Runnable onLand) {
        long[] elapsed = {0};
        BukkitTask[] handle = new BukkitTask[1];
        handle[0] = ctx.repeat(() -> {
            elapsed[0] += 2;
            if (!player.isOnline()) {
                handle[0].cancel();
                return;
            }
            if (player.isOnGround() || elapsed[0] >= maxTicks) {
                handle[0].cancel();
                onLand.run();
            }
        }, 2, 2);
    }
}
