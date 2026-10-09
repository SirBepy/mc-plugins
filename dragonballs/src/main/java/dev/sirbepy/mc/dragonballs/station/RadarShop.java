package dev.sirbepy.mc.dragonballs.station;

import dev.sirbepy.mc.core.econ.VaultEconomy;
import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.radar.RadarItems;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

/**
 * Right-clicking one of the configured radar signs (3 at the Dragon Ball station, the same 3 again at
 * the market's Radars shop) buys that set's Dragon Radar through whatever classic Vault
 * {@code Economy} is registered (VaultUnlocked on the live server). No purchase limit, no sign
 * editing - the interact event is always cancelled for any of those blocks. The End
 * sign stays clickable even while the End is disabled, but refuses the sale and takes no money:
 * there would be nothing for the radar to ever point at.
 */
public final class RadarShop implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final Supplier<StationConfig> stationConfig;
    private final Supplier<DragonBallsConfig> config;
    private final RadarItems radarItems;
    private final BallSets sets;

    public RadarShop(JavaPlugin plugin, Supplier<StationConfig> stationConfig, Supplier<DragonBallsConfig> config,
                      RadarItems radarItems, BallSets sets) {
        this.plugin = plugin;
        this.stationConfig = stationConfig;
        this.config = config;
        this.radarItems = radarItems;
        this.sets = sets;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        BallSet set = stationConfig.get().setSoldAt(clicked);
        if (set == null) {
            return;
        }
        event.setCancelled(true);
        purchase(event.getPlayer(), set);
    }

    private void purchase(Player player, BallSet set) {
        if (set == BallSet.END && !sets.isEnabled(BallSet.END)) {
            config.get().messages().send(player, "end-radar-closed");
            return;
        }

        Economy economy = economy();
        if (economy == null) {
            plugin.getLogger().warning("Radar shop: no Vault Economy provider registered; the sign is closed.");
            player.sendMessage(MINI.deserialize("<red>The radar shop is closed."));
            return;
        }

        double price = stationConfig.get().radarSign(set).price();
        String radarName = RadarItems.displayName(set);
        if (!economy.has(player, price)) {
            double balance = economy.getBalance(player);
            player.sendMessage(MINI.deserialize("<red>A " + radarName + " costs " + formatAmount(price)
                    + " coins. You have " + formatAmount(balance) + "."));
            return;
        }

        economy.withdrawPlayer(player, price);
        ItemStack radar = radarItems.create(set);
        var leftover = player.getInventory().addItem(radar);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItem(player.getLocation(), leftover.values().iterator().next());
        }
        player.sendMessage(MINI.deserialize("<gold>You bought a " + radarName + "!"));
    }

    private Economy economy() {
        return VaultEconomy.find(plugin.getServer());
    }

    private static String formatAmount(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
