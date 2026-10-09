package dev.sirbepy.mc.endfight.cult;

import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import dev.sirbepy.mc.endfight.arena.GuardPost;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Creature;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Raider;

import java.util.List;

/**
 * Keeps a guard on its post: no wandering goals (removed once, at spawn), and a periodic check
 * that pathfinds a stray guard back or teleports one that's fallen or strayed too far - vanilla
 * fall damage is left alone, so falling off a tower is still fair.
 *
 * <p>Uses Paper's {@code MobGoals}/{@code Pathfinder} API (verified against the real 26.2
 * paper-api jar: {@code VanillaGoal.RANDOM_STROLL} etc. are {@code GoalKey<Creature>}, which a
 * {@code Pillager}/{@code Vindicator}/{@code Evoker}/{@code Ravager} all satisfy since every
 * raider is a {@code Creature}). MockBukkit ships no mock for either interface, so none of this is
 * unit tested - {@link GuardPlan} and {@link WaveMath} carry the tested logic; this class is the
 * thin, runtime-only wiring on top.
 */
final class GuardTether {

    private GuardTether() {
    }

    /** Removes every random-stroll-style vanilla goal so a freshly spawned guard never wanders
     * off on its own; {@link #tickAll} is what actually holds it to its post afterwards. Takes
     * {@link Creature} rather than {@link Mob}: {@code VanillaGoal.RANDOM_STROLL} etc. are
     * {@code GoalKey<Creature>}, and {@code MobGoals.removeGoal}'s single type parameter has to
     * match that exactly - a plain {@code Mob} reference (a wider type than {@code Creature})
     * doesn't satisfy it, even though every guard mob actually spawned here (pillager, vindicator,
     * evoker, ravager) is one.
     *
     * <p>Wrapped in a try/catch: real Paper always has this API (verified against the 26.2
     * paper-api jar), but a test double or a future server fork without it must not take down the
     * whole guard spawn over an AI nicety - the guard still spawns and gets tethered by
     * {@link #tickAll}, it just also keeps vanilla's own wandering goals if this fails. */
    static void removeWanderingGoals(Creature mob) {
        try {
            MobGoals goals = Bukkit.getMobGoals();
            goals.removeGoal(mob, VanillaGoal.RANDOM_STROLL);
            goals.removeGoal(mob, VanillaGoal.WATER_AVOIDING_RANDOM_STROLL);
            goals.removeGoal(mob, VanillaGoal.MOVE_THROUGH_VILLAGE);
            goals.removeGoal(mob, VanillaGoal.STROLL_THROUGH_VILLAGE);
            goals.removeGoal(mob, VanillaGoal.MOVE_BACK_TO_VILLAGE);
            goals.removeGoal(mob, VanillaGoal.MOVE_TOWARDS_RESTRICTION);
            if (mob instanceof Raider raider) {
                goals.removeGoal(raider, VanillaGoal.RAIDER_LONG_DISTANCE_PATROL);
                goals.removeGoal(raider, VanillaGoal.RAIDER_MOVE_THROUGH_VILLAGE);
                goals.removeGoal(raider, VanillaGoal.RAIDER_PATHFIND_TO_RAID);
            }
        } catch (RuntimeException e) {
            // No MobGoals support here; the guard spawn itself still succeeded.
        }
    }

    /** Walks every live cult guard in {@code world} and either lets it be, pathfinds it back
     * towards its post, or teleports it there outright. */
    static void tickAll(World world, List<GuardPost> posts, CultConfig.Guards config) {
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class)) {
            if (!(entity instanceof Mob mob) || !CultMarker.isCult(mob)) {
                continue;
            }
            int postIndex = CultMarker.postIndex(mob);
            if (postIndex < 0 || postIndex >= posts.size()) {
                continue;
            }
            tick(mob, posts.get(postIndex), config);
        }
    }

    private static void tick(Mob mob, GuardPost post, CultConfig.Guards config) {
        if (mob.getTarget() != null) {
            return;
        }
        Location at = mob.getLocation();
        double dx = at.getX() - (post.x() + 0.5);
        double dz = at.getZ() - (post.z() + 0.5);
        double horizontal = Math.hypot(dx, dz);
        double fallen = post.y() - at.getY();
        if (fallen > config.fallTeleportDistance() || horizontal > config.wanderTeleportDistance()) {
            mob.teleport(postLocation(mob.getWorld(), post));
            return;
        }
        if (horizontal > config.tetherRange()) {
            mob.getPathfinder().moveTo(postLocation(mob.getWorld(), post), 1.0);
        }
    }

    private static Location postLocation(World world, GuardPost post) {
        return new Location(world, post.x() + 0.5, post.y(), post.z() + 0.5);
    }
}
