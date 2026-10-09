package dev.sirbepy.mc.endfight.cult;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vex;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/**
 * The cult never hurts or targets its own: the dragon controls it and doesn't care, but the
 * cultists guarding each other's crystals would look broken fighting each other. Covers a direct
 * hit, a shot projectile (its shooter is the one who "did" the hit), and an evoker's vex or fangs
 * (their owner is the evoker that summoned them). Player and dragon damage on a cult mob are left
 * alone - only cult-on-cult is ever cancelled.
 */
public final class CultFriendlyFire implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (CultMarker.isCult(event.getEntity()) && isCultResponsible(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (CultMarker.isCult(event.getEntity()) && CultMarker.isCult(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    /** True if {@code damager} is itself cult, or acts on behalf of a cult mob (a bolt it fired,
     * or a vex/fangs it summoned). */
    static boolean isCultResponsible(Entity damager) {
        if (CultMarker.isCult(damager)) {
            return true;
        }
        LivingEntity responsible = responsibleFor(damager);
        return responsible != null && CultMarker.isCult(responsible);
    }

    private static LivingEntity responsibleFor(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        if (damager instanceof EvokerFangs fangs) {
            return fangs.getOwner();
        }
        if (damager instanceof Vex vex) {
            return vex.getOwner();
        }
        return null;
    }
}
