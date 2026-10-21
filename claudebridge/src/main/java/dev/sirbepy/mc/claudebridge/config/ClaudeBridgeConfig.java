package dev.sirbepy.mc.claudebridge.config;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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
        messages = new Messages(messagesWithJarFallback(config));
    }

    /**
     * The {@code messages} section with every key it lacks filled in from the jar's own bundled
     * {@code config.yml}. {@code saveDefaultConfig()} never rewrites an existing
     * {@code plugins/ClaudeBridge/config.yml}, so a key a newer jar adds (live's file predates
     * {@code shenron-offline}) would otherwise render as {@code [missing message: ...]}; the file's
     * own values still win. Same approach as Market's {@code MarketConfig}.
     */
    private static ConfigurationSection messagesWithJarFallback(FileConfiguration config) {
        YamlConfiguration merged = new YamlConfiguration();
        try (InputStream stream = ClaudeBridgeConfig.class.getResourceAsStream("/config.yml")) {
            if (stream != null) {
                copyStrings(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getConfigurationSection("messages"), merged);
            }
        } catch (IOException e) {
            // A jar resource stream only throws on close(); everything copied above is kept.
        }
        copyStrings(config.getConfigurationSection("messages"), merged);
        return merged;
    }

    private static void copyStrings(ConfigurationSection from, ConfigurationSection into) {
        if (from == null) {
            return;
        }
        for (String key : from.getKeys(true)) {
            if (from.isString(key)) {
                into.set(key, from.getString(key));
            }
        }
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
