package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * What a ranged kill does next: Ricochet (a new arrow at the nearest mob) and Homecoming (the
 * drops and XP come to the shooter instead of landing where the mob died). Split out of
 * {@link RangerArrows}; {@code arrows} is needed only for Ricochet's mob-only tag.
 */
final class RangerKills implements Listener {

    private final KitContext ctx;
    private final RangerArrows arrows;

    RangerKills(KitContext ctx, RangerArrows arrows) {
        this.ctx = ctx;
        this.arrows = arrows;
    }

    void enable() {
        ctx.listen(this);
    }

    // Ricochet: a kill fires a new arrow at the nearest mob within range.

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.getLastDamageCause() instanceof EntityDamageByEntityEvent last)) return;
        if (!(last.getDamager() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player player)) return;
        if (!ctx.active(player)) return;
        int rank = ctx.rank(player, "ricochet");
        if (rank == 0) return;
        double radius = rank == 1 ? 6 : 10;
        LivingEntity target = nearest(dead.getLocation(), radius);
        if (target == null) return;
        Vector dir = target.getLocation().toVector().subtract(dead.getLocation().toVector()).normalize();
        dead.getWorld().spawn(dead.getLocation(), Arrow.class, a -> {
            a.setShooter(player);
            a.setVelocity(dir.multiply(2));
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            a.getPersistentDataContainer().set(arrows.mobOnlyKey(), PersistentDataType.BOOLEAN, true);
        });
        ctx.fx().line(dead.getLocation(), target.getLocation(), Particle.CRIT);
    }

    /**
     * Homecoming: a ranged kill's drops go into the shooter's inventory (overflow at their feet) and
     * its XP straight onto their bar, instead of landing where the mob died. HIGHEST, not MONITOR,
     * because it edits the drop list; other plugins' drop changes at HIGH and below are kept.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHomecoming(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof Player) return;
        if (!(dead.getLastDamageCause() instanceof EntityDamageByEntityEvent last)) return;
        if (!(last.getDamager() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player player)) return;
        if (ctx.rank(player, "homecoming") == 0 || !player.isOnline()) return;
        for (ItemStack drop : event.getDrops()) {
            if (drop == null || drop.getType().isAir()) continue;
            dev.sirbepy.mc.core.Inventories.giveOrDrop(player, drop);
        }
        event.getDrops().clear();
        if (event.getDroppedExp() > 0) {
            player.giveExp(event.getDroppedExp());
            event.setDroppedExp(0);
        }
    }

    private LivingEntity nearest(Location at, double radius) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : at.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof LivingEntity living && Mobs.hostile(living)) {
                double d = living.getLocation().distanceSquared(at);
                if (d < bestDist) {
                    bestDist = d;
                    best = living;
                }
            }
        }
        return best;
    }
}
