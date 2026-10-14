package dev.sirbepy.mc.minibosses.registry;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every Guardian is one YAML file under a {@code guardians/} directory (bundled defaults in
 * {@code src/main/resources/guardians/}, copied into the plugin's data folder on first run via
 * {@code JavaPlugin.saveResource}, then runtime-editable there - the same split {@code tweaks}
 * uses for {@code changelog.yml}). Adding a 4th Guardian is "drop a new YAML file here", never a
 * code change to this class.
 *
 * <p>A malformed file (missing id, missing a tier, an unparsable dimension) is logged and skipped
 * rather than crashing the plugin or the rest of the load - one bad Guardian must never take the
 * others down with it.
 */
public final class GuardianRegistry {

    private final File guardiansDir;
    private final Logger logger;

    private volatile Map<String, GuardianDef> guardians = Map.of();

    public GuardianRegistry(File guardiansDir, Logger logger) {
        this.guardiansDir = guardiansDir;
        this.logger = logger;
    }

    /** (Re)loads every {@code *.yml} file directly under the guardians directory. Replaces the
     * whole in-memory table at once; a Guardian removed from disk since the last load is dropped. */
    public void load() {
        Map<String, GuardianDef> loaded = new LinkedHashMap<>();
        File[] files = guardiansDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                parse(file).ifPresent(def -> loaded.put(def.id(), def));
            }
        }
        this.guardians = Map.copyOf(loaded);
    }

    public Optional<GuardianDef> get(String id) {
        return Optional.ofNullable(guardians.get(id));
    }

    public Map<String, GuardianDef> all() {
        return guardians;
    }

    private Optional<GuardianDef> parse(File file) {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read Guardian file " + file + "; skipping it", e);
            return Optional.empty();
        }

        String id = yaml.getString("id");
        if (id == null || id.isBlank()) {
            logger.warning("Guardian file " + file + " has no id; skipping it");
            return Optional.empty();
        }

        World.Environment dimension;
        try {
            dimension = World.Environment.valueOf(yaml.getString("dimension", "").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Guardian '" + id + "' in " + file + " has a missing or unknown dimension; skipping it");
            return Optional.empty();
        }

        ConfigurationSection tiersSection = yaml.getConfigurationSection("tiers");
        Map<Tier, GuardianTierRow> tiers = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.values()) {
            ConfigurationSection section = tiersSection == null ? null
                    : tiersSection.getConfigurationSection(tier.name().toLowerCase(Locale.ROOT));
            if (section == null) {
                logger.warning("Guardian '" + id + "' in " + file + " is missing its " + tier.displayName()
                        + " tier; skipping it");
                return Optional.empty();
            }
            tiers.put(tier, new GuardianTierRow(section.getDouble("health"), section.getDouble("damage"),
                    section.getStringList("abilities"), section.getString("loot-table", "")));
        }

        return Optional.of(new GuardianDef(id, yaml.getString("display-name", id), dimension,
                yaml.getString("description", ""), Map.copyOf(tiers), yaml.getString("summon-hook", "")));
    }
}
