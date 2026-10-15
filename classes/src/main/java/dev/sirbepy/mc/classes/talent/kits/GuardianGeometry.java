package dev.sirbepy.mc.classes.talent.kits;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Pure geometry and number math for the Guardian kit, kept out of {@link GuardianListeners} so it
 * can be unit tested without a full event (MockBukkit has no ray tracing, see
 * docs/classes-revamp.md's Guardian section and the task's note on {@code rayTraceBlocks}).
 */
final class GuardianGeometry {

    private GuardianGeometry() {
    }

    /** Whether {@code target} is within {@code maxDistance} blocks of {@code origin} inside a cone
     * {@code halfAngleDegrees} wide on either side of {@code facing} (so a 90 degree cone is 45). */
    static boolean inCone(Location origin, Vector facing, Location target, double maxDistance, double halfAngleDegrees) {
        if (!origin.getWorld().equals(target.getWorld())) return false;
        Vector to = target.toVector().subtract(origin.toVector());
        to.setY(0);
        double distance = to.length();
        if (distance > maxDistance || distance < 1e-6) return false;
        Vector flatFacing = facing.clone().setY(0);
        if (flatFacing.lengthSquared() < 1e-6) return false;
        flatFacing.normalize();
        to.normalize();
        double cos = flatFacing.dot(to);
        return cos >= Math.cos(Math.toRadians(halfAngleDegrees));
    }

    /**
     * A mob's melee hit on {@code victim} counts as blocked when the victim is holding up a shield
     * and the attacker is in front of them (rule from the task: dot product of the victim's facing
     * and the direction to the attacker is positive).
     */
    static boolean isBlocked(Player victim, Location attackerLocation) {
        if (!victim.isBlocking()) return false;
        Vector facing = victim.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 1e-6) return false;
        Vector toAttacker = attackerLocation.toVector().subtract(victim.getLocation().toVector()).setY(0);
        if (toAttacker.lengthSquared() < 1e-6) return true;
        return facing.normalize().dot(toAttacker.normalize()) > 0;
    }

    /** Rooted (standing still while sneaking): velocity near zero on the ground plane. */
    static boolean isStandingStill(Player player) {
        Vector v = player.getVelocity();
        return player.isSneaking() && Math.abs(v.getX()) < 0.01 && Math.abs(v.getZ()) < 0.01;
    }

    /** Hearts to health points; the spec gives every Guardian number in hearts. */
    static double hearts(double hearts) {
        return hearts * 2;
    }

    /** Grit's current damage bonus in percent: stacks times the per-rank step, rank 0 means no node. */
    static double gritPercent(int rank, int stacks) {
        if (rank <= 0 || stacks <= 0) return 0;
        double perStack = rank == 1 ? 2 : 4;
        return perStack * Math.min(stacks, 5);
    }

    /** Fortress's damage-taken multiplier: 60% off mobs, half that (30%, rule 2) off players. */
    static double fortressReduction(boolean fromPlayer) {
        return fromPlayer ? 0.30 : 0.60;
    }
}
