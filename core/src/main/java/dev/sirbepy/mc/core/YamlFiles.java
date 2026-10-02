package dev.sirbepy.mc.core;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class YamlFiles {

    private YamlFiles() {
    }

    public static YamlConfiguration load(File file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        if (!file.exists()) {
            return yaml;
        }
        try {
            yaml.load(file);
        } catch (InvalidConfigurationException e) {
            throw new IOException("Corrupt YAML in " + file, e);
        }
        return yaml;
    }

    /**
     * Writes to a sibling temp file, then moves it over the target, so a crash mid-save leaves
     * either the old file or the new one, never a half-written one.
     */
    public static void saveAtomically(YamlConfiguration yaml, File file) throws IOException {
        Path target = file.toPath();
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
