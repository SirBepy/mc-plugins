package dev.sirbepy.mc.minibosses;

import org.bukkit.plugin.java.JavaPlugin;

public final class MinibossesPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Minibosses ready (scaffold only, no bosses yet).");
    }

    @Override
    public void onDisable() {
        getLogger().info("Minibosses disabled.");
    }
}
