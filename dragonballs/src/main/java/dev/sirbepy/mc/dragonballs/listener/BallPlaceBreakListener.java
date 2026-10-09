package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.placed.FloatingBalls;
import dev.sirbepy.mc.dragonballs.placed.PickupGuard;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.registry.BallView;
import dev.sirbepy.mc.dragonballs.scatter.ScatterTownyGuard;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Placing a ball sets it floating in the block it would have filled; hitting it picks it back up
 * as an item. The vanilla head placement still runs first, so spawn protection and Towny's build
 * rules decide where a ball may go exactly as they did for the head block, and only then is the
 * head cancelled in favour of the floating ball. Stone balls place and pick up like active ones;
 * which set a ball belongs to comes from its own PDC marker. Picking one up announces it server-wide
 * like a ground pickup, unless the same player set it down in the last 30 s (adjusting a summon
 * cluster shouldn't spam chat). A ball can't be placed inside a Towny town at all (Joe, 2026-10-08:
 * "dragon balls can never be placed in a town"), so summons happen in the wilderness; it stays in
 * hand.
 */
public final class BallPlaceBreakListener implements Listener {

    private final BallSets sets;
    private final FloatingBalls balls;
    private final PickupGuard guard;
    private final ScatterTownyGuard towns;
    private final FoundAnnouncer announcer;
    // Who last set a given ball down and when, for the self-placed announce suppression.
    private final RecentSelfAction recentlyPlaced = new RecentSelfAction();

    public BallPlaceBreakListener(BallSets sets, FloatingBalls balls, PickupGuard guard, ScatterTownyGuard towns,
                                  Supplier<DragonBallsConfig> config) {
        this(sets, balls, guard, towns,
                (finder, set, star) -> BallPickupListener.announceFound(config.get(), finder, set, star));
    }

    BallPlaceBreakListener(BallSets sets, FloatingBalls balls, PickupGuard guard, ScatterTownyGuard towns,
                           FoundAnnouncer announcer) {
        this.sets = sets;
        this.balls = balls;
        this.guard = guard;
        this.towns = towns;
        this.announcer = announcer;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        ItemStack placed = event.getItemInHand();
        OptionalInt star = sets.items().starOf(placed);
        if (star.isEmpty()) {
            if (balls.isOccupied(block.getLocation())) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (balls.isOccupied(block.getLocation())) {
            return;
        }
        if (!towns.isWilderness(block.getLocation().add(0.5, 0.5, 0.5))) {
            event.getPlayer().sendActionBar(Component.text("Dragon Balls can't be placed inside a town.", NamedTextColor.RED));
            return;
        }
        BallSet set = sets.items().setOf(placed);
        boolean stone = sets.items().isStoneItem(placed);
        takeOne(event.getPlayer().getInventory(), event.getHand(), placed);
        sets.registry(set).markPlaced(star.getAsInt(), block.getLocation(), stone);
        recentlyPlaced.record(set, star.getAsInt(), event.getPlayer().getUniqueId());
    }

    private static void takeOne(PlayerInventory inventory, EquipmentSlot hand, ItemStack placed) {
        if (placed.getAmount() > 1) {
            ItemStack rest = placed.clone();
            rest.setAmount(placed.getAmount() - 1);
            inventory.setItem(hand, rest);
        } else {
            inventory.setItem(hand, null);
        }
    }

    // After the summon trigger, which cancels a hit it turns into a summon.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(PrePlayerAttackEntityEvent event) {
        balls.ballOf(event.getAttacked()).ifPresent(ref -> {
            event.setCancelled(true);
            pickUp(event.getPlayer(), ref);
        });
    }

    private void pickUp(Player player, FloatingBalls.BallRef ref) {
        BallRegistry registry = sets.registry(ref.set());
        int star = ref.star();
        Location block = ref.block();
        if (!registry.isPlacedAt(star, block)) {
            return;
        }
        if (!guard.mayPickUp(player, block)) {
            player.sendActionBar(Component.text("You can't take a Dragon Ball from here.", NamedTextColor.RED));
            return;
        }
        boolean stone = registry.get(star).map(BallView::stone).orElse(false);
        ItemStack ball = sets.items().itemFor(ref.set(), star, stone);
        var leftover = player.getInventory().addItem(ball);
        if (leftover.isEmpty()) {
            registry.markHeld(star, player, stone);
            if (!recentlyPlaced.isRecent(ref.set(), star, player.getUniqueId())) {
                announcer.announce(player, ref.set(), star);
            }
        } else {
            var dropped = player.getWorld().dropItem(block.clone().add(0.5, 0.5, 0.5), leftover.values().iterator().next());
            registry.markDropped(star, dropped, stone);
        }
        player.getWorld().playSound(block.clone().add(0.5, 0.5, 0.5), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
    }
}
