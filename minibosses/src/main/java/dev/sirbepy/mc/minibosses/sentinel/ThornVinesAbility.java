package dev.sirbepy.mc.minibosses.sentinel;

import dev.sirbepy.mc.minibosses.ability.GuardianAbility;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Overgrown Sentinel's Heroic-tier mechanic: real vine hazards that punish standing still,
 * distinct from Normal's one-shot {@link RootAbility} CC. Fires {@link #CHECKS} times, every
 * {@link #CHECK_INTERVAL_TICKS}, snapshotting each nearby player's location and comparing it to
 * their location at the previous check - anyone who hasn't moved past {@link #MOVEMENT_THRESHOLD}
 * blocks between checks gets bitten by the vines (damage + Poison); anyone who kept moving takes
 * nothing. Registered under the id {@code overgrown_sentinel.yml}'s Heroic tier already names
 * ({@code overgrown_sentinel_thorn_vines}), resolving what Phase A left as a documented no-op.
 */
public final class ThornVinesAbility implements GuardianAbility {

    public static final String ID = "overgrown_sentinel_thorn_vines";

    static final double RADIUS = 6.0; // matches RootAbility's engagement radius
    static final long CHECK_INTERVAL_TICKS = 40L; // 2 seconds
    static final int CHECKS = 5; // 5 * 2s = 10s of hazard uptime
    static final double MOVEMENT_THRESHOLD = 1.5; // blocks between checks to count as "moved"
    static final double DAMAGE = 4.0; // 2 hearts per bite, real but not a one-shot
    static final int POISON_DURATION_TICKS = 60;
    static final int POISON_AMPLIFIER = 0;

    private final JavaPlugin plugin;

    public ThornVinesAbility(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void trigger(LivingEntity boss) {
        Map<UUID, Location> lastSeen = new HashMap<>();
        for (Player player : nearbyPlayers(boss)) {
            lastSeen.put(player.getUniqueId(), player.getLocation());
        }
        new BukkitRunnable() {
            int checksLeft = CHECKS;

            @Override
            public void run() {
                if (checksLeft <= 0 || boss.isDead() || !boss.isValid()) {
                    cancel();
                    return;
                }
                checksLeft--;
                for (Player player : nearbyPlayers(boss)) {
                    Location now = player.getLocation();
                    Location last = lastSeen.put(player.getUniqueId(), now);
                    if (stayedPut(last, now)) {
                        player.damage(DAMAGE);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, POISON_DURATION_TICKS,
                                POISON_AMPLIFIER, false, true, true));
                    }
                }
            }
        }.runTaskTimer(plugin, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    private static boolean stayedPut(Location last, Location now) {
        return last != null && last.getWorld() != null && last.getWorld().equals(now.getWorld())
                && last.distanceSquared(now) < MOVEMENT_THRESHOLD * MOVEMENT_THRESHOLD;
    }

    private static List<Player> nearbyPlayers(LivingEntity boss) {
        List<Player> out = new java.util.ArrayList<>();
        for (var entity : boss.getWorld().getNearbyEntities(boss.getLocation(), RADIUS, RADIUS, RADIUS)) {
            if (entity instanceof Player player) {
                out.add(player);
            }
        }
        return out;
    }
}
