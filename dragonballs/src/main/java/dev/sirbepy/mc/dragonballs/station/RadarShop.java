package dev.sirbepy.mc.dragonballs.station;

import dev.sirbepy.mc.core.Inventories;
import dev.sirbepy.mc.core.econ.VaultEconomy;
import dev.sirbepy.mc.dragonballs.CoinFormat;
import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.debt.DebtService;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

/**
 * Right-clicking one of the 3 configured radar signs, at the market's Radars shop (the only place
 * the 3 Dragon Radars sell, since the Dragon Ball station's own duplicate copy was retired), buys
 * that set's Dragon Radar through whatever classic Vault {@code Economy} is registered
 * (VaultUnlocked on the live server). No purchase limit, no sign editing - the interact event is
 * always cancelled for any of those blocks. The End sign stays clickable even while the End is
 * disabled, but refuses the sale and takes no money: there would be nothing for the radar to ever
 * point at.
 */
public final class RadarShop implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final Supplier<StationConfig> stationConfig;
    private final Supplier<DragonBallsConfig> config;
    private final RadarItems radarItems;
    private final BallSets sets;
    private final DebtService debt;

    public RadarShop(JavaPlugin plugin, Supplier<StationConfig> stationConfig, Supplier<DragonBallsConfig> config,
                      RadarItems radarItems, BallSets sets, DebtService debt) {
        this.plugin = plugin;
        this.stationConfig = stationConfig;
        this.config = config;
        this.radarItems = radarItems;
        this.sets = sets;
        this.debt = debt;
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

        double owed = debt.debtOf(player.getUniqueId());
        if (owed > 0) {
            player.sendMessage(MINI.deserialize("<red>You owe " + CoinFormat.format(owed) + " and can't buy anything until it's paid off."));
            return;
        }

        double price = stationConfig.get().radarSign(set).price();
        String radarName = RadarItems.displayName(set);
        if (!economy.has(player, price)) {
            double balance = economy.getBalance(player);
            player.sendMessage(MINI.deserialize("<red>A " + radarName + " costs " + CoinFormat.format(price)
                    + " coins. You have " + CoinFormat.format(balance) + "."));
            return;
        }

        economy.withdrawPlayer(player, price);
        Inventories.giveOrDrop(player, radarItems.create(set));
        player.sendMessage(MINI.deserialize("<gold>You bought a " + radarName + "!"));
    }

    private Economy economy() {
        return VaultEconomy.find(plugin.getServer());
    }
}
