package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Balls may move freely within a player's own inventory (plain click/drag between slots, dropping
 * them), but never into any other inventory: a container, the crafting grid, a bundle, a hopper's
 * automatic transfer, or onto an entity (item frame, armor stand, allay) or a direct-insert block
 * (decorated pot, chiseled bookshelf). Equipping one as a helmet is blocked the same way.
 */
public final class BallContainerGuardListener implements Listener {

    private static final Set<Material> DIRECT_INSERT_BLOCKS = Set.of(Material.DECORATED_POT, Material.CHISELED_BOOKSHELF);

    private final BallRegistry registry;

    public BallContainerGuardListener(BallRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // A click landing directly on a crafting-grid or container slot (anvil, chest, furnace,
        // hopper GUI, shulker, barrel, crafter, etc.): clickedInventory is never the player's own
        // PlayerInventory in that case.
        if (event.getClickedInventory() != null && !event.getClickedInventory().equals(player.getInventory())
                && (registry.isBall(current) || registry.isBall(cursor))) {
            event.setCancelled(true);
            return;
        }
        // Shift-click auto-routes to whatever else is open (another container, or the crafting grid
        // when nothing else is); simplest to block it outright rather than special-case the target.
        if (registry.isBall(current) && event.getClick().isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        // Clicking a ball (on the cursor) onto an open bundle inserts it into the bundle.
        if (current != null && current.getType() == Material.BUNDLE && registry.isBall(cursor)) {
            event.setCancelled(true);
            return;
        }
        // The reverse: a bundle on the cursor right-clicked onto a ball scoops the ball into it.
        if (cursor != null && cursor.getType() == Material.BUNDLE && registry.isBall(current)) {
            event.setCancelled(true);
            return;
        }
        // Manually equipping a ball into the armor/helmet slot.
        if (event.getSlotType() == InventoryType.SlotType.ARMOR && registry.isBall(cursor)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!registry.isBall(event.getOldCursor())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (registry.isBall(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** A hopper (or hopper minecart) sucking a dropped ball off the ground. */
    @EventHandler(ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (registry.isBall(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame || event.getRightClicked() instanceof ArmorStand
                || event.getRightClicked() instanceof Allay)) {
            return;
        }
        Player player = event.getPlayer();
        if (registry.isBall(player.getInventory().getItemInMainHand()) || registry.isBall(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (DIRECT_INSERT_BLOCKS.contains(event.getClickedBlock().getType()) && registry.isBall(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
