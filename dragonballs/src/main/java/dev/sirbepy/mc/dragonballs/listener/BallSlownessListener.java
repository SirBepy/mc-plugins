package dev.sirbepy.mc.dragonballs.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Keeps each player's carrying-speed penalty in sync with how many balls they hold. Every handler
 * just schedules a recount (see {@link BallSlowness#recomputeNextTick}); none of them compute
 * anything themselves, so this stays a thin list of "something might have changed" triggers.
 */
public final class BallSlownessListener implements Listener {

    private final BallSlowness slowness;

    public BallSlownessListener(BallSlowness slowness) {
        this.slowness = slowness;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        slowness.recomputeNextTick(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        slowness.recomputeNextTick(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        slowness.recomputeNextTick(event.getEntity());
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            slowness.recomputeNextTick(player);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        slowness.recomputeNextTick(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            slowness.recomputeNextTick(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            slowness.recomputeNextTick(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            slowness.recomputeNextTick(player);
        }
    }
}
