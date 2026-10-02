package dev.sirbepy.mc.tag.head;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Makes the IT head unremovable: no inventory click/drag/creative action may touch it, it cannot
 * be dropped or hand-swapped, it never ends up in death drops, and any stray marked head found
 * anywhere is deleted on sight.
 */
public final class HeadProtectionListener implements Listener {

    private final HeadService heads;

    public HeadProtectionListener(HeadService heads) {
        this.heads = heads;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (heads.isTagHead(event.getCurrentItem()) || heads.isTagHead(event.getCursor())) {
            event.setCancelled(true);
            returnStrandedCursor(event);
            return;
        }
        // Number-key hotbar swap: the armor slot never moves, but the targeted hotbar item could
        // be swapped into it indirectly via the "swap with armor" clientside shortcut.
        if (event.getHotbarButton() >= 0) {
            ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
            if (heads.isTagHead(hotbarItem)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Cancelling a click onto the tag head's slot blocks the slot swap, but Bukkit leaves whatever
     * the player was holding stranded on their cursor: invisible to both inventory and ender chest
     * (a player equipping something onto the head slot the instant they get tagged would otherwise
     * lose track of it, and lose it for good if the window closes or they disconnect first). Routed
     * through {@link HeadService#absorbDisplaced} rather than a plain inventory add so the item is
     * marked and findable by a later {@link HeadService#revoke}, same as a normally-displaced helmet.
     */
    private void returnStrandedCursor(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.isEmpty() || heads.isTagHead(cursor)) {
            return;
        }
        event.getView().setCursor(null);
        if (event.getWhoClicked() instanceof Player player) {
            heads.absorbDisplaced(player, cursor);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (heads.isTagHead(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (heads.isTagHead(event.getCurrentItem()) || heads.isTagHead(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (heads.isTagHead(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (heads.isTagHead(event.getMainHandItem()) || heads.isTagHead(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(heads::isTagHead);
        if (heads.isTagHead(event.getEntity().getInventory().getHelmet())) {
            event.getEntity().getInventory().setHelmet(null);
        }
    }

    @EventHandler
    public void onOpenInventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            heads.purgeStrayHeads(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        heads.purgeStrayHeads(event.getPlayer());
    }
}
