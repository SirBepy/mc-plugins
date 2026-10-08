package dev.sirbepy.mc.tweaks.seagifts;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Each gift's on/off switch, when it was last flipped and whether death switched it off, per owner, in
 * {@code sea-gifts.yml}. On disk because the hourly cooldown counts real time and his switches have to
 * survive a restart. A gift with no record yet is on and free to flip, so the plugin takes over the
 * effects the console gave him at the grant without a gap.
 */
public final class SeaGiftsStore {

    public static final Duration COOLDOWN = Duration.ofHours(1);

    /** {@code remaining} is zero when the flip went through, else how long until it may. */
    public record Flip(boolean on, Duration remaining) {
        public boolean refused() {
            return !remaining.isZero();
        }
    }

    private final File file;
    private final Logger logger;
    private final YamlConfiguration yaml;

    public SeaGiftsStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        YamlConfiguration loaded;
        try {
            loaded = YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not read " + file + "; the sea gifts start on", e);
            loaded = new YamlConfiguration();
        }
        this.yaml = loaded;
    }

    public boolean isOn(UUID owner, SeaGift gift) {
        return yaml.getBoolean(path(owner, gift, "on"), true);
    }

    /** How long until {@code gift} may be flipped; zero when it may now. */
    public Duration wait(UUID owner, SeaGift gift, long nowMillis) {
        if (yaml.getBoolean(path(owner, gift, "death-off"), false)) return Duration.ZERO;
        long readyAt = yaml.getLong(path(owner, gift, "flipped-at"), 0) + COOLDOWN.toMillis();
        return Duration.ofMillis(Math.max(0, readyAt - nowMillis));
    }

    /**
     * Switches {@code gift} the other way unless it was flipped less than an hour ago. Switching back on a
     * gift that death took is never refused (death isn't his flip), but it does start the hour.
     */
    public Flip flip(UUID owner, SeaGift gift, long nowMillis) {
        boolean on = isOn(owner, gift);
        Duration wait = wait(owner, gift, nowMillis);
        if (!wait.isZero()) return new Flip(on, wait);
        yaml.set(path(owner, gift, "on"), !on);
        yaml.set(path(owner, gift, "flipped-at"), nowMillis);
        yaml.set(path(owner, gift, "death-off"), null);
        save();
        return new Flip(!on, Duration.ZERO);
    }

    /** Switches off every gift that was on and returns them. */
    public List<SeaGift> switchOffForDeath(UUID owner) {
        List<SeaGift> taken = new ArrayList<>();
        for (SeaGift gift : SeaGift.values()) {
            if (!isOn(owner, gift)) continue;
            yaml.set(path(owner, gift, "on"), false);
            yaml.set(path(owner, gift, "death-off"), true);
            taken.add(gift);
        }
        if (!taken.isEmpty()) save();
        return taken;
    }

    private static String path(UUID owner, SeaGift gift, String field) {
        return owner + "." + gift.key() + "." + field;
    }

    private void save() {
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file, e);
        }
    }
}
