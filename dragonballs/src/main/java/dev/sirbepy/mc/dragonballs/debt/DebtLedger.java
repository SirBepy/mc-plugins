package dev.sirbepy.mc.dragonballs.debt;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists each player's debt to {@code plugins/DragonBalls/debt.yml}, atomically (same
 * {@link YamlFiles} pattern {@code CurseStore} uses for {@code curses.yml}). Flat amount, no
 * interest, no deadline (Joe): {@link #charge} only ever adds to the balance, and only
 * {@link #payDown} ever reduces it - there is no side ledger of "how it was collected", a player's
 * current debt is the whole state.
 */
public final class DebtLedger implements DebtService {

    private final File file;
    private final Logger logger;
    private final Map<UUID, Double> debts = new LinkedHashMap<>();

    public DebtLedger(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    @Override
    public double debtOf(UUID playerId) {
        return debts.getOrDefault(playerId, 0.0);
    }

    @Override
    public void charge(UUID playerId, double amount) {
        if (amount <= 0) {
            return;
        }
        debts.merge(playerId, amount, Double::sum);
        save();
    }

    /**
     * Pays down up to {@code amount} of {@code playerId}'s debt; returns whatever is left over once
     * debt reaches 0 (e.g. an incoming deposit bigger than what was owed), which the caller (the
     * Economy decorator, {@link DebtPaydownEconomy}) credits to the player's real balance instead.
     * A no-op (returns {@code amount} unchanged) when the player owes nothing, or for a non-positive
     * amount.
     */
    public double payDown(UUID playerId, double amount) {
        if (amount <= 0) {
            return amount;
        }
        double owed = debtOf(playerId);
        if (owed <= 0) {
            return amount;
        }
        double paid = Math.min(owed, amount);
        double remaining = owed - paid;
        if (remaining <= 0) {
            debts.remove(playerId);
        } else {
            debts.put(playerId, remaining);
        }
        save();
        return amount - paid;
    }

    private void load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file + "; starting with no debts", e);
            return;
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            double amount = players.getDouble(key + ".amount", 0);
            if (amount > 0) {
                debts.put(playerId, amount);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Double> entry : debts.entrySet()) {
            yaml.set("players." + entry.getKey() + ".amount", entry.getValue());
        }
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file + "; a restart could lose recent debt changes", e);
        }
    }
}
