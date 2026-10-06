package dev.sirbepy.mc.cartel.phone;

import dev.sirbepy.mc.cartel.CartelConfig;
import dev.sirbepy.mc.cartel.CartelStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Right-clicking the phone opens its menu for the owner only. The phone can't be thrown away, is
 * kept through death (taken out of the drops before the graves datapack sees them), and nobody else
 * can pick it up. The owner gets it the first time they join.
 */
public final class PhoneListener implements Listener {

    private final Phone phone;
    private final Supplier<CartelConfig> config;
    private final CartelStore store;
    private final Consumer<Player> openMenu;

    public PhoneListener(Phone phone, Supplier<CartelConfig> config, CartelStore store, Consumer<Player> openMenu) {
        this.phone = phone;
        this.config = config;
        this.store = store;
        this.openMenu = openMenu;
    }

    // Right-clicking air arrives already cancelled, so cancelled events must still be seen.
    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!phone.isPhone(event.getItem())) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(config.get().owner())) {
            player.sendMessage(Component.text("The line is dead. This phone isn't yours.", NamedTextColor.GRAY));
            return;
        }
        openMenu.accept(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (phone.isPhone(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("You don't throw away the cartel's phone.", NamedTextColor.GRAY));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (phone.isPhone(event.getItem().getItemStack())
                && !event.getEntity().getUniqueId().equals(config.get().owner())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }
        Iterator<ItemStack> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack drop = drops.next();
            if (phone.isPhone(drop)) {
                drops.remove();
                event.getItemsToKeep().add(drop);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID owner = config.get().owner();
        if (player.getUniqueId().equals(owner) && !store.phoneGiven(owner)) {
            give(player);
            store.setPhoneGiven(owner, true);
        }
    }

    /** Puts a phone in {@code player}'s inventory, or at their feet if it's full. */
    public void give(Player player) {
        ItemStack item = phone.create();
        for (ItemStack overflow : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
        player.sendMessage(Component.text("A flip phone buzzes in your pocket. The cousins are a call away.",
                NamedTextColor.DARK_RED));
    }
}
