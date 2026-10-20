package dev.sirbepy.mc.minibosses.chronicle;

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
 * Each player's place in the Chronicles, in {@code chronicles-progress.yml}: a stage number (see
 * {@link Chronicle} for what each one means) and which Guardians' fragments they have already been
 * handed, so a repeat kill never drops a second copy.
 */
public final class ChronicleProgress {

    private final File file;
    private final Logger logger;
    private YamlConfiguration state;

    public ChronicleProgress(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "chronicles-progress.yml");
        this.logger = logger;
        try {
            state = YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file + "; starting with no Chronicles progress", e);
            state = new YamlConfiguration();
        }
    }

    public int stage(UUID player) {
        return state.getInt(player + ".stage");
    }

    public void setStage(UUID player, int stage) {
        state.set(player + ".stage", stage);
        save();
    }

    public boolean hasFragment(UUID player, String guardianId) {
        return state.getStringList(player + ".fragments").contains(guardianId);
    }

    public void markFragment(UUID player, String guardianId) {
        List<String> given = new ArrayList<>(state.getStringList(player + ".fragments"));
        if (!given.contains(guardianId)) {
            given.add(guardianId);
            state.set(player + ".fragments", given);
            save();
        }
    }

    public void reset(UUID player) {
        state.set(player.toString(), null);
        save();
    }

    private void save() {
        try {
            YamlFiles.saveAtomically(state, file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file, e);
        }
    }
}
