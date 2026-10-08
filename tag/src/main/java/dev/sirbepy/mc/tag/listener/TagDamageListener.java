package dev.sirbepy.mc.tag.listener;

import dev.sirbepy.mc.tag.TagService;
import dev.sirbepy.mc.tag.logic.Playability;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Detects a valid tag. Runs at LOWEST with cancelled events included, so it sees every hit
 * before anything else (PvP toggles, region protection) can cancel it, and cancels the damage
 * itself on a valid tag so it works even with PvP off.
 */
public final class TagDamageListener implements Listener {

    private final TagService service;

    public TagDamageListener(TagService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null || !service.isIt(attacker.getUniqueId())) {
            return;
        }
        if (!Playability.playable(attacker) || !Playability.playable(victim)) {
            return;
        }
        TagService.TagResult result = service.attemptTag(attacker, victim);
        if (result == TagService.TagResult.OK) {
            event.setCancelled(true);
        }
        // Any other result (opted out, tagback, self) is not a valid tag: leave the event
        // untouched so normal PvP rules decide whether the damage actually lands.
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        Entity damager = event.getDamager();
        if (damager instanceof Player player
                && (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            return player;
        }
        if (service.config().allowProjectileTags
                && cause == EntityDamageEvent.DamageCause.PROJECTILE
                && damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
