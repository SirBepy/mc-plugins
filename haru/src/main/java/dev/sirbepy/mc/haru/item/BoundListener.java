package dev.sirbepy.mc.haru.item;

import dev.sirbepy.mc.core.Inventories;
import dev.sirbepy.mc.haru.HaruContext;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.dialogue.Speech;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

/**
 * Nobody but its owner can pick up or click out a bound item; anything that still reaches another
 * player (a hopper, a shop plugin) is taken off them by {@link #sweep} and kept at Haru for its owner.
 */
public final class BoundListener implements Listener {

    private final HaruContext ctx;

    public BoundListener(HaruContext ctx) {
        this.ctx = ctx;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && ctx.bound().foreignTo(event.getItem().getItemStack(), player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        UUID clicker = event.getWhoClicked().getUniqueId();
        if (ctx.bound().foreignTo(event.getCurrentItem(), clicker) || ctx.bound().foreignTo(event.getCursor(), clicker)) {
            event.setCancelled(true);
        }
        if (event.getHotbarButton() >= 0) {
            ItemStack swapped = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
            if (ctx.bound().foreignTo(swapped, clicker)) {
                event.setCancelled(true);
            }
        }
    }

    /** Moves every bound item a player isn't the owner of into Haru's lost and found. */
    public void sweep(Iterable<? extends Player> players) {
        boolean changed = false;
        for (Player player : players) {
            PlayerInventory inventory = player.getInventory();
            ItemStack[] contents = inventory.getContents();
            boolean took = false;
            for (int slot = 0; slot < contents.length; slot++) {
                UUID owner = ctx.bound().ownerOf(contents[slot]);
                if (owner == null || owner.equals(player.getUniqueId())) {
                    continue;
                }
                ctx.store().state(owner).lostAndFound().add(contents[slot]);
                inventory.setItem(slot, null);
                took = true;
            }
            if (took) {
                Speech.note(player, "Something bound to another player slipped out of your hands and drifted back to Haru.");
                changed = true;
            }
        }
        if (changed) {
            ctx.store().save();
        }
    }

    /** Hands the owner everything waiting for them; true if there was anything. */
    public boolean returnLostItems(Player owner, OwnerState state) {
        if (state.lostAndFound().isEmpty()) {
            return false;
        }
        for (ItemStack item : state.lostAndFound()) {
            Inventories.giveOrDrop(owner, item);
        }
        state.lostAndFound().clear();
        ctx.store().save();
        return true;
    }
}
