package dev.sirbepy.mc.claudebridge.config;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads and holds everything from config.yml. */
public final class ClaudeBridgeConfig {

    private final JavaPlugin plugin;
    private volatile long cooldownSeconds;
    private volatile long onlineThresholdSeconds;
    private volatile Messages messages;

    public ClaudeBridgeConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        cooldownSeconds = config.getLong("cooldown-seconds", 20);
        onlineThresholdSeconds = config.getLong("shenron-online-threshold-seconds", 45);
        messages = new Messages(config.getConfigurationSection("messages"));
    }

    public long cooldownSeconds() {
        return cooldownSeconds;
    }

    public long onlineThresholdSeconds() {
        return onlineThresholdSeconds;
    }

    public Messages messages() {
        return messages;
    }
}
