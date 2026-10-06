package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.Enderman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** A placed ball block can't be destroyed by anything other than a deliberate break: not
 * explosions, pistons, flowing water/lava, Endermen, or fire. */
public final class BallProtectionListener implements Listener {

    private final BallRegistry registry;

    public BallProtectionListener(BallRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isBallBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isBallBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isBallBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isBallBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (isBallBlock(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEndermanGrief(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Enderman && isBallBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (isBallBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private boolean isBallBlock(Block block) {
        return block.getState() instanceof Skull skull && registry.items().isBallBlock(skull);
    }
}
