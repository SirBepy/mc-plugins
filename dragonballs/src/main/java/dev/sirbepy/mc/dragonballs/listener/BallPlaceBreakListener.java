package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Placing sets a ball down as a world block; breaking picks it back up as an item. Stone balls can
 * be placed and broken exactly like active ones (only the texture/name/PDC flag differ) - there's
 * no special-case here for stone at all, which is the point: {@link BallRegistry#markPlaced} and the
 * item factory already carry the stone flag through on their own.
 */
public final class BallPlaceBreakListener implements Listener {

    private final BallRegistry registry;

    public BallPlaceBreakListener(BallRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack placed = event.getItemInHand();
        registry.starOf(placed).ifPresent(star -> {
            boolean stone = registry.items().isStoneItem(placed);
            Block block = event.getBlockPlaced();
            if (block.getState() instanceof Skull skull) {
                registry.items().markBlock(skull, star);
                registry.items().applyBlockTexture(skull, star, stone);
                skull.update(true);
            }
            registry.markPlaced(star, block.getLocation(), stone);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Skull skull) || !registry.items().isBallBlock(skull)) {
            return;
        }
        int star = registry.items().starOfBlock(skull).orElseThrow();
        boolean stone = registry.get(star).map(view -> view.stone()).orElse(false);
        event.setDropItems(false);
        Player player = event.getPlayer();
        ItemStack ball = registry.items().itemFor(star, stone);
        var leftover = player.getInventory().addItem(ball);
        if (leftover.isEmpty()) {
            registry.markHeld(star, player, stone);
        } else {
            var dropped = player.getWorld().dropItem(block.getLocation().add(0.5, 0.5, 0.5), leftover.values().iterator().next());
            registry.markDropped(star, dropped, stone);
        }
    }
}
