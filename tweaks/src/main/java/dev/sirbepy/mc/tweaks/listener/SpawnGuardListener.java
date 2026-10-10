package dev.sirbepy.mc.tweaks.listener;

import dev.sirbepy.mc.core.SpawnArea;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.util.List;

/**
 * Stands in for vanilla spawn protection, which the server runs at {@code spawn-protection=0}: on
 * Paper 26.3 vanilla protection swallows every non-op right-click before any plugin event fires, so
 * the spawn radar sign and guide lecterns were dead for regular players. This guards only the
 * destructive side - breaking, placing, buckets, hanging entities, armor stands and explosions - and
 * leaves doors, signs, lecterns, buttons and chests usable, across the whole {@link SpawnArea}. Ops
 * are exempt, as they were in vanilla, and so is anyone with {@value #BUILD_PERMISSION} (Joe,
 * 2026-10-10: "make it so mijo is able to build/break in the spawn area"; nobody is op on live).
 */
public final class SpawnGuardListener implements Listener {

    public static final String BUILD_PERMISSION = "tweaks.spawn.build";

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isGuarded(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isGuarded(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isGuarded(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isGuarded(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (isGuarded(playerBehind(event.getRemover()), event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStandDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ArmorStand
                && isGuarded(playerBehind(event.getDamager()), event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    // Explosions spare the spawn blocks whoever lit them, the way a protected spawn would want; the
    // blast itself still goes off and still hurts entities.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        spareSpawnBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        spareSpawnBlocks(event.blockList());
    }

    private static void spareSpawnBlocks(List<Block> blocks) {
        blocks.removeIf(block -> SpawnArea.contains(block.getLocation()));
    }

    private static boolean isGuarded(Player player, Location location) {
        return player != null && !player.isOp() && !player.hasPermission(BUILD_PERMISSION) && SpawnArea.contains(location);
    }

    /** The player who did it, directly or through an arrow/trident they shot; null for anything else. */
    static Player playerBehind(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
