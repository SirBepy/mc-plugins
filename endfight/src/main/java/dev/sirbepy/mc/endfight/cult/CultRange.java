package dev.sirbepy.mc.endfight.cult;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

/** Thin Bukkit-facing helpers: who's close enough to the portal to matter, and how many cult mobs
 * are already alive. Kept out of {@link WaveMath}/{@link GuardPlan} so that math stays pure. */
final class CultRange {

    private CultRange() {
    }

    /** Players in {@code world} within {@code range} blocks of the portal at 0,0, measured flat
     * (horizontal only - matches "within 160 blocks of 0,0" ignoring height). */
    static List<Player> playersInRange(World world, double range) {
        double rangeSq = range * range;
        return world.getPlayers().stream()
                .filter(player -> horizontalDistanceSquared(player.getLocation()) <= rangeSq)
                .toList();
    }

    /** The players actually fighting: in range, alive, and in survival or adventure. Waves scale
     * with how many there are, and only they are chased - never a creative or spectating op. */
    static List<Player> fighters(World world, double range) {
        return playersInRange(world, range).stream().filter(CultRange::isFighting).toList();
    }

    static boolean isFighting(Player player) {
        GameMode mode = player.getGameMode();
        return !player.isDead() && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE);
    }

    static int aliveCultCount(World world) {
        int count = 0;
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class)) {
            if (CultMarker.isCult(entity)) {
                count++;
            }
        }
        return count;
    }

    /** Live wave mobs only: guards have a post, and there is one per post however many that is. */
    static int aliveWaveCount(World world) {
        int count = 0;
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class)) {
            if (CultMarker.isCult(entity) && CultMarker.postIndex(entity) < 0) {
                count++;
            }
        }
        return count;
    }

    private static double horizontalDistanceSquared(Location at) {
        return at.getX() * at.getX() + at.getZ() * at.getZ();
    }
}
