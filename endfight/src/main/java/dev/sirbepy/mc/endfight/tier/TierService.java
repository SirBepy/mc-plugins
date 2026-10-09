package dev.sirbepy.mc.endfight.tier;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The live {@link TierChoice}: which tier the altar has picked for the NEXT fight ({@link #next()},
 * persisted, default {@link Tier#FLEDGELING}), and which tier is locked in for whichever fight is
 * currently in progress once a dragon has actually spawned ({@link #lockForSpawn()}) - a choice
 * made at the altar while a dragon is alive only ever takes effect on the one after it (Joe,
 * 2026-10-09). Persisted to {@code plugins/EndFight/tier.yml} so a restart mid-fight keeps both.
 *
 * <p>{@link #firstKillEver()} stays true until EITHER this plugin's own persisted {@code
 * kill-recorded} flag OR {@link EndDragonBattleHistory} says a dragon has died here - either
 * signal remembering a kill is enough, so losing one of them (a corrupted {@code tier.yml}, or an
 * End world recreated from a backup that predates the flag) never un-forces the first-kill tier for the
 * first fight twice.
 */
public final class TierService implements TierChoice {

    private final File file;
    private final Tier defaultTier;
    private final EndDragonBattleHistory battleHistory;
    private final Logger logger;

    private volatile Tier nextChoice;
    private volatile Tier activeTier;
    private volatile boolean killRecorded;

    public TierService(File file, Tier defaultTier, EndDragonBattleHistory battleHistory, Logger logger) {
        this.file = file;
        this.defaultTier = defaultTier;
        this.nextChoice = defaultTier;
        this.battleHistory = battleHistory;
        this.logger = logger;
        load();
    }

    @Override
    public Tier current() {
        if (activeTier != null) {
            return activeTier;
        }
        return firstKillEver() ? Tier.FIRST_KILL : nextChoice;
    }

    @Override
    public boolean firstKillEver() {
        return !killRecorded && !battleHistory.hasBeenPreviouslyKilled();
    }

    /** The altar's live pick for whichever fight hasn't locked in yet - what the menu shows as
     * "chosen for the next fight". */
    public Tier next() {
        return nextChoice;
    }

    /** {@code /endfight tier set} and the altar menu. {@code who} is unused for now (kept so a
     * later phase can log/announce who picked without this method's shape changing). */
    public void setNext(Tier tier, Player who) {
        this.nextChoice = tier;
        save();
    }

    /**
     * A fresh dragon just spawned: snapshots the tier this fight is held at (forced
     * {@link Tier#FIRST_KILL} if no dragon has ever died here yet, regardless of what the altar
     * says) and returns it so the caller can push it to DKE/the fallback. {@link #current()}
     * returns this locked value for the rest of the fight even if the altar's {@link #next()}
     * pick changes mid-fight.
     */
    public Tier lockForSpawn() {
        Tier locked = firstKillEver() ? Tier.FIRST_KILL : nextChoice;
        this.activeTier = locked;
        save();
        return locked;
    }

    /**
     * A dragon actually died in a real fight (the tier module's own filter, independent of
     * {@code reward/}'s {@code DragonDeathListener} - this module doesn't touch that package):
     * from now on {@link #firstKillEver()} is false even if {@link EndDragonBattleHistory} is
     * ever wrong, missing, or unavailable (no End world loaded at the time).
     */
    public void recordKill() {
        this.killRecorded = true;
        save();
    }

    private void load() {
        try {
            YamlConfiguration yaml = YamlFiles.load(file);
            nextChoice = parseTier(yaml.getString("next"), defaultTier);
            activeTier = parseTier(yaml.getString("active"), null);
            killRecorded = yaml.getBoolean("kill-recorded", false);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file + "; starting the altar's tier choice from defaults", e);
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next", nextChoice.name());
        yaml.set("active", activeTier == null ? null : activeTier.name());
        yaml.set("kill-recorded", killRecorded);
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file + "; a restart would forget the altar's tier choice", e);
        }
    }

    private static Tier parseTier(String name, Tier fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return Tier.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
