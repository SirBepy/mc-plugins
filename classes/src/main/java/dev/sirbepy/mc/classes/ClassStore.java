package dev.sirbepy.mc.classes;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** players.yml: every player who ever picked a class. Saved on change by a timer and on disable. */
public final class ClassStore {

    private final File file;
    private final Logger log;
    private final Map<UUID, ClassProfile> profiles = new HashMap<>();
    private boolean dirty;

    public ClassStore(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /** The player's profile; a player who never picked gets an empty one that isn't saved until it changes. */
    public ClassProfile get(UUID player) {
        return profiles.computeIfAbsent(player, id -> new ClassProfile());
    }

    public void markDirty() {
        dirty = true;
    }

    public void load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(file);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not read " + file + "; nobody has a class until it's fixed", e);
            return;
        }
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection s = yaml.getConfigurationSection(key);
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (s == null) continue;
            ClassProfile p = get(id);
            p.active = RpgClass.parse(s.getString("active", ""));
            p.lastPicked = RpgClass.parse(s.getString("last-picked", ""));
            p.lastPickAt = s.getLong("last-pick-at");
            ConfigurationSection xp = s.getConfigurationSection("xp");
            if (xp != null) {
                for (String c : xp.getKeys(false)) {
                    RpgClass rpg = RpgClass.parse(c);
                    if (rpg != null) p.xp.put(rpg, Math.max(0, Math.min(Levels.MAX_XP, xp.getInt(c))));
                }
            }
            p.day = s.getString("day", "");
            p.earnedToday = s.getInt("earned-today");
            p.capNoticeSent = s.getBoolean("cap-notice-sent");
        }
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, ClassProfile> e : profiles.entrySet()) {
            ClassProfile p = e.getValue();
            if (p.lastPickAt == 0 && p.active == null && p.xp.isEmpty()) continue;
            String k = e.getKey().toString();
            yaml.set(k + ".active", p.active == null ? null : p.active.id());
            yaml.set(k + ".last-picked", p.lastPicked == null ? null : p.lastPicked.id());
            yaml.set(k + ".last-pick-at", p.lastPickAt);
            p.xp.forEach((c, v) -> yaml.set(k + ".xp." + c.id(), v));
            yaml.set(k + ".day", p.day);
            yaml.set(k + ".earned-today", p.earnedToday);
            yaml.set(k + ".cap-notice-sent", p.capNoticeSent);
        }
        try {
            YamlFiles.saveAtomically(yaml, file);
            dirty = false;
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not save " + file, e);
        }
    }
}
