package dev.sirbepy.mc.endfight.arena;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;

/**
 * Keeps the arena standing against everything the dragon fight can throw at it.
 *
 * <p>javap-verified against the 26.3 server jar
 * ({@code EnderDragon.checkWalls(ServerLevel, AABB)}): the dragon's own wall-breaking calls
 * {@code CraftEventFactory.callEntityExplodeEvent} and then only removes blocks still in
 * {@code event.blockList()} - so pulling arena positions out of that list (same as end crystals'
 * own explosions and any other entity explosion) is enough to stop it, no separate dragon-specific
 * event exists on Paper.
 */
public final class ArenaProtection implements Listener {

    private final ArenaRegistry registry;

    public ArenaProtection(ArenaRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeArenaBlocks(event.blockList().iterator());
    }

    /** Beds/respawn anchors going off in the End - a separate event from entity explosions. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeArenaBlocks(event.blockList().iterator());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!isArena(event.getBlock())) {
            return;
        }
        if (!event.getPlayer().hasPermission("endfight.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (isArena(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgniteSpread(BlockIgniteEvent event) {
        if (event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD && isArena(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private void removeArenaBlocks(Iterator<Block> blocks) {
        while (blocks.hasNext()) {
            Block block = blocks.next();
            if (isArena(block)) {
                blocks.remove();
            }
        }
    }

    private boolean isArena(Block block) {
        return registry.isArenaBlock(block.getX(), block.getY(), block.getZ());
    }
}
