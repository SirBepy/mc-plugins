package dev.sirbepy.mc.endfight.reward;

import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Bukkit wiring for {@link RewardService}: fighter tracking (delegated straight to
 * {@link RewardService#fighters()}, the same shape as guild's {@code DragonSlayers}), the dragon's
 * death (filtered exactly like {@code DragonSlayers.onDeath}: the End only, never an op's
 * {@code /kill}), and suppressing the death animation's XP orbs so {@link RewardService} can sum
 * and hand out the total itself instead of letting players race for drops.
 */
public final class DragonDeathListener implements Listener {

    private final RewardService rewards;

    public DragonDeathListener(RewardService rewards) {
        this.rewards = rewards;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        rewards.fighters().onDamage(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        rewards.fighters().onInteract(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon) || dragon.getWorld().getEnvironment() != World.Environment.THE_END
                || event.getDamageSource().getDamageType() == DamageType.GENERIC_KILL) {
            return;
        }
        rewards.onDragonDeath(dragon);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOrbSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof ExperienceOrb orb && rewards.suppressIfDying(orb)) {
            event.setCancelled(true);
        }
    }
}
