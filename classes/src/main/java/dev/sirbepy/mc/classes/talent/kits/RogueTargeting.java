package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.talent.Mobs;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Finding "the mob you look at" and landing a teleport safely. {@code World#rayTraceEntities} and
 * {@code LivingEntity#rayTraceBlocks} aren't implemented in MockBukkit (see the kit report's
 * Deviations), so every Rogue ability that needs either uses a manual cone search or a block check
 * instead, the same trick {@code ArtificerGadgets} uses for Grav Gun.
 */
final class RogueTargeting {

    private RogueTargeting() {
    }

    /**
     * The nearest hostile mob roughly in front of {@code player}, within {@code maxDistance}. The
     * cone check is flattened to the horizontal plane: a mob at the player's feet level (the common
     * case) sits well off the player's eye-height look vector at short range, which would otherwise
     * fail a 3D dot-product test that a "mob you look at" ability has no business failing.
     */
    static LivingEntity lookedAtMob(Player player, double maxDistance) {
        Vector dir = player.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-6) return null;
        dir = dir.normalize();
        Location origin = player.getLocation();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity e : player.getNearbyEntities(maxDistance, maxDistance, maxDistance)) {
            if (!(e instanceof LivingEntity living) || !Mobs.hostile(living)) continue;
            Vector toEntity = living.getLocation().toVector().subtract(origin.toVector()).setY(0);
            double distance = toEntity.length();
            if (distance < 1e-6 || distance > maxDistance || toEntity.normalize().dot(dir) < 0.85) continue;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    /** Whether both the foot and head block at {@code at} are open ground to stand on. */
    static boolean standable(Location at) {
        Block feet = at.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        return !feet.getType().isSolid() && !head.getType().isSolid();
    }

    /**
     * A spot {@code distance} blocks behind {@code mob} (opposite its facing), at {@code mob}'s
     * height, or null if nothing standable is there (a teleport never lands in a block).
     */
    static Location behind(LivingEntity mob, double distance) {
        Vector away = mob.getLocation().getDirection().normalize().multiply(-distance);
        Location spot = mob.getLocation().clone().add(away);
        return standable(spot) ? spot : null;
    }

    /** A spot {@code distance} blocks from {@code mob} towards {@code from}, or null if unsafe. */
    static Location adjacentTowards(LivingEntity mob, Location from, double distance) {
        Vector toFrom = from.toVector().subtract(mob.getLocation().toVector());
        if (toFrom.lengthSquared() < 1e-6) toFrom = mob.getLocation().getDirection().multiply(-1);
        Location spot = mob.getLocation().clone().add(toFrom.normalize().multiply(distance));
        return standable(spot) ? spot : null;
    }
}
