package dev.sirbepy.mc.safechests.listener;

import dev.sirbepy.mc.safechests.bolt.BoltAccess;
import dev.sirbepy.mc.safechests.grant.GrantManager;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.popcraft.bolt.event.LockBlockEvent;
import org.popcraft.bolt.protection.Protection;

/**
 * Extends a whitelist grant to chests the owner places after the grant was already given. Bolt fires
 * {@link LockBlockEvent} and only creates the protection afterward (bolt-bukkit
 * {@code listeners/BlockListener}: the event is posted, then {@code createProtection}/{@code saveProtection}
 * run only if it was not cancelled), so the protection does not exist yet when this listener runs. One tick
 * later it does, found by re-checking the same block.
 */
public final class NewProtectionListener {

    private final BoltAccess bolt;
    private final GrantManager grants;
    private final JavaPlugin plugin;

    public NewProtectionListener(BoltAccess bolt, GrantManager grants, JavaPlugin plugin) {
        this.bolt = bolt;
        this.grants = grants;
        this.plugin = plugin;
    }

    public void register() {
        bolt.api().registerListener(LockBlockEvent.class, this::onLockBlock);
    }

    private void onLockBlock(LockBlockEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Block block = event.getBlock();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Protection protection = bolt.api().findProtection(block);
            if (protection != null) {
                grants.applyActiveGrantsTo(protection);
            }
        });
    }
}
