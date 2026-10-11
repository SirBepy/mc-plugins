package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.YamlFiles;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Starter Shop Boost (Joe, 2026-10-11): a player whose all-time peak net worth has never
 * crossed {@code starter-boost.net-worth-threshold} sells for a multiplier at the spawn stalls,
 * starting at {@code max-multiplier} and tapering smoothly to 1x as their lifetime bonus from it
 * approaches {@code bonus-cap} - after that, selling pays exactly what the sign/stall says, same as
 * any other player. Net worth only ever rises here, so a veteran who's currently broke never
 * qualifies, even though they'd pass the threshold check today.
 *
 * <p>The Heads stall pays through our own code already, so its sale amount is known exactly and
 * {@link #onSale} is called directly. The five Essentials [Sell] stalls pay through Essentials' own
 * code, not ours, so this reads the player's balance either side of a click at one of their sign
 * slots to learn what a sale actually paid - {@code onInteractBefore} runs at LOW priority, before
 * Essentials' own handler, {@code onInteractAfter} at MONITOR, after it.
 */
public final class StarterBoost implements Listener {

    /** Pays a player a bonus top-up; false when no economy took the money. */
    @FunctionalInterface
    public interface Payout {
        boolean pay(Player player, double amount);
    }

    private final Market market;
    private final Supplier<MarketConfig> config;
    private final Function<Player, Double> balance;
    private final Payout payout;
    private final File stateFile;
    private final Logger logger;

    private final Map<UUID, BigDecimal> peakNetWorth = new HashMap<>();
    private final Map<UUID, BigDecimal> bonusPaid = new HashMap<>();
    // A sell-sign click's balance just before Essentials processes it; compared against the
    // balance after, in onInteractAfter. Removed either way, so a click never leaks an entry.
    private final Map<UUID, Double> preSaleBalance = new HashMap<>();

    public StarterBoost(Market market, Supplier<MarketConfig> config, Function<Player, Double> balance,
                         Payout payout, File stateFile, Logger logger) {
        this.market = market;
        this.config = config;
        this.balance = balance;
        this.payout = payout;
        this.stateFile = stateFile;
        this.logger = logger;
        load();
    }

    /** Applies the boost to a sale the caller already paid in full (the Heads stall). */
    public void onSale(Player player, double baseAmount) {
        observe(player, balance.apply(player));
        award(player, baseAmount);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractBefore(PlayerInteractEvent event) {
        if (!config.get().starterBoostEnabled() || !isSellSlot(event)) {
            return;
        }
        Double before = balance.apply(event.getPlayer());
        if (before != null) {
            preSaleBalance.put(event.getPlayer().getUniqueId(), before);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractAfter(PlayerInteractEvent event) {
        if (!config.get().starterBoostEnabled() || !isSellSlot(event)) {
            return;
        }
        Double before = preSaleBalance.remove(event.getPlayer().getUniqueId());
        Double after = balance.apply(event.getPlayer());
        observe(event.getPlayer(), after);
        if (before == null || after == null || after <= before) {
            return;
        }
        award(event.getPlayer(), after - before);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        observe(event.getPlayer(), balance.apply(event.getPlayer()));
    }

    private boolean isSellSlot(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return false;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return false;
        }
        for (Stall stall : market.layout().stalls()) {
            for (Stall.Slot slot : stall.slots()) {
                if (slot.x() == block.getX() && slot.y() == block.getY() && slot.z() == block.getZ()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Records the highest balance ever observed for this player: the eligibility floor, and it
     * only ever goes up, so spending back down never re-qualifies a former veteran. */
    private void observe(Player player, Double currentBalance) {
        if (currentBalance == null) {
            return;
        }
        BigDecimal value = BigDecimal.valueOf(currentBalance);
        BigDecimal peak = peakNetWorth.get(player.getUniqueId());
        if (peak == null || value.compareTo(peak) > 0) {
            peakNetWorth.put(player.getUniqueId(), value);
            save();
        }
    }

    /** Pays the boosted top-up for a base sale amount already paid, and tells the player. */
    private void award(Player player, double baseAmount) {
        MarketConfig cfg = config.get();
        if (!cfg.starterBoostEnabled() || baseAmount <= 0) {
            return;
        }
        BigDecimal peak = peakNetWorth.getOrDefault(player.getUniqueId(), BigDecimal.ZERO);
        if (peak.compareTo(cfg.starterBoostNetWorthThreshold()) >= 0) {
            return;
        }
        BigDecimal cap = cfg.starterBoostBonusCap();
        BigDecimal paidSoFar = bonusPaid.getOrDefault(player.getUniqueId(), BigDecimal.ZERO);
        BigDecimal remaining = cap.subtract(paidSoFar);
        if (remaining.signum() <= 0) {
            return;
        }
        double multiplier = multiplier(cfg, paidSoFar, cap);
        BigDecimal bonus = BigDecimal.valueOf(baseAmount * (multiplier - 1)).setScale(2, RoundingMode.HALF_UP).min(remaining);
        if (bonus.signum() <= 0) {
            return;
        }
        if (!payout.pay(player, bonus.doubleValue())) {
            return;
        }
        bonusPaid.put(player.getUniqueId(), paidSoFar.add(bonus));
        observe(player, balance.apply(player));
        save();
        cfg.messages().send(player, "starter-boost-sale", Placeholder.unparsed("bonus", PriceList.money(bonus)),
                Placeholder.unparsed("multiplier", String.format(Locale.ROOT, "%.1f", multiplier)));
    }

    /** {@code maxMultiplier} at no bonus paid yet, tapering smoothly to 1x right at the cap. */
    private static double multiplier(MarketConfig cfg, BigDecimal paidSoFar, BigDecimal cap) {
        if (cap.signum() <= 0) {
            return 1.0;
        }
        double remainingShare = Math.clamp(1 - paidSoFar.doubleValue() / cap.doubleValue(), 0.0, 1.0);
        return 1 + (cfg.starterBoostMaxMultiplier() - 1) * remainingShare;
    }

    private void load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(stateFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + stateFile + "; starting with no Starter Boost history", e);
            return;
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                logger.warning("Bad player id " + key + " in " + stateFile + "; skipped");
                continue;
            }
            ConfigurationSection section = players.getConfigurationSection(key);
            putIfPresent(peakNetWorth, id, section.getString("peak-net-worth"));
            putIfPresent(bonusPaid, id, section.getString("bonus-paid"));
        }
    }

    private void putIfPresent(Map<UUID, BigDecimal> into, UUID id, String value) {
        if (value == null) {
            return;
        }
        try {
            into.put(id, new BigDecimal(value));
        } catch (NumberFormatException e) {
            logger.warning("Bad Starter Boost value '" + value + "' for " + id + " in " + stateFile + "; skipped");
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (UUID id : peakNetWorth.keySet()) {
            yaml.set("players." + id + ".peak-net-worth", peakNetWorth.get(id).toPlainString());
            BigDecimal paid = bonusPaid.get(id);
            if (paid != null) {
                yaml.set("players." + id + ".bonus-paid", paid.toPlainString());
            }
        }
        try {
            YamlFiles.saveAtomically(yaml, stateFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + stateFile + "; a restart would lose Starter Boost history", e);
        }
    }
}
