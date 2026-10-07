package dev.sirbepy.mc.guild.econ;

import org.bukkit.OfflinePlayer;

/** Players' coin balances. The live server's is Essentials behind Vault; tests use an in-memory one. */
public interface Bank {

    /** False while no economy is registered, so the Guild refuses money moves instead of losing them. */
    boolean available();

    double balance(OfflinePlayer player);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);
}
