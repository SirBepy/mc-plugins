package dev.sirbepy.mc.minibosses.progress;

import dev.sirbepy.mc.core.YamlFiles;
import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every player's own Guardian kill counts, per Guardian and tier, in {@code kills.yml}. The Hall
 * of Fame only keeps two server-wide first-record slots, so anything that asks "has this player
 * beaten X" (tier unlocks, the Chronicles' chapter gates) reads this instead. A kill credits every
 * fighter who hurt the Guardian, the same set the Hall of Fame and Guild contracts already credit.
 */
public final class GuardianKills {

    private final File file;
    private final Logger logger;
    private YamlConfiguration state;

    public GuardianKills(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "kills.yml");
        this.logger = logger;
        try {
            state = YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file + "; starting with no recorded kills", e);
            state = new YamlConfiguration();
        }
    }

    private static String path(UUID player, String guardianId, Tier tier) {
        return player + "." + guardianId + "." + tier.name().toLowerCase();
    }

    public void record(String guardianId, Tier tier, Collection<UUID> fighters) {
        for (UUID fighter : fighters) {
            String path = path(fighter, guardianId, tier);
            state.set(path, state.getInt(path) + 1);
        }
        try {
            YamlFiles.saveAtomically(state, file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file, e);
        }
    }

    public int count(UUID player, String guardianId, Tier tier) {
        return state.getInt(path(player, guardianId, tier));
    }

    /** True once {@code player} has helped kill this Guardian at {@code tier} or any harder one. */
    public boolean beaten(UUID player, String guardianId, Tier atLeast) {
        for (Tier tier : Tier.values()) {
            if (tier.ordinal() >= atLeast.ordinal() && count(player, guardianId, tier) > 0) {
                return true;
            }
        }
        return false;
    }
}
