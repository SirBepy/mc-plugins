package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A ball never leaves its own home dimension. Crossing into any other world (a vanilla portal, a
 * plugin teleport, {@code /tp}) leaves every carried ball whose home isn't the destination behind
 * at the departure point, dropped through the same {@code World#dropItem} + {@code markDropped}
 * path death drops use so it gets the same lifecycle flags ({@link BallItemLifecycleListener}'s
 * {@code ItemSpawnEvent} handler) and the registry marks it DROPPED there. A ball whose home IS the
 * destination is untouched. {@code PlayerPortalEvent} needs its own handler alongside {@code
 * PlayerTeleportEvent} - despite extending it, it compiles its own static {@code HandlerList}
 * (confirmed via javap on paper-api), so a vanilla nether/end portal crossing would never reach a
 * handler registered only for the parent type. Same reasoning for {@code EntityPortalEvent} and
 * {@code EntityTeleportEvent} on the dropped-ball-entity side below.
 */
public final class BallDimensionGuardListener implements Listener {

    private final BallSets sets;
    private final Supplier<DragonBallsConfig> config;
    private final BallSlowness slowness;

    public BallDimensionGuardListener(BallSets sets, Supplier<DragonBallsConfig> config, BallSlowness slowness) {
        this.sets = sets;
        this.config = config;
        this.slowness = slowness;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        handleCrossWorldMove(event.getPlayer(), event.getFrom(), event.getTo());
    }

    /** Separate from {@link #onTeleport}: see the class javadoc for why a vanilla portal crossing
     * needs its own handler here rather than falling through the parent type's. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        handleCrossWorldMove(event.getPlayer(), event.getFrom(), event.getTo());
    }

    private void handleCrossWorldMove(Player player, Location from, Location to) {
        World fromWorld = from.getWorld();
        World toWorld = to == null ? null : to.getWorld();
        if (fromWorld == null || toWorld == null || fromWorld.equals(toWorld)) {
            return;
        }
        Location dropAt = from;
        boolean leftAnyBehind = false;

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            int s = slot;
            if (leaveBehindIfForeign(player, dropAt, toWorld, contents[slot], replacement -> inventory.setItem(s, replacement))) {
                leftAnyBehind = true;
            }
        }
        if (leaveBehindIfForeign(player, dropAt, toWorld, inventory.getItemInOffHand(), inventory::setItemInOffHand)) {
            leftAnyBehind = true;
        }
        if (leaveBehindIfForeign(player, dropAt, toWorld, player.getItemOnCursor(), player::setItemOnCursor)) {
            leftAnyBehind = true;
        }

        if (leftAnyBehind) {
            slowness.recomputeNextTick(player);
        }
    }

    /** @return true if {@code item} was a ball whose home isn't {@code destination} (and so was
     * left behind and cleared through {@code clearSlot}); false for anything else, including a ball
     * that's actually headed home. */
    private boolean leaveBehindIfForeign(Player player, Location dropAt, World destination, ItemStack item,
                                          Consumer<ItemStack> clearSlot) {
        if (!sets.items().isBall(item)) {
            return false;
        }
        BallSet set = sets.items().setOf(item);
        if (destination.getName().equals(set.worldName(config.get()))) {
            return false;
        }
        leaveBehind(player, dropAt, set, item);
        clearSlot.accept(null);
        return true;
    }

    private void leaveBehind(Player player, Location dropAt, BallSet set, ItemStack item) {
        int star = sets.items().starOf(item).orElseThrow();
        boolean stone = sets.items().isStoneItem(item);
        BallRegistry registry = sets.registry(set);
        Item entity = dropAt.getWorld().dropItem(dropAt, item);
        registry.markDropped(star, entity, stone);

        Component displayName = item.getItemMeta().displayName();
        String name = displayName != null
                ? PlainTextComponentSerializer.plainText().serialize(displayName)
                : star + "-Star Dragon Ball";
        config.get().messages().send(player, "ball-stays-behind",
                Placeholder.unparsed("name", name), Placeholder.unparsed("home", set.homeName()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (isBallItemEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        World from = event.getFrom().getWorld();
        World to = event.getTo() == null ? null : event.getTo().getWorld();
        if (from != null && to != null && !from.equals(to) && isBallItemEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private boolean isBallItemEntity(Entity entity) {
        return entity instanceof Item item && sets.items().isBall(item.getItemStack());
    }
}
