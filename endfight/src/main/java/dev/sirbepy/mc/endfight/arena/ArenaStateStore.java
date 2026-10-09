package dev.sirbepy.mc.endfight.arena;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** {@code state.yml} (de)serialization: {@code built}, {@code layoutVersion} and the 10 pillars,
 * so a restart can restore {@link ArenaRegistry} without loading a single chunk. */
final class ArenaStateStore {

    private final File file;
    private final Logger logger;

    ArenaStateStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    ArenaState load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file + "; starting unbuilt", e);
            return ArenaState.EMPTY;
        }
        if (!file.exists()) {
            return ArenaState.EMPTY;
        }
        boolean built = yaml.getBoolean("built", false);
        int layoutVersion = yaml.getInt("layoutVersion", ArenaState.CURRENT_LAYOUT_VERSION);
        List<Pillar> pillars = new ArrayList<>();
        List<?> rawPillars = yaml.getList("pillars", List.of());
        for (Object raw : rawPillars) {
            if (raw instanceof ConfigurationSection section) {
                pillars.add(readPillar(section));
            } else if (raw instanceof java.util.Map<?, ?> map) {
                YamlConfiguration wrapper = new YamlConfiguration();
                for (var entry : map.entrySet()) {
                    wrapper.set(String.valueOf(entry.getKey()), entry.getValue());
                }
                pillars.add(new Pillar(wrapper.getInt("cx"), wrapper.getInt("cz"), wrapper.getInt("radius"),
                        wrapper.getInt("topY"), wrapper.getInt("surfaceY")));
            }
        }
        Terrain terrain = yaml.isSet("terrain.fortressY")
                ? new Terrain(yaml.getInt("terrain.fortressY"), yaml.getInt("terrain.altarY")) : null;
        return new ArenaState(built, layoutVersion, pillars, terrain);
    }

    private Pillar readPillar(ConfigurationSection section) {
        return new Pillar(section.getInt("cx"), section.getInt("cz"), section.getInt("radius"),
                section.getInt("topY"), section.getInt("surfaceY"));
    }

    void save(ArenaState state) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("built", state.built());
        yaml.set("layoutVersion", state.layoutVersion());
        List<java.util.Map<String, Object>> pillars = new ArrayList<>(state.pillars().size());
        for (Pillar p : state.pillars()) {
            pillars.add(java.util.Map.of(
                    "cx", p.cx(), "cz", p.cz(), "radius", p.radius(), "topY", p.topY(), "surfaceY", p.surfaceY()));
        }
        yaml.set("pillars", pillars);
        if (state.terrain() != null) {
            yaml.set("terrain.fortressY", state.terrain().fortressY());
            yaml.set("terrain.altarY", state.terrain().altarY());
        }
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file + "; a restart would forget the arena is built", e);
        }
    }
}
