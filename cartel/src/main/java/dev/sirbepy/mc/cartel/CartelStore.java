package dev.sirbepy.mc.cartel;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
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

    /** A trophy head waiting for an offline owner, with the time the hit landed ({@code null} for
     * an entry saved before this stamp existed, which loads back as unstamped = old head), so a
     * delayed delivery still stamps the trophy with its original kill time, not the moment it's
     * finally handed over. */
    public record PendingHead(UUID player, String name, Instant killedAt) {

        public PendingHead(UUID player, String name) {
            this(player, name, null);
        }
    }

    public void addPendingHead(UUID owner, PendingHead head) {
        List<String> heads = new ArrayList<>(yaml.getStringList(owner + ".pending-heads"));
        String killedAt = head.killedAt() == null ? "" : String.valueOf(head.killedAt().toEpochMilli());
        heads.add(head.player() + "|" + head.name() + "|" + killedAt);
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
            // A pre-stamp entry is just "uuid|name" (2 fields): the split's 3rd slot stays absent,
            // same as a 3-field entry whose time was blank, so both load back unstamped.
            String[] parts = entry.split("\\|", 3);
            if (parts.length < 2) {
                logger.warning("Skipping a malformed pending head: " + entry);
                continue;
            }
            try {
                Instant killedAt = parts.length == 3 && !parts[2].isEmpty() ? Instant.ofEpochMilli(Long.parseLong(parts[2])) : null;
                heads.add(new PendingHead(UUID.fromString(parts[0]), parts[1], killedAt));
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
