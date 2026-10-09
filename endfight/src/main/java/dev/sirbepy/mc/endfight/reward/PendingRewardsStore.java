package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code pending-rewards.yml}: a participant offline at the kill gets their bundle + XP chain run
 * on their next join instead ({@link #take}), and every fight they missed survives a restart until
 * then - the file is keyed by player UUID, each holding a list of {@code {xp, bundles}} entries.
 */
public final class PendingRewardsStore {

    private final File file;
    private final Logger logger;

    public PendingRewardsStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void add(UUID id, PendingReward reward) {
        YamlConfiguration yaml = readAll();
        List<Map<?, ?>> existing = new ArrayList<>(yaml.getMapList(id.toString()));
        existing.add(Map.of("xp", reward.xp(), "bundles", reward.bundles()));
        yaml.set(id.toString(), existing);
        save(yaml);
    }

    /** Every entry owed to {@code id}, removing them from the file (the caller is about to deliver
     * every one it gets back; nothing stays half-delivered since the whole list is cleared at once). */
    public List<PendingReward> take(UUID id) {
        YamlConfiguration yaml = readAll();
        List<Map<?, ?>> raw = yaml.getMapList(id.toString());
        if (raw.isEmpty()) {
            return List.of();
        }
        List<PendingReward> owed = new ArrayList<>(raw.size());
        for (Map<?, ?> entry : raw) {
            int xp = entry.get("xp") instanceof Number n ? n.intValue() : 0;
            int bundles = entry.get("bundles") instanceof Number n ? n.intValue() : 1;
            owed.add(new PendingReward(xp, bundles));
        }
        yaml.set(id.toString(), null);
        save(yaml);
        return owed;
    }

    /** Every pending entry across every player, for {@code /endfight rewards status}. */
    public int totalEntries() {
        YamlConfiguration yaml = readAll();
        int total = 0;
        for (String key : yaml.getKeys(false)) {
            total += yaml.getMapList(key).size();
        }
        return total;
    }

    private YamlConfiguration readAll() {
        try {
            return YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file + "; treating pending rewards as empty", e);
            return new YamlConfiguration();
        }
    }

    private void save(YamlConfiguration yaml) {
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file + "; a restart would forget pending rewards", e);
        }
    }
}
