package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
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
 * item factory already carry the stone flag through on their own. Which set a ball belongs to comes
 * from its own PDC marker, so placing/breaking a Nether or End ball routes to that set's registry.
 */
public final class BallPlaceBreakListener implements Listener {

    private final BallSets sets;

    public BallPlaceBreakListener(BallSets sets) {
        this.sets = sets;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack placed = event.getItemInHand();
        sets.items().starOf(placed).ifPresent(star -> {
            BallSet set = sets.items().setOf(placed);
            BallRegistry registry = sets.registry(set);
            boolean stone = sets.items().isStoneItem(placed);
            Block block = event.getBlockPlaced();
            if (block.getState() instanceof Skull skull) {
                sets.items().markBlock(skull, set, star);
                sets.items().applyBlockTexture(skull, set, star, stone);
                skull.update(true);
            }
            registry.markPlaced(star, block.getLocation(), stone);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Skull skull) || !sets.items().isBallBlock(skull)) {
            return;
        }
        BallSet set = sets.items().setOfBlock(skull);
        BallRegistry registry = sets.registry(set);
        int star = sets.items().starOfBlock(skull).orElseThrow();
        boolean stone = registry.get(star).map(view -> view.stone()).orElse(false);
        event.setDropItems(false);
        Player player = event.getPlayer();
        ItemStack ball = sets.items().itemFor(set, star, stone);
        var leftover = player.getInventory().addItem(ball);
        if (leftover.isEmpty()) {
            registry.markHeld(star, player, stone);
        } else {
            var dropped = player.getWorld().dropItem(block.getLocation().add(0.5, 0.5, 0.5), leftover.values().iterator().next());
            registry.markDropped(star, dropped, stone);
        }
    }
}
