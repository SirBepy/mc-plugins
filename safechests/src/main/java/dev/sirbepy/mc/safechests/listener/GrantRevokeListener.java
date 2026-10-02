package dev.sirbepy.mc.safechests.listener;

import dev.sirbepy.mc.safechests.bolt.BoltAccess;
import dev.sirbepy.mc.safechests.grant.GrantManager;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.popcraft.bolt.protection.Protection;

/** Revokes an "Allow once" grant the moment the one open it bought is over, or the player disconnects. */
public final class GrantRevokeListener implements Listener {

    private final BoltAccess bolt;
    private final GrantManager grants;

    public GrantRevokeListener(BoltAccess bolt, GrantManager grants) {
        this.bolt = bolt;
        this.grants = grants;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        HumanEntity closer = event.getPlayer();
        if (!(closer instanceof Player player)) {
            return;
        }
        Protection protection = bolt.protectionFor(event.getInventory());
        if (protection != null) {
            grants.revokeOneTime(protection, player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        grants.revokeAllOneTimeFor(event.getPlayer().getUniqueId());
    }
}
