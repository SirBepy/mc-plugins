package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.rig.RigBody;
import dev.sirbepy.mc.minibosses.rig.RigMath;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.Optional;

/** Aiming, rig anchors, ground lookups and knockback shared by the Sentinel's attacks. */
final class AttackKit {

    private AttackKit() {
    }

    /** The boss's own attack damage (already scaled for group size at summon), the base every multiple applies to. */
    static double baseDamage(FightContext ctx) {
        AttributeInstance attack = ctx.boss().getAttribute(Attribute.ATTACK_DAMAGE);
        return attack == null ? SentinelTuning.FALLBACK_BASE_DAMAGE : attack.getValue();
    }

    static Optional<Player> nearestFighter(FightContext ctx) {
        Location from = ctx.boss().getLocation();
        return ctx.fighters().stream().min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(from)));
    }

    /** Minecraft yaw (0 faces +Z, 90 faces -X) that looks from {@code from} to {@code to}. */
    static float yawToward(Location from, Location to) {
        return (float) Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
    }

    static void face(FightContext ctx, float yaw) {
        ctx.boss().setRotation(yaw, 0f);
    }

    /** Turns the boss toward the nearest fighter and returns the yaw it now holds; its current yaw if nobody is in the area. */
    static float faceNearestFighter(FightContext ctx) {
        float yaw = nearestFighter(ctx).map(p -> yawToward(ctx.boss().getLocation(), p.getLocation()))
                .orElse(ctx.boss().getLocation().getYaw());
        face(ctx, yaw);
        return yaw;
    }

    static Optional<RigBody> body(FightContext ctx) {
        return ctx.animator() instanceof RigBody body ? Optional.of(body) : Optional.empty();
    }

    /** The boss's feet with its current facing, level. */
    static Location feet(FightContext ctx) {
        Location feet = body(ctx).map(b -> b.rig().feet()).orElseGet(() -> ctx.boss().getLocation());
        feet.setPitch(0);
        return feet;
    }

    /**
     * Where the fist lands in the slam pose, on the ground. The model's own anchor when there is
     * one, so the circle is exactly where the fist is drawn; a fixed reach in front of the boss otherwise.
     */
    static Location slamPoint(FightContext ctx) {
        Location feet = feet(ctx);
        Location fist = body(ctx)
                .map(b -> RigMath.anchor(b.rig().definition(), ctx.tier(), "slam_hit", "slam_fist", feet))
                .orElseGet(() -> inFront(feet, SentinelTuning.SLAM_FALLBACK_REACH * RigMath.tierSize(ctx.tier())));
        return ground(ctx, fist.getX(), fist.getZ(), feet.getY());
    }

    static Location inFront(Location feet, double distance) {
        double yaw = Math.toRadians(feet.getYaw());
        return feet.clone().add(-Math.sin(yaw) * distance, 0, Math.cos(yaw) * distance);
    }

    static Location ground(FightContext ctx, double x, double z, double refY) {
        return new Location(ctx.area().world(), x, ctx.area().surfaceY(x, z, refY), z);
    }

    /** The block under {@code at}, or stone where that chunk is not loaded or the spot is over air: what cracks. */
    static Material groundMaterial(FightContext ctx, Location at) {
        World world = ctx.area().world();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return Material.STONE;
        }
        Material under = world.getBlockAt(x, at.getBlockY() - 1, z).getType();
        return under.isSolid() ? under : Material.STONE;
    }

    /** Sends {@code player} away from {@code from} along the ground; away from {@code fallbackFrom} if standing right on {@code from}. */
    static void knockAway(Player player, Location from, Location fallbackFrom, double horizontal, double vertical) {
        Vector away = player.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (away.lengthSquared() < 1.0e-6) {
            away = player.getLocation().toVector().subtract(fallbackFrom.toVector()).setY(0);
        }
        if (away.lengthSquared() < 1.0e-6) {
            away = new Vector(0, 0, 1);
        }
        push(player, away, horizontal, vertical);
    }

    static void push(Player player, Vector horizontalDirection, double horizontal, double vertical) {
        Vector velocity = horizontalDirection.clone().setY(0).normalize().multiply(horizontal);
        velocity.setY(vertical);
        player.setVelocity(velocity);
    }
}
