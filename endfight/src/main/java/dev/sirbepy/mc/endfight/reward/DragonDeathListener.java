package dev.sirbepy.mc.endfight.reward;

import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Bukkit wiring for {@link RewardService}: fighter tracking (delegated straight to
 * {@link RewardService#fighters()}, the same shape as guild's {@code DragonSlayers}) and the
 * dragon's death (filtered exactly like {@code DragonSlayers.onDeath}: the End only, never an
 * op's {@code /kill}). {@link RewardService#onDragonDeath} does its own orb sweeping on a
 * scheduled task (see {@link OrbSweep}) rather than listening for the orbs' spawn event here.
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
}
