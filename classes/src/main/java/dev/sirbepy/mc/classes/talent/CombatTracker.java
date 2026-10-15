package dev.sirbepy.mc.classes.talent;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Who is fighting whom: mob fights keep the resource bar up and stop its drain, and player-vs-player
 * hits decide who counts as an ally. An ally is any other player nearby who hasn't traded a hit with
 * you in the last {@link #PVP_MILLIS}, so group buffs and heals never help someone you're fighting.
 */
public final class CombatTracker implements Listener {

    static final long PVP_MILLIS = 10_000;

    private final Resources resources;
    private final LongSupplier now;
    /** "a|b" with a < b, to the time of their last hit either way. */
    private final Map<String, Long> pvp = new HashMap<>();

    public CombatTracker(Resources resources, LongSupplier now) {
        this.resources = resources;
        this.now = now;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Entity source = event.getDamager();
        if (source instanceof Projectile p && p.getShooter() instanceof Entity shooter) source = shooter;
        Entity victim = event.getEntity();
        if (source instanceof Player a && victim instanceof Player b && a != b) {
            pvp.put(key(a.getUniqueId(), b.getUniqueId()), now.getAsLong());
        } else if (source instanceof Player a && victim instanceof Mob) {
            resources.tagCombat(a.getUniqueId());
        } else if (source instanceof Mob && victim instanceof Player b) {
            resources.tagCombat(b.getUniqueId());
        }
    }

    public boolean fighting(Player a, Player b) {
        Long at = pvp.get(key(a.getUniqueId(), b.getUniqueId()));
        return at != null && now.getAsLong() - at < PVP_MILLIS;
    }

    /** Other players within {@code radius} of {@code player} who count as allies (see the class doc). */
    public List<Player> allies(Player player, double radius) {
        List<Player> out = new ArrayList<>();
        for (Player other : player.getLocation().getNearbyPlayers(radius)) {
            if (other == player || other.isDead() || other.getGameMode() == GameMode.SPECTATOR) continue;
            if (!fighting(player, other)) out.add(other);
        }
        return out;
    }

    public void forget(UUID player) {
        String id = player.toString();
        pvp.keySet().removeIf(k -> k.startsWith(id) || k.endsWith(id));
    }

    private static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }
}
