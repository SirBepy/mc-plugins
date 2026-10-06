package dev.sirbepy.mc.cartel.call;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Keeps the cousins on the job: they only target who {@link CallManager#allowedTarget} allows, never
 * hurt their boss or each other, can't be hurt by their boss, never turn into drowned, and drop
 * nothing when they die (Joe: their gear never drops). A boss who was offline when a hit landed
 * gets the trophy head on their next join.
 */
public final class CousinListener implements Listener {

    private final CallManager calls;
    private final Cousins cousins;

    public CousinListener(CallManager calls, Cousins cousins) {
        this.calls = calls;
        this.cousins = cousins;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (cousins.isCousin(event.getEntity()) && !calls.allowedTarget(event.getEntity(), event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        LivingEntity attacker = attacker(event.getDamager());
        boolean byCousin = cousins.isCousin(attacker);
        if ((byCousin && (calls.isOwner(victim) || cousins.isCousin(victim)))
                || (cousins.isCousin(victim) && calls.isOwner(attacker))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent event) {
        calls.noteAttack(event.getEntity(), attacker(event.getDamager()));
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (cousins.isCousin(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        calls.onPlayerDeath(event.getEntity(), event.getDamageSource().getCausingEntity());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        calls.deliverPendingHeads(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (cousins.isCousin(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    static LivingEntity attacker(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return damager instanceof LivingEntity living ? living : null;
    }
}
