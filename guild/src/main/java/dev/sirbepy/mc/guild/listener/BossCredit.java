package dev.sirbepy.mc.guild.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Remembers who hurt each boss, so a boss contract credits everyone who fought it in its last minute,
 * not only the one who landed the final blow (bosses are meant to be done together).
 */
public final class BossCredit implements Listener {

    public static final Set<EntityType> BOSSES = Set.of(EntityType.WITHER, EntityType.ELDER_GUARDIAN,
            EntityType.WARDEN, EntityType.ENDER_DRAGON);
    static final long WINDOW_MILLIS = 60_000L;

    private final LongSupplier now;
    private final Map<UUID, Map<UUID, Long>> hits = new HashMap<>();

    public BossCredit(LongSupplier now) {
        this.now = now;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!BOSSES.contains(event.getEntityType())) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker != null) {
            hits.computeIfAbsent(event.getEntity().getUniqueId(), id -> new HashMap<>())
                    .put(attacker.getUniqueId(), now.getAsLong());
        }
    }

    /** Everyone online who hit {@code boss} in the last minute, plus its killer; forgets the boss. */
    public Set<Player> credited(Entity boss, Player killer) {
        Set<Player> players = new LinkedHashSet<>();
        if (killer != null) {
            players.add(killer);
        }
        Map<UUID, Long> fought = hits.remove(boss.getUniqueId());
        if (fought != null) {
            long cutoff = now.getAsLong() - WINDOW_MILLIS;
            fought.forEach((id, time) -> {
                Player player = Bukkit.getPlayer(id);
                if (player != null && time >= cutoff) {
                    players.add(player);
                }
            });
        }
        return players;
    }

    /** Drops bosses nobody has hit for a minute (despawned, unloaded), so the map can't grow forever. */
    public void sweep() {
        long cutoff = now.getAsLong() - WINDOW_MILLIS;
        hits.values().forEach(m -> m.values().removeIf(time -> time < cutoff));
        hits.values().removeIf(Map::isEmpty);
    }

    static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof Tameable pet && pet.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }
}
