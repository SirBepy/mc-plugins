package dev.sirbepy.mc.endfight.tier;

import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Marks {@link TierService#recordKill()} on a real dragon kill, so {@link TierService
 * #firstKillEver()} has its own independent memory even if {@link EndDragonBattleHistory}'s
 * world-API read is ever wrong or unavailable. Filters exactly like {@code reward}'s
 * {@code DragonDeathListener.onDeath} (the End only, never an op's {@code /kill}) - duplicated
 * rather than shared, since {@code reward/} isn't this module's package to depend on.
 */
public final class TierKillTracker implements Listener {

    private final TierService tiers;

    public TierKillTracker(TierService tiers) {
        this.tiers = tiers;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon) || dragon.getWorld().getEnvironment() != World.Environment.THE_END
                || event.getDamageSource().getDamageType() == DamageType.GENERIC_KILL) {
            return;
        }
        tiers.recordKill();
    }
}
