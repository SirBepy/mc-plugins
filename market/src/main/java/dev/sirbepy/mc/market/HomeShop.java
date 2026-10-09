package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.YamlFiles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sells extra /sethome slots (HuskHomes 4.11) by granting a LuckPerms user node: HuskHomes'
 * LuckPerms hook resolves every {@code huskhomes.max_homes.<n>} node the player holds and takes
 * the highest (javap of HuskHomes' LuckPermsHook: {@code Collections.reverseOrder()} then
 * {@code get(0)}), so granting a higher {@code n} raises the cap; stack_permission_limits is
 * false, so the nodes don't add up. The live default LuckPerms group already holds
 * {@code huskhomes.max_homes.3}, so 3 is everyone's floor; this shop sells 4, 5 and 6 (Joe,
 * 2026-10-09).
 *
 * <p>LuckPerms applies a permission change asynchronously, so a fast double-click could see the
 * player's permissions still at the old value and charge them twice for the same home: this
 * shop's own record of what it already sold them ({@code homes.yml}), not just their current
 * permissions, decides the next price.
 */
public final class HomeShop implements Listener {

    @FunctionalInterface
    public interface Charge {
        /** False when there is no economy, the balance is short, or the withdrawal itself failed. */
        boolean charge(Player player, double amount);
    }

    @FunctionalInterface
    public interface Balance {
        /** The player's balance, or null when no economy is registered. */
        Double balance(Player player);
    }

    @FunctionalInterface
    public interface Refund {
        boolean refund(Player player, double amount);
    }

    /** Grants {@code huskhomes.max_homes.<next>} (and unsets {@code <next - 1>} when the shop was
     * the one that granted it) via LuckPerms console commands; false when there is no LuckPerms to
     * run them (no softdepend registered). */
    @FunctionalInterface
    public interface HomeGrant {
        boolean grant(UUID playerId, int next);
    }

    private static final int LINE_LENGTH = 15;
    // Checked cheaply down from here for the player's own highest granted node: nobody needs more
    // than an admin's blanket .100, and this scan covers that too.
    private static final int PERMISSION_SCAN_MAX = 100;

    private final MarketLayout layout;
    private final Supplier<MarketConfig> config;
    private final Charge charge;
    private final Balance balance;
    private final Refund refund;
    private final HomeGrant grant;
    private final File stateFile;
    private final Logger logger;

    // The highest home count this shop has already sold each player, keyed by uuid; absent means
    // just their LuckPerms-default floor. Guards the LuckPerms-is-async double-charge race above.
    private final Map<UUID, Integer> sold = new HashMap<>();

    public HomeShop(MarketLayout layout, Supplier<MarketConfig> config, Charge charge, Balance balance,
                     Refund refund, HomeGrant grant, File stateFile, Logger logger) {
        this.layout = layout;
        this.config = config;
        this.charge = charge;
        this.balance = balance;
        this.refund = refund;
        this.grant = grant;
        this.stateFile = stateFile;
        this.logger = logger;
        load();
    }

    /** Writes the shop's sign: today's three prices, same look as every other plugin sign. */
    public void apply(World world) {
        MarketLayout.Shop shop = layout.shops().get("homes");
        if (shop == null || shop.sign() == null) {
            return;
        }
        Stall.Slot slot = shop.sign();
        Block block = world.getBlockAt(slot.x(), slot.y(), slot.z());
        Material signBlock = layout.signBlock();
        if (block.getType() != signBlock) {
            block.setType(signBlock, false);
        }
        Sign sign = (Sign) block.getState();
        Directional data = (Directional) sign.getBlockData();
        data.setFacing(slot.facing());
        sign.setBlockData(data);

        MarketConfig cfg = config.get();
        SignSide front = sign.getSide(Side.FRONT);
        front.line(0, Component.text("Extra home").decorate(TextDecoration.BOLD));
        front.line(1, Component.text(truncate(ordinalPrice(cfg, cfg.baseHomes() + 1, "4th"))));
        front.line(2, Component.text(truncate(ordinalPrice(cfg, cfg.baseHomes() + 2, "5th"))));
        front.line(3, Component.text(truncate(ordinalPrice(cfg, cfg.baseHomes() + 3, "6th"))));
        front.setColor(shop.color());
        front.setGlowingText(true);
        sign.update(true, false);
    }

    private static String ordinalPrice(MarketConfig cfg, int n, String ordinal) {
        BigDecimal price = cfg.homePrice(n);
        return price == null ? ordinal + ": -" : ordinal + ": " + money(price);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        MarketLayout.Shop shop = layout.shops().get("homes");
        Stall.Slot sign = shop == null ? null : shop.sign();
        Block block = event.getClickedBlock();
        if (sign == null || block == null || block.getX() != sign.x() || block.getY() != sign.y() || block.getZ() != sign.z()) {
            return;
        }
        event.setCancelled(true);
        purchase(event.getPlayer());
    }

    /** Sells the player their next home slot, if the shop still has one to sell them. */
    void purchase(Player player) {
        var messages = config.get().messages();
        MarketConfig cfg = config.get();
        int current = currentHomes(player, cfg);
        if (current >= cfg.maxHomes()) {
            messages.send(player, "homes-max");
            return;
        }
        int next = current + 1;
        BigDecimal price = cfg.homePrice(next);
        if (!charge.charge(player, price.doubleValue())) {
            Double bal = balance.balance(player);
            if (bal == null) {
                messages.send(player, "homes-closed");
            } else {
                messages.send(player, "homes-poor", Placeholder.unparsed("price", money(price)),
                        Placeholder.unparsed("balance", money(BigDecimal.valueOf(bal))));
            }
            return;
        }
        // Recorded before the grant dispatch, which is why a near-simultaneous second click sees
        // this player already at `next`: the real fix for the LuckPerms-is-async race above.
        record(player.getUniqueId(), next, cfg);
        if (!grant.grant(player.getUniqueId(), next)) {
            refund.refund(player, price.doubleValue());
            record(player.getUniqueId(), current, cfg);
            messages.send(player, "homes-closed");
            return;
        }
        messages.send(player, "homes-success", Placeholder.unparsed("n", String.valueOf(next)));
    }

    /** The highest home count the player already has: their own highest granted permission node,
     * or this shop's own sale record, whichever is higher. */
    private int currentHomes(Player player, MarketConfig cfg) {
        int current = cfg.baseHomes();
        for (int n = PERMISSION_SCAN_MAX; n > cfg.baseHomes(); n--) {
            if (player.hasPermission("huskhomes.max_homes." + n)) {
                current = n;
                break;
            }
        }
        return Math.max(current, sold.getOrDefault(player.getUniqueId(), cfg.baseHomes()));
    }

    private void record(UUID playerId, int n, MarketConfig cfg) {
        if (n <= cfg.baseHomes()) {
            sold.remove(playerId);
        } else {
            sold.put(playerId, n);
        }
        save();
    }

    private static String truncate(String text) {
        return text.length() <= LINE_LENGTH ? text : text.substring(0, LINE_LENGTH);
    }

    private static String money(BigDecimal amount) {
        return "$" + String.format(Locale.US, "%,d", amount.toBigInteger());
    }

    private void load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(stateFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + stateFile + "; starting with no sale records", e);
            return;
        }
        var section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                sold.put(UUID.fromString(key), section.getInt(key));
            } catch (IllegalArgumentException e) {
                logger.warning("Bad uuid " + key + " in " + stateFile + "; skipped");
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        sold.forEach((id, n) -> yaml.set("players." + id, n));
        try {
            YamlFiles.saveAtomically(yaml, stateFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + stateFile + "; a restart would forget today's sales", e);
        }
    }
}
