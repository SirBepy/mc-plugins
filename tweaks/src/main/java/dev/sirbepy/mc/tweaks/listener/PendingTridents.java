package dev.sirbepy.mc.tweaks.listener;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loyalty tridents taken back for an owner who was offline or dead, per owner UUID, in
 * {@code loyalty-tridents.yml}. On disk because the trident entity is already gone when one lands
 * here: kept in memory, a restart before the owner rejoins would delete the item for good. Items are
 * stored as Paper's byte form (base64), which carries the data version, so a trident owed across a
 * Minecraft update is upgraded on read like a player's own inventory.
 */
public final class PendingTridents {

    private final File file;
    private final Logger logger;
    private final YamlConfiguration yaml;

    public PendingTridents(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        YamlConfiguration loaded;
        try {
            loaded = YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not read " + file + "; tridents owed from before this start are not delivered", e);
            loaded = new YamlConfiguration();
        }
        this.yaml = loaded;
    }

    public void add(UUID owner, ItemStack item) {
        List<String> items = new ArrayList<>(yaml.getStringList(owner.toString()));
        items.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        yaml.set(owner.toString(), items);
        save();
    }

    /** Everything owed to {@code owner}, removed from the file; empty when nothing is. */
    public List<ItemStack> take(UUID owner) {
        List<String> encoded = yaml.getStringList(owner.toString());
        if (encoded.isEmpty()) return List.of();
        List<ItemStack> items = new ArrayList<>();
        for (String entry : encoded) {
            items.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(entry)));
        }
        yaml.set(owner.toString(), null);
        save();
        return items;
    }

    private void save() {
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file + "; owed tridents are only in memory until the next restart", e);
        }
    }
}
