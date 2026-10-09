package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code plugins/EndFight/presence.yml}: which players turned the full-set "Dragon's Presence"
 * aura off (Joe's 2026-10-09 decision: on by default), so only the opt-outs need an entry and
 * everyone else is inferred on. Read-modify-write like {@link RewardProgressStore}: callers only
 * run on the main thread (a toggle command, a menu click), so there is no concurrent writer to race.
 */
public final class DragonPresenceStore {

    private final File file;
    private final Logger logger;

    public DragonPresenceStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public boolean isOff(UUID id) {
        return readOff().contains(id.toString());
    }

    /** Flips the player's preference and persists it; returns whether presence is now on. */
    public boolean toggle(UUID id) {
        Set<String> off = readOff();
        // Was in the off-set (off) -> removing it turns presence on; otherwise adding it turns it off.
        boolean nowOn = off.remove(id.toString());
        if (!nowOn) {
            off.add(id.toString());
        }
        save(off);
        return nowOn;
    }

    private Set<String> readOff() {
        try {
            YamlConfiguration yaml = YamlFiles.load(file);
            return new HashSet<>(yaml.getStringList("off"));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file + "; treating every player's Dragon's Presence as on", e);
            return new HashSet<>();
        }
    }

    private void save(Set<String> off) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("off", new ArrayList<>(off));
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file + "; a restart would forget who turned Dragon's Presence off", e);
        }
    }
}
