package dev.sirbepy.mc.dragonballs.station;

import dev.sirbepy.mc.dragonballs.radar.RadarItems;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

/**
 * Right-clicking the configured sign buys a Dragon Radar through whatever classic Vault
 * {@code Economy} is registered (VaultUnlocked on the live server). No purchase limit, no sign
 * editing - the interact event is always cancelled for this block.
 */
public final class RadarShop implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final Supplier<StationConfig> stationConfig;
    private final RadarItems radarItems;

    public RadarShop(JavaPlugin plugin, Supplier<StationConfig> stationConfig, RadarItems radarItems) {
        this.plugin = plugin;
        this.stationConfig = stationConfig;
        this.radarItems = radarItems;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || !isConfiguredSign(clicked)) {
            return;
        }
        event.setCancelled(true);
        purchase(event.getPlayer());
    }

    private boolean isConfiguredSign(Block block) {
        StationConfig config = stationConfig.get();
        return block.getWorld().getName().equals(config.signWorld())
                && block.getX() == config.signX()
                && block.getY() == config.signY()
                && block.getZ() == config.signZ();
    }

    private void purchase(Player player) {
        Economy economy = economy();
        if (economy == null) {
            plugin.getLogger().warning("Radar shop: no Vault Economy provider registered; the sign is closed.");
            player.sendMessage(MINI.deserialize("<red>The radar shop is closed."));
            return;
        }

        double price = stationConfig.get().radarPrice();
        if (!economy.has(player, price)) {
            double balance = economy.getBalance(player);
            player.sendMessage(MINI.deserialize("<red>A Dragon Radar costs " + formatAmount(price)
                    + " coins. You have " + formatAmount(balance) + "."));
            return;
        }

        economy.withdrawPlayer(player, price);
        ItemStack radar = radarItems.create();
        var leftover = player.getInventory().addItem(radar);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItem(player.getLocation(), leftover.values().iterator().next());
        }
        player.sendMessage(MINI.deserialize("<gold>You bought a Dragon Radar!"));
    }

    private Economy economy() {
        ServicesManager services = plugin.getServer().getServicesManager();
        var registration = services.getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    private static String formatAmount(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
