package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code /market linkdiscord <id>}: a player's own Discord numeric user id, so {@link DealAnnouncer}
 * can post a real {@code <@id>} mention when a watched item is a great deal, instead of the plain
 * {@code @MinecraftName} text it used before (Joe, 2026-10-19: that text never actually notified
 * anyone, Discord only pings on a genuine mention). Persisted to {@code discord-links.yml}, the same
 * {@link YamlFiles} helper {@code watches.yml} uses, so a restart doesn't lose anyone's link.
 */
public final class MarketDiscordLinks {

    private final File stateFile;
    private final Logger logger;
    private final Map<UUID, String> links = new HashMap<>();

    public MarketDiscordLinks(File stateFile, Logger logger) {
        this.stateFile = stateFile;
        this.logger = logger;
        load();
    }

    /** This player's linked Discord user id, or null if they've never run {@code /market linkdiscord}. */
    public String idOf(UUID playerId) {
        return links.get(playerId);
    }

    public void link(UUID playerId, String discordId) {
        links.put(playerId, discordId);
        save();
    }

    private void load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(stateFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + stateFile + "; starting with no Discord links", e);
            return;
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                links.put(UUID.fromString(key), players.getString(key));
            } catch (IllegalArgumentException e) {
                logger.warning("Bad player id " + key + " in " + stateFile + "; skipped");
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        links.forEach((id, discordId) -> yaml.set("players." + id, discordId));
        try {
            YamlFiles.saveAtomically(yaml, stateFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + stateFile + "; a restart would lose everyone's Discord link", e);
        }
    }
}
