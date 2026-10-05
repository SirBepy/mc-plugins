package dev.sirbepy.mc.tag.tracker;

import dev.sirbepy.mc.tag.TagService;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Right-click cycles IT's tracker, and the tracker never leaves IT's own inventory: it can be
 * rearranged there (hotbar, storage, offhand) but not dropped, put in any container, crafting
 * grid, bundle, item frame, armor stand or allay, nor dropped on death. Any tracker that still
 * turns up as an item entity is deleted on spawn.
 */
public final class TrackerListener implements Listener {

    private static final Set<InventoryAction> DROP_ACTIONS = Set.of(InventoryAction.DROP_ALL_CURSOR,
            InventoryAction.DROP_ONE_CURSOR, InventoryAction.DROP_ALL_SLOT, InventoryAction.DROP_ONE_SLOT);
    // The top half of the player's own inventory screen: CRAFTING (the 2x2 grid) on Paper; CREATIVE
    // and PLAYER cover the creative tab and views whose top is the player inventory itself.
    private static final Set<InventoryType> OWN_INVENTORY_TOPS = Set.of(InventoryType.CRAFTING,
            InventoryType.CREATIVE, InventoryType.PLAYER);

    private final TagService service;
    private final TrackerService tracker;

    public TrackerListener(TagService service, TrackerService tracker) {
        this.service = service;
        this.tracker = tracker;
    }

    // ignoreCancelled = false: Bukkit fires every right-click-air already cancelled.
    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!tracker.isTracker(event.getItem())) {
            return;
        }
        // Denies only the item's own use (a lodestone would otherwise rebind it), so doors and
        // chests still open with the compass in hand.
        event.setUseItemInHand(Event.Result.DENY);
        service.cycleTracker(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        ItemStack hotbarItem = event.getHotbarButton() >= 0
                ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;
        boolean currentIsTracker = tracker.isTracker(event.getCurrentItem());
        boolean cursorIsTracker = tracker.isTracker(event.getCursor());
        boolean hotbarIsTracker = tracker.isTracker(hotbarItem);
        if (!currentIsTracker && !cursorIsTracker && !hotbarIsTracker) {
            return;
        }
        if (DROP_ACTIONS.contains(event.getAction()) || leavesOwnInventory(event, cursorIsTracker || hotbarIsTracker)
                || intoBundle(event, currentIsTracker, cursorIsTracker)) {
            event.setCancelled(true);
        }
    }

    /**
     * With any other window open (chest, ender chest, crafter, villager...) the tracker stays
     * frozen outright. In the player's own inventory view the only foreign slots are the 2x2
     * crafting grid and its result.
     */
    private boolean leavesOwnInventory(InventoryClickEvent event, boolean placingTracker) {
        if (!OWN_INVENTORY_TOPS.contains(event.getView().getTopInventory().getType())) {
            return true;
        }
        InventoryType.SlotType slotType = event.getSlotType();
        return placingTracker && (slotType == InventoryType.SlotType.CRAFTING || slotType == InventoryType.SlotType.RESULT);
    }

    private boolean intoBundle(InventoryClickEvent event, boolean currentIsTracker, boolean cursorIsTracker) {
        return (cursorIsTracker && isBundle(event.getCurrentItem())) || (currentIsTracker && isBundle(event.getCursor()));
    }

    private static boolean isBundle(ItemStack item) {
        return item != null && item.getType().name().endsWith("BUNDLE");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!tracker.isTracker(event.getOldCursor())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    // The creative inventory can delete or clone any item, so it never touches the tracker.
    @EventHandler(ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (tracker.isTracker(event.getCurrentItem()) || tracker.isTracker(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (tracker.isTracker(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame) && !(event.getRightClicked() instanceof Allay)) {
            return;
        }
        if (tracker.isTracker(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (tracker.isTracker(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(tracker::isTracker);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (tracker.isTracker(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onOpenInventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && !service.isIt(player.getUniqueId())) {
            tracker.purge(player);
        }
    }
}
