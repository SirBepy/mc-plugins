package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.classes.Classes;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Turns sneak + a bound input into an ability, cancelling the input's normal effect (the slot
 * change, the swap, the drop, the item use) so nothing moves. Also times how long each player has
 * been sneaking, for the key legend on the resource bar.
 */
public final class BindListener implements Listener {

    /** Holding sneak this long swaps the resource bar's text for the key legend. */
    static final long LEGEND_AFTER_MILLIS = 400;

    private final Classes classes;
    private final Binds binds;
    private final AbilitySlots slots;
    private final LongSupplier now;
    private final Map<UUID, Long> sneakingSince = new HashMap<>();

    public BindListener(Classes classes, Binds binds, AbilitySlots slots, LongSupplier now) {
        this.classes = classes;
        this.binds = binds;
        this.slots = slots;
        this.now = now;
    }

    public boolean showLegend(Player player) {
        Long since = sneakingSince.get(player.getUniqueId());
        return since != null && player.isSneaking() && now.getAsLong() - since >= LEGEND_AFTER_MILLIS;
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) sneakingSince.put(event.getPlayer().getUniqueId(), now.getAsLong());
        else sneakingSince.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sneakingSince.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHotbar(PlayerItemHeldEvent event) {
        if (fire(event.getPlayer(), Bind.hotbar(event.getNewSlot()))) event.setCancelled(true);
    }

    // ignoreCancelled: Carry and Tag's head lock cancel swaps that mean something to them.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (fire(event.getPlayer(), Bind.SWAP)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (fire(event.getPlayer(), Bind.DROP)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent event) {
        // Right click fires once per hand; only the main hand's counts.
        if (event.getHand() != EquipmentSlot.HAND) return;
        Bind input = event.getAction() == Action.LEFT_CLICK_AIR ? Bind.LEFT_AIR
                : event.getAction() == Action.RIGHT_CLICK_AIR ? Bind.RIGHT_AIR : null;
        if (input != null && fire(event.getPlayer(), input)) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    /** Whether {@code input} is bound for this sneaking player with a class; casts if so. */
    private boolean fire(Player player, Bind input) {
        if (!player.isSneaking() || classes.active(player.getUniqueId()) == null) return false;
        Slot slot = binds.slotFor(player.getUniqueId(), input);
        if (slot == null) return false;
        slots.cast(player, slot);
        return true;
    }
}
