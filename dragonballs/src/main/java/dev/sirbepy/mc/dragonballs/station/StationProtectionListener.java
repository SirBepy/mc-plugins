package dev.sirbepy.mc.dragonballs.station;

import org.bukkit.GameMode;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Protects the 7 decorative station heads the same way {@code BallProtectionListener} protects real
 * ball blocks (explosions, pistons, flowing liquid, Endermen, fire), plus blocking survival breaks
 * outright - a decor head isn't pickupable, so unlike a ball block there's no "intended" break path.
 */
public final class StationProtectionListener implements Listener {

    private final StationDisplay display;

    public StationProtectionListener(StationDisplay display) {
        this.display = display;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!display.isDecor(event.getBlock())) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isOp() && player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(display::isDecor);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(display::isDecor);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(display::isDecor)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(display::isDecor)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (display.isDecor(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEndermanGrief(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Enderman && display.isDecor(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (display.isDecor(event.getBlock())) {
            event.setCancelled(true);
        }
    }
}
