package dev.sirbepy.mc.cartel;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cooldowns, the one-time phone gift and undelivered trophy heads, per owner, in {@code state.yml}. Kept on disk because a
 * cooldown counted in real days has to survive restarts; writes are rare (one per call), so each
 * change saves at once.
 */
public final class CartelStore {

    private final File file;
    private final Logger logger;
    private final YamlConfiguration yaml;

    public CartelStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        YamlConfiguration loaded;
        try {
            loaded = YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not read " + file + "; cooldowns start fresh", e);
            loaded = new YamlConfiguration();
        }
        this.yaml = loaded;
    }

    public long guardReadyAt(UUID owner) {
        return yaml.getLong(owner + ".guard-ready-at", 0);
    }

    public void setGuardReadyAt(UUID owner, long millis) {
        set(owner + ".guard-ready-at", millis);
    }

    public long hitReadyAt(UUID owner) {
        return yaml.getLong(owner + ".hit-ready-at", 0);
    }

    public void setHitReadyAt(UUID owner, long millis) {
        set(owner + ".hit-ready-at", millis);
    }

    public boolean phoneGiven(UUID owner) {
        return yaml.getBoolean(owner + ".phone-given", false);
    }

    public void setPhoneGiven(UUID owner, boolean given) {
        set(owner + ".phone-given", given);
    }

    /** A trophy head waiting for an offline owner. */
    public record PendingHead(UUID player, String name) {
    }

    public void addPendingHead(UUID owner, PendingHead head) {
        List<String> heads = new ArrayList<>(yaml.getStringList(owner + ".pending-heads"));
        heads.add(head.player() + "|" + head.name());
        set(owner + ".pending-heads", heads);
    }

    /** Every head waiting for {@code owner}, removed from the store. */
    public List<PendingHead> takePendingHeads(UUID owner) {
        List<String> raw = yaml.getStringList(owner + ".pending-heads");
        if (raw.isEmpty()) {
            return List.of();
        }
        List<PendingHead> heads = new ArrayList<>();
        for (String entry : raw) {
            int bar = entry.indexOf('|');
            try {
                heads.add(new PendingHead(UUID.fromString(entry.substring(0, bar)), entry.substring(bar + 1)));
            } catch (RuntimeException e) {
                logger.warning("Skipping a malformed pending head: " + entry);
            }
        }
        set(owner + ".pending-heads", null);
        return heads;
    }

    private void set(String path, Object value) {
        yaml.set(path, value);
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file, e);
        }
    }
}
