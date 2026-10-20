package dev.sirbepy.mc.minibosses.arena;

import dev.sirbepy.mc.core.YamlFiles;
import dev.sirbepy.mc.minibosses.registry.GuardianDef;
import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Each Guardian's arena, the box ImMijo built for it, in {@code arenas.yml}, set by an op with
 * {@code /guardianadmin arena set}. Once a Guardian has an arena it can only be summoned inside it.
 * Before that, an Overworld Guardian can be summoned anywhere and a Nether or End one not at all
 * (Joe, 2026-10-21: only the Sentinel is fightable until the other arenas are built).
 */
public final class ArenaRegistry {

    /** Where a summon attempt stands. */
    public enum Rule {
        /** No arena yet, and this Guardian may be summoned anywhere meanwhile. */
        ANYWHERE,
        /** Inside this Guardian's arena. */
        IN_ARENA,
        /** This Guardian has an arena and the player is not in it. */
        WRONG_PLACE,
        /** No arena yet, and this Guardian waits for one. */
        NOT_READY
    }

    private final File file;
    private final Logger logger;
    private final GuardianRegistry guardians;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaRegistry(File dataFolder, Logger logger, GuardianRegistry guardians) {
        this.file = new File(dataFolder, "arenas.yml");
        this.logger = logger;
        this.guardians = guardians;
        load();
    }

    public Optional<Arena> get(String guardianId) {
        return Optional.ofNullable(arenas.get(guardianId));
    }

    public Map<String, Arena> all() {
        return Map.copyOf(arenas);
    }

    public Rule rule(String guardianId, Location at) {
        Arena arena = arenas.get(guardianId);
        if (arena == null) {
            GuardianDef def = guardians.get(guardianId).orElse(null);
            return def != null && def.dimension() == World.Environment.NORMAL ? Rule.ANYWHERE : Rule.NOT_READY;
        }
        return arena.contains(at) ? Rule.IN_ARENA : Rule.WRONG_PLACE;
    }

    /** The arena whose box holds {@code at}, if any. */
    public Optional<Arena> at(Location at) {
        return arenas.values().stream().filter(arena -> arena.contains(at)).findFirst();
    }

    public void set(Arena arena) {
        arenas.put(arena.guardianId(), arena);
        save();
    }

    public boolean clear(String guardianId) {
        boolean removed = arenas.remove(guardianId) != null;
        save();
        return removed;
    }

    private void load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file + "; no Guardian arenas are set", e);
            return;
        }
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection s = yaml.getConfigurationSection(id);
            World world = s == null ? null : Bukkit.getWorld(s.getString("world", ""));
            if (world == null) {
                logger.warning("Arena for " + id + " names a world that isn't loaded; skipping it.");
                continue;
            }
            arenas.put(id, new Arena(id, world, new BoundingBox(s.getDouble("min.x"), s.getDouble("min.y"),
                    s.getDouble("min.z"), s.getDouble("max.x"), s.getDouble("max.y"), s.getDouble("max.z"))));
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            String id = arena.guardianId();
            BoundingBox box = arena.box();
            yaml.set(id + ".world", arena.world().getName());
            yaml.set(id + ".min.x", box.getMinX());
            yaml.set(id + ".min.y", box.getMinY());
            yaml.set(id + ".min.z", box.getMinZ());
            yaml.set(id + ".max.x", box.getMaxX());
            yaml.set(id + ".max.y", box.getMaxY());
            yaml.set(id + ".max.z", box.getMaxZ());
        }
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file, e);
        }
    }
}
