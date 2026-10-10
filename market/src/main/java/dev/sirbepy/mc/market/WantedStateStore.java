package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code wanted.yml}: WantedBoard's own period/target/claim state, split out of WantedBoard so that
 * class stays the listener and orchestration, this the persistence. Same keys WantedBoard always
 * saved, including the old-format fallback {@link #load} applies.
 */
final class WantedStateStore {

    private final File stateFile;
    private final Logger logger;

    WantedStateStore(File stateFile, Logger logger) {
        this.stateFile = stateFile;
        this.logger = logger;
    }

    /** The saved state, or null when there is none yet (a fresh server) or the file can't be read. */
    WantedState load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(stateFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + stateFile + ", picking a new Wanted target", e);
            return null;
        }
        String day = yaml.getString("period");
        if (day == null) {
            return null;
        }
        boolean cooldown = yaml.getBoolean("cooldown", false);
        UUID targetId = uuid(yaml.getString("target-id"));
        String targetName = yaml.getString("target-name");
        // A file from before the time-alive bounty has a fixed price and no factor: factor 1, and
        // played true so its target isn't denied a survivor reward that board never tracked.
        double factor = targetId == null ? 0 : yaml.getDouble("factor", 1.0);
        Instant postedAt = targetId == null ? null : instant(yaml.getString("posted-at"));
        boolean played = yaml.getBoolean("played", true);
        Long diedStreakTicks = yaml.isSet("died-streak-ticks") ? yaml.getLong("died-streak-ticks") : null;
        UUID claimedBy = uuid(yaml.getString("claimed-by"));
        String claimedByName = yaml.getString("claimed-by-name");
        return new WantedState(day, cooldown, targetId, targetName, factor, postedAt, played, diedStreakTicks,
                claimedBy, claimedByName);
    }

    void save(WantedState state) {
        YamlConfiguration yaml = new YamlConfiguration();
        if (state != null) {
            yaml.set("period", state.period());
            yaml.set("cooldown", state.cooldown());
            if (state.hasTarget()) {
                yaml.set("target-id", state.targetId().toString());
                yaml.set("target-name", state.targetName());
                yaml.set("factor", state.factor());
                yaml.set("posted-at", state.postedAt().toString());
                yaml.set("played", state.played());
                if (state.died()) {
                    yaml.set("died-streak-ticks", state.diedStreakTicks());
                }
            }
            if (state.claimed()) {
                yaml.set("claimed-by", state.claimedBy().toString());
                yaml.set("claimed-by-name", state.claimedByName());
            }
        }
        try {
            YamlFiles.saveAtomically(yaml, stateFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + stateFile + "; a restart would pick again", e);
        }
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant instant(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
