package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Balls never stay in an offline player's inventory (Joe, 2026-10-08): on quit (a kick and a
 * restart disconnect included) every carried ball drops where they logged out, through the same
 * {@code World#dropItem} + {@code markDropped} path death drops use. A player who logged out before
 * this rule, still carrying balls, drops them at their feet on their next join instead.
 *
 * <p>Only a ball the registry has HELD by this player is dropped; any other ball item in the
 * inventory is a dupe, left for {@link BallJoinDupeListener} to delete, so dropping can never mint a
 * second copy. Paper disables plugins before it disconnects players on shutdown, so the quit
 * handler never sees a restart: {@link #dropCarried} is also called from {@code onDisable}.
 */
public final class BallLogoutDropListener implements Listener {

    private final BallSets sets;
    private final Supplier<DragonBallsConfig> config;
    private final BallSlowness slowness;
    private final Consumer<Item> lifecycleFlags;

    public BallLogoutDropListener(BallSets sets, Supplier<DragonBallsConfig> config, BallSlowness slowness) {
        this(sets, config, slowness, BallItemLifecycleListener::applyFlags);
    }

    /** {@code lifecycleFlags} is injectable because MockBukkit leaves {@code Item#setUnlimitedLifetime}
     * unimplemented. */
    BallLogoutDropListener(BallSets sets, Supplier<DragonBallsConfig> config, BallSlowness slowness,
                           Consumer<Item> lifecycleFlags) {
        this.sets = sets;
        this.config = config;
        this.slowness = slowness;
        this.lifecycleFlags = lifecycleFlags;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        dropCarried(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (dropCarried(player) > 0) {
            config.get().messages().send(player, "balls-dropped-on-join");
            slowness.recomputeNextTick(player);
        }
    }

    /** @return how many balls were dropped at the player's location. */
    public int dropCarried(Player player) {
        Location at = player.getLocation();
        PlayerInventory inventory = player.getInventory();
        int dropped = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            int s = slot;
            dropped += dropIfHeld(player, at, contents[slot], replacement -> inventory.setItem(s, replacement));
        }
        dropped += dropIfHeld(player, at, inventory.getItemInOffHand(), inventory::setItemInOffHand);
        dropped += dropIfHeld(player, at, player.getItemOnCursor(), player::setItemOnCursor);
        return dropped;
    }

    private int dropIfHeld(Player player, Location at, ItemStack item, Consumer<ItemStack> clearSlot) {
        if (!sets.items().isBall(item)) {
            return 0;
        }
        BallRegistry registry = sets.registryFor(item).orElseThrow();
        int star = sets.items().starOf(item).orElseThrow();
        if (!registry.isHeldBy(star, player.getUniqueId())) {
            return 0;
        }
        clearSlot.accept(null);
        Item entity = at.getWorld().dropItem(at, item);
        // Applied directly as well as via ItemSpawnEvent: during onDisable this plugin's own
        // listeners no longer fire, and an unflagged ball item would despawn after 5 minutes.
        lifecycleFlags.accept(entity);
        registry.markDropped(star, entity, sets.items().isStoneItem(item));
        return 1;
    }
}
