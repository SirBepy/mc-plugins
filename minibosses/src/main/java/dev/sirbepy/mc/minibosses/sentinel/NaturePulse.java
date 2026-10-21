package dev.sirbepy.mc.minibosses.sentinel;

import dev.sirbepy.mc.minibosses.engine.FightEntities;
import dev.sirbepy.mc.minibosses.relic.RelicActive;
import dev.sirbepy.mc.minibosses.relic.RelicRanks;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The Heart of the Wild's active: a short defensive burst that shoves hostile mobs away and
 * wraps the caster and any player in range in Absorption. It is a panic button on a long
 * cooldown, not sustained healing: Absorption is a one-off buffer and the cooldown is far longer
 * than its duration at every rank. Rank III also leaves the shoved mobs slowed so the caster can
 * get clear.
 */
public final class NaturePulse implements RelicActive {

    public static final String NAME = "Nature Pulse";

    /** Particles and sound for a pulse; a seam so tests can run without a client-visible world. */
    public interface Visuals {
        void play(Player caster, double radius);
    }

    // Indexed by rank - 1.
    private static final int[] RADIUS = {5, 6, 7};
    private static final int[] ABSORPTION_AMPLIFIER = {0, 1, 1};
    private static final int[] ABSORPTION_SECONDS = {6, 8, 10};
    private static final int[] COOLDOWN_SECONDS = {90, 70, 50};
    private static final int RANK_III_SLOW_TICKS = 60;
    private static final int RANK_III_SLOW_AMPLIFIER = 1;
    private static final double KNOCKBACK_STRENGTH = 1.2;
    private static final double KNOCKBACK_LIFT = 0.4;
    // The PDC key every Guardian fight class stamps on its boss (MinibossesPlugin's namespace).
    private static final NamespacedKey GUARDIAN_KEY = new NamespacedKey("minibosses", "guardian");

    private final Visuals visuals;

    public NaturePulse(Visuals visuals) {
        this.visuals = visuals;
    }

    public static int radius(int rank) {
        return RADIUS[index(rank)];
    }

    @Override
    public String guardianId() {
        return SentinelFight.GUARDIAN_ID;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int cooldownSeconds(int rank) {
        return COOLDOWN_SECONDS[index(rank)];
    }

    @Override
    public void fire(Player player, int rank) {
        int i = index(rank);
        double radius = RADIUS[i];
        Location origin = player.getLocation();
        for (Entity entity : player.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (entity.getLocation().distanceSquared(origin) > radius * radius) {
                continue;
            }
            if (entity instanceof Player ally) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ABSORPTION_SECONDS[i] * 20,
                        ABSORPTION_AMPLIFIER[i], false, true, true));
            } else if (entity instanceof Enemy && entity instanceof LivingEntity mob && !isBoss(mob)) {
                shove(mob, origin);
                if (rank >= RelicRanks.MAX_RANK) {
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, RANK_III_SLOW_TICKS,
                            RANK_III_SLOW_AMPLIFIER, false, true, true));
                }
            }
        }
        visuals.play(player, radius);
    }

    /** Guardians, their fight entities and vanilla bosses are never shoved or slowed: a relic
     * earned from a boss must not interrupt that same boss's wind-ups or drag it off its arena. */
    private static boolean isBoss(LivingEntity mob) {
        return mob instanceof Boss
                || mob.getPersistentDataContainer().has(GUARDIAN_KEY, PersistentDataType.STRING)
                || mob.getScoreboardTags().contains(FightEntities.TAG);
    }

    private static void shove(LivingEntity mob, Location origin) {
        Vector away = mob.getLocation().toVector().subtract(origin.toVector()).setY(0);
        // A mob exactly on the caster has no "away"; any direction beats none.
        Vector push = away.lengthSquared() < 1.0E-6 ? new Vector(1, 0, 0) : away.normalize();
        mob.setVelocity(push.multiply(KNOCKBACK_STRENGTH).setY(KNOCKBACK_LIFT));
    }

    private static int index(int rank) {
        return Math.max(RelicRanks.MIN_RANK, Math.min(RelicRanks.MAX_RANK, rank)) - 1;
    }
}
