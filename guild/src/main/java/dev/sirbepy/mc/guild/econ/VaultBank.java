package dev.sirbepy.mc.guild.econ;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

/** Whatever classic Vault {@code Economy} is registered, looked up on each use so load order can't matter. */
public final class VaultBank implements Bank {

    private final Server server;

    public VaultBank(Server server) {
        this.server = server;
    }

    private Economy economy() {
        var registration = server.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    @Override
    public boolean available() {
        return economy() != null;
    }

    @Override
    public double balance(OfflinePlayer player) {
        Economy economy = economy();
        return economy == null ? 0 : economy.getBalance(player);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        Economy economy = economy();
        return economy != null && economy.has(player, amount) && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        Economy economy = economy();
        return economy != null && economy.depositPlayer(player, amount).transactionSuccess();
    }
}
