package dev.sirbepy.mc.tag.state;

import dev.sirbepy.mc.core.YamlFiles;
import dev.sirbepy.mc.tag.logic.PendingAction;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** Reads and writes {@code plugins/Tag/state.yml}, atomically on every write. */
public final class StateRepository {

    private StateRepository() {
    }

    public static TagState load(File file) throws IOException {
        YamlConfiguration yaml = YamlFiles.load(file);
        TagState state = new TagState();

        String currentIt = yaml.getString("current-it");
        if (currentIt != null) {
            state.currentIt = UUID.fromString(currentIt);
        }
        String lastTagger = yaml.getString("last-tagger");
        if (lastTagger != null) {
            state.lastTagger = UUID.fromString(lastTagger);
        }
        state.tagbackExpiryMillis = yaml.getLong("tagback-expiry", 0L);

        ConfigurationSection statsSection = yaml.getConfigurationSection("stats");
        if (statsSection != null) {
            for (String key : statsSection.getKeys(false)) {
                ConfigurationSection s = statsSection.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                PlayerStats ps = new PlayerStats();
                ps.timesTagged = s.getInt("times-tagged");
                ps.tagsMade = s.getInt("tags-made");
                ps.minutesItOnline = s.getDouble("minutes-it-online");
                ps.minutesItOffline = s.getDouble("minutes-it-offline");
                ps.points = s.getDouble("points");
                state.stats.put(UUID.fromString(key), ps);
            }
        }

        for (String uuid : yaml.getStringList("opted-out")) {
            state.optedOut.add(UUID.fromString(uuid));
        }

        ConfigurationSection pendingSection = yaml.getConfigurationSection("pending");
        if (pendingSection != null) {
            for (String key : pendingSection.getKeys(false)) {
                state.pending.put(UUID.fromString(key), PendingAction.valueOf(pendingSection.getString(key)));
            }
        }

        ConfigurationSection leaderboardSection = yaml.getConfigurationSection("leaderboard");
        if (leaderboardSection != null) {
            String world = leaderboardSection.getString("world");
            if (world != null) {
                state.leaderboardLocation = new TagState.LeaderboardLocation(world,
                        leaderboardSection.getDouble("x"),
                        leaderboardSection.getDouble("y"),
                        leaderboardSection.getDouble("z"));
            }
        }

        return state;
    }

    public static void save(TagState state, File file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();

        if (state.currentIt != null) {
            yaml.set("current-it", state.currentIt.toString());
        }
        if (state.lastTagger != null) {
            yaml.set("last-tagger", state.lastTagger.toString());
        }
        yaml.set("tagback-expiry", state.tagbackExpiryMillis);

        for (Map.Entry<UUID, PlayerStats> entry : state.stats.entrySet()) {
            String base = "stats." + entry.getKey();
            PlayerStats ps = entry.getValue();
            yaml.set(base + ".times-tagged", ps.timesTagged);
            yaml.set(base + ".tags-made", ps.tagsMade);
            yaml.set(base + ".minutes-it-online", ps.minutesItOnline);
            yaml.set(base + ".minutes-it-offline", ps.minutesItOffline);
            yaml.set(base + ".points", ps.points);
        }

        yaml.set("opted-out", state.optedOut.stream().map(UUID::toString).toList());

        for (Map.Entry<UUID, PendingAction> entry : state.pending.entrySet()) {
            yaml.set("pending." + entry.getKey(), entry.getValue().name());
        }

        if (state.leaderboardLocation != null) {
            yaml.set("leaderboard.world", state.leaderboardLocation.world);
            yaml.set("leaderboard.x", state.leaderboardLocation.x);
            yaml.set("leaderboard.y", state.leaderboardLocation.y);
            yaml.set("leaderboard.z", state.leaderboardLocation.z);
        }

        YamlFiles.saveAtomically(yaml, file);
    }
}
