package dev.sirbepy.mc.carry.listener;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.sirbepy.mc.carry.CarryConfig;
import dev.sirbepy.mc.carry.CarryManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.function.Supplier;

/**
 * Sneak + right-click with an empty hand picks a mob up. While carrying, the player's hands are
 * locked: right-clicking a block puts the mob down, sneak + left-click throws it, everything else
 * (attacking, using items, switching slots, inventory clicks) is cancelled.
 */
public final class CarryListener implements Listener {

    private final CarryManager carry;
    private final Supplier<CarryConfig> config;

    public CarryListener(CarryManager carry, Supplier<CarryConfig> config) {
        this.carry = carry;
        this.config = config;
    }

    // ignoreCancelled: a protection plugin that already refused the interaction (Bolt, Towny) wins.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity target = event.getRightClicked();
        if (carry.isCarried(target) || carry.isCarrying(player)) {
            event.setCancelled(true);
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND || !player.isSneaking() || !player.getInventory().getItemInMainHand().isEmpty()
                || !player.hasPermission("carry.use") || !carry.isCarryable(target)) {
            return;
        }
        event.setCancelled(true);
        String denial = carry.tryPickUp(player, target);
        actionBar(player, denial != null ? denial : "picked-up");
    }

    // Not ignoreCancelled: Bukkit fires air clicks already cancelled.
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!carry.isCarrying(player) || event.getAction() == Action.PHYSICAL) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        // An empty main hand makes the client send the off hand's click as well.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block clicked = event.getClickedBlock();
        String denial = null;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && clicked != null) {
            Location target = clicked.getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
            denial = carry.tryPutDown(player, target);
        } else if (event.getAction().isLeftClick() && player.isSneaking()) {
            denial = carry.tryThrow(player);
        }
        if (denial != null) {
            actionBar(player, denial);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !carry.isCarrying(player)) {
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            String denial = carry.tryThrow(player);
            if (denial != null) {
                actionBar(player, denial);
            }
        }
    }

    // Void and /kill still go through, so a carried mob can always be removed.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCarriedDamaged(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.VOID && cause != EntityDamageEvent.DamageCause.KILL
                && carry.isCarried(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // Any dismount the plugin didn't make (diving, a teleport plugin's eject) puts the mob down there.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getDismounted() instanceof Player && carry.isCarried(event.getEntity())) {
            carry.forget(event.getEntity());
        }
    }

    @EventHandler
    public void onCarriedRemoved(EntityRemoveFromWorldEvent event) {
        if (carry.isCarried(event.getEntity())) {
            carry.forget(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        carry.release(event.getPlayer(), event.getFrom());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        carry.release(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        carry.release(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSlotChange(PlayerItemHeldEvent event) {
        if (carry.isCarrying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (carry.isCarrying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && carry.isCarrying(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && carry.isCarrying(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (carry.isCarrying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (carry.isCarrying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private void actionBar(Player player, String key) {
        player.sendActionBar(config.get().messages().render(key));
    }
}
