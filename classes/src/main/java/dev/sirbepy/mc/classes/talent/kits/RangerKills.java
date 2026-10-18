package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
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
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
            mendThenGiveExp(player, event.getDroppedExp());
            event.setDroppedExp(0);
        }
    }

    /**
     * giveExp() alone skips Mending, which vanilla triggers only on physical orb pickup. Mirrors
     * vanilla's own repair math (ExperienceOrb#repairPlayerItems): pick one random equipped item
     * with Mending that's damaged, repair it at 2 durability per XP point, and only hand the
     * leftover XP to the player's bar.
     */
    private void mendThenGiveExp(Player player, int exp) {
        List<ItemStack> candidates = new ArrayList<>();
        for (ItemStack item : new ItemStack[] {
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand(),
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()
        }) {
            if (item != null && !item.getType().isAir() && item.containsEnchantment(Enchantment.MENDING)
                    && item.getItemMeta() instanceof Damageable damageable && damageable.getDamage() > 0) {
                candidates.add(item);
            }
        }
        if (!candidates.isEmpty()) {
            ItemStack item = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            Damageable meta = (Damageable) item.getItemMeta();
            int damage = meta.getDamage();
            int repaired = Math.min(exp * 2, damage);
            meta.setDamage(damage - repaired);
            item.setItemMeta(meta);
            exp -= (int) Math.ceil(repaired / 2.0);
        }
        if (exp > 0) {
            player.giveExp(exp);
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
