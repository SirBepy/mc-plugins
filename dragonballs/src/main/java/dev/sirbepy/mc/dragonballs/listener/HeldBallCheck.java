package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.registry.BallState;
import dev.sirbepy.mc.dragonballs.registry.BallView;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Keeps "HELD by X" true: a ball the registry has held by an online player must actually be in
 * their inventory, offhand or cursor. Datapacks and vanilla commands move items with no Bukkit
 * event (graves' {@code item replace} when a grave is opened, {@code /clear}, {@code item replace}),
 * so the listeners alone can't see a ball leave. Runs every second (cheap: at most 21 held balls,
 * one inventory scan each). A ball that left its holder is either lying near them as an
 * item entity the registry never heard of (graves spawns the slot's old content as a fresh entity),
 * which becomes the tracked DROPPED entity, or it's gone, and drops at the holder's feet the way an
 * offline holder's ball drops where they logged out: the player loses nothing to someone else's
 * bug, and the drop is synchronous, so it can't race a scatter or a give the way an async
 * re-scatter would.
 *
 * <p>The reverse runs too: a ball item in an online player's inventory, offhand or cursor that its
 * set's registry doesn't have HELD by that player is a stale copy (a rescatter moves a held ball
 * without touching the item, the startup offline-holder drop mints a new one while the saved
 * inventory keeps the old), and is deleted on the spot rather than left until their next join.
 */
public final class HeldBallCheck implements Runnable {

    public static final long PERIOD_TICKS = 20L;
    // Consecutive runs a ball must be missing (with no stray entity) before it's dropped anew, so a
    // single unlucky moment can never mint a second copy for a player who still has it.
    static final int MISSES_BEFORE_DROP = 2;
    // A creative client moves items around its inventory with the server-side cursor empty, so a
    // ball being dragged there looks missing until it's put down; no drag takes 10 seconds.
    static final int CREATIVE_MISSES_BEFORE_DROP = 10;
    static final double STRAY_RADIUS = 32;

    private final BallSets sets;
    private final Supplier<DragonBallsConfig> config;
    private final Consumer<Item> lifecycleFlags;
    private final Logger logger;
    private final Map<String, Integer> misses = new HashMap<>();
    // Set after construction (BallPickupListener's constructor itself takes this check), so an
    // adopted stray can count as its former holder's own drop; null is tolerated so a test that
    // only exercises the check in isolation doesn't have to wire it.
    private BallPickupListener pickupListener;

    public HeldBallCheck(BallSets sets, Supplier<DragonBallsConfig> config, Logger logger) {
        this(sets, config, BallItemLifecycleListener::applyFlags, logger);
    }

    /** {@code lifecycleFlags} is injectable because MockBukkit leaves {@code Item#setUnlimitedLifetime}
     * unimplemented. */
    HeldBallCheck(BallSets sets, Supplier<DragonBallsConfig> config, Consumer<Item> lifecycleFlags, Logger logger) {
        this.sets = sets;
        this.config = config;
        this.lifecycleFlags = lifecycleFlags;
        this.logger = logger;
    }

    /** Lets an adoption in {@link #check} also suppress the "found" broadcast for the former
     * holder picking the adopted ball back up, the same way a real drop would. Public: the plugin
     * wires this from outside the listener package, after constructing both. */
    public void trackSelfPickupsWith(BallPickupListener pickupListener) {
        this.pickupListener = pickupListener;
    }

    /** True when {@code registry} has {@code star} HELD by a player who is online but isn't carrying
     * it: wherever that ball is now, it isn't where the registry says. */
    public boolean isAdrift(BallRegistry registry, int star) {
        Optional<BallView> view = registry.get(star);
        if (view.isEmpty() || view.get().state() != BallState.HELD) {
            return false;
        }
        Player holder = Bukkit.getPlayer(view.get().holderId());
        return holder != null && !sets.items().carries(holder, registry.set(), star);
    }

    @Override
    public void run() {
        Set<String> stillHeld = new HashSet<>();
        for (BallRegistry registry : sets.all()) {
            if (!sets.isEnabled(registry.set())) {
                continue;
            }
            for (BallView view : registry.held()) {
                String key = registry.set() + ":" + view.star();
                stillHeld.add(key);
                check(registry, view, key);
            }
        }
        // A miss only counts toward a drop while the ball stays HELD in between.
        misses.keySet().retainAll(stillHeld);
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeStaleCopies(player);
        }
    }

    /** Runs after the held check, so a ball it just dropped anew at the holder's feet makes any copy
     * that turns up in their inventory later a stale one: the dropped entity is the real ball now. */
    private void removeStaleCopies(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isStaleCopy(contents[slot], player)) {
                inventory.setItem(slot, null);
            }
        }
        if (isStaleCopy(player.getItemOnCursor(), player)) {
            player.setItemOnCursor(null);
        }
    }

    private boolean isStaleCopy(ItemStack item, Player player) {
        Optional<BallRegistry> owner = sets.registryFor(item);
        if (owner.isEmpty() || !sets.isEnabled(owner.get().set())) {
            return false;
        }
        BallRegistry registry = owner.get();
        int star = sets.items().starOf(item).getAsInt();
        if (registry.isHeldBy(star, player.getUniqueId())) {
            return false;
        }
        logger.warning(registry.set() + " " + star + "-star copy in " + player.getName()
                + "'s inventory isn't the tracked ball (registry: " + BallPickupListener.describe(registry, star)
                + "); removed it");
        return true;
    }

    private void check(BallRegistry registry, BallView view, String key) {
        Player holder = Bukkit.getPlayer(view.holderId());
        if (holder == null || sets.items().carries(holder, registry.set(), view.star())) {
            misses.remove(key);
            return;
        }
        Item stray = findStray(registry, view.star(), holder);
        if (stray != null) {
            misses.remove(key);
            lifecycleFlags.accept(stray);
            registry.markDropped(view.star(), stray, sets.items().isStoneItem(stray.getItemStack()));
            if (pickupListener != null) {
                // The adoption behaves exactly like holder dropping it themselves, so it gets the
                // same 30 s "don't re-announce" grace on the pickup that follows.
                pickupListener.recordSelfDrop(registry.set(), view.star(), holder.getUniqueId());
            }
            logger.info(registry.set() + " " + view.star() + "-star left " + holder.getName()
                    + "'s inventory without an event; tracking it as dropped at " + BallView.describe(stray.getLocation()));
            return;
        }
        int threshold = holder.getGameMode() == GameMode.CREATIVE ? CREATIVE_MISSES_BEFORE_DROP : MISSES_BEFORE_DROP;
        if (misses.merge(key, 1, Integer::sum) < threshold) {
            return;
        }
        misses.remove(key);
        Location at = dropSpot(registry, holder, view);
        if (at == null) {
            return;
        }
        Item entity = at.getWorld().dropItem(at, sets.items().itemFor(registry.set(), view.star(), view.stone()));
        lifecycleFlags.accept(entity);
        registry.markDropped(view.star(), entity, view.stone());
        config.get().messages().send(holder, "ball-returned", Placeholder.unparsed("star", String.valueOf(view.star())));
        logger.warning(registry.set() + " " + view.star() + "-star vanished from " + holder.getName()
                + "'s inventory without an event; dropped it at their feet, " + BallView.describe(at));
    }

    /** The holder's feet; a ball never leaves its home world, so outside it (never expected: the
     * dimension guard drops a ball at the border) it goes to its last known spot or the world spawn. */
    private Location dropSpot(BallRegistry registry, Player holder, BallView view) {
        World home = registry.set().world(config.get());
        if (home == null) {
            return null;
        }
        if (home.equals(holder.getWorld())) {
            return holder.getLocation();
        }
        Location last = view.lastKnownLocation();
        return last != null && home.equals(last.getWorld()) ? last : home.getSpawnLocation();
    }

    /** A ball item entity of this set and star lying loose near the holder, or null. Only near: the
     * datapacks that move items without an event spawn them where the player is (a grave they just
     * opened), while a far one is an old copy left in a chunk that was unloaded when its ball moved on,
     * a dupe that must not become the real ball. */
    private Item findStray(BallRegistry registry, int star, Player holder) {
        Location near = holder.getLocation();
        for (Item item : holder.getWorld().getEntitiesByClass(Item.class)) {
            if (item.isValid() && item.getLocation().distanceSquared(near) <= STRAY_RADIUS * STRAY_RADIUS
                    && sets.items().isBall(item.getItemStack(), registry.set(), star)) {
                return item;
            }
        }
        return null;
    }
}
