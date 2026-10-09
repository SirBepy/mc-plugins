package dev.sirbepy.mc.core.econ;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;

/** Finds whatever classic Vault {@code Economy} a server has registered (VaultUnlocked, live). */
public final class VaultEconomy {

    private VaultEconomy() {
    }

    /**
     * Looked up fresh on each call, never cached, so a caller holding this class across plugin
     * load order (Vault registering its provider after the caller's plugin enables) still sees it.
     */
    public static Economy find(Server server) {
        var registration = server.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }
}
