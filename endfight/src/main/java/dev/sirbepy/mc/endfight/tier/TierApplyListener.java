package dev.sirbepy.mc.endfight.tier;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Locks {@link TierService}'s choice onto a fresh dragon and schedules {@link DkeTierPusher} to
 * push it a few ticks later (giving Dragonkind Evolved's own 1-second tick - see
 * {@link DkeTierPusher}'s class docs - time to start the dragon first). Covers both ways a dragon
 * becomes "fresh": the normal {@code CreatureSpawnEvent} on every respawn, and the one dragon
 * vanilla already spawned when the End world first loaded, before this plugin ever saw a
 * {@code CreatureSpawnEvent} for it - {@link #applyToExistingDragon()}, called once from
 * {@code EndFightPlugin#onEnable}.
 *
 * <p>A PDC flag marks a dragon done the moment either path decides to apply, not after the
 * delayed push actually runs, so a plugin reload mid-delay (or a second enable-time scan) never
 * re-locks/re-pushes a tier onto a dragon that's already mid-fight.
 */
public final class TierApplyListener implements Listener {

    static final NamespacedKey APPLIED_KEY = new NamespacedKey("endfight", "tier-applied");

    private final Plugin plugin;
    private final TierService tiers;
    private final DkeTierPusher pusher;
    private final long applyDelayTicks;

    public TierApplyListener(Plugin plugin, TierService tiers, DkeTierPusher pusher, long applyDelayTicks) {
        this.plugin = plugin;
        this.tiers = tiers;
        this.pusher = pusher;
        this.applyDelayTicks = applyDelayTicks;
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.ENDER_DRAGON
                || event.getEntity().getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        tryApply((EnderDragon) event.getEntity());
    }

    /** Scans every End world for a dragon nobody has tagged yet - the live End's very first
     * dragon, which vanilla spawns on world load rather than through any event this plugin can
     * listen for. */
    public void applyToExistingDragon() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.THE_END) {
                continue;
            }
            for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
                tryApply(dragon);
            }
        }
    }

    private void tryApply(EnderDragon dragon) {
        if (dragon.getPersistentDataContainer().has(APPLIED_KEY, PersistentDataType.BOOLEAN)) {
            return;
        }
        dragon.getPersistentDataContainer().set(APPLIED_KEY, PersistentDataType.BOOLEAN, true);
        Tier tier = tiers.lockForSpawn();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> pusher.apply(dragon, tier), applyDelayTicks);
    }
}
