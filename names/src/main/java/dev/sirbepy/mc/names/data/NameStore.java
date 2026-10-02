package dev.sirbepy.mc.names.data;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** UUID to {@code {color, nick}}, persisted in {@code players.yml} via atomic writes. */
public final class NameStore {

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, String> colors = new ConcurrentHashMap<>();
    private final Map<UUID, String> nicks = new ConcurrentHashMap<>();

    public NameStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public void load() {
        colors.clear();
        nicks.clear();
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load players.yml", e);
            return;
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String uuidString : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidString);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection entry = players.getConfigurationSection(uuidString);
            if (entry == null) {
                continue;
            }
            String color = entry.getString("color");
            if (color != null) {
                colors.put(uuid, color);
            }
            String nick = entry.getString("nick");
            if (nick != null) {
                nicks.put(uuid, nick);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (UUID uuid : colors.keySet()) {
            yaml.set("players." + uuid + ".color", colors.get(uuid));
        }
        for (UUID uuid : nicks.keySet()) {
            yaml.set("players." + uuid + ".nick", nicks.get(uuid));
        }
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save players.yml", e);
        }
    }

    public Optional<String> getColor(UUID player) {
        return Optional.ofNullable(colors.get(player));
    }

    public void setColor(UUID player, String storageKey) {
        colors.put(player, storageKey);
        save();
    }

    public void clearColor(UUID player) {
        colors.remove(player);
        save();
    }

    public Optional<String> getNick(UUID player) {
        return Optional.ofNullable(nicks.get(player));
    }

    public void setNick(UUID player, String nickname) {
        nicks.put(player, nickname);
        save();
    }

    public void clearNick(UUID player) {
        nicks.remove(player);
        save();
    }
}
