package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.core.YamlFiles;
import dev.sirbepy.mc.endfight.tier.Tier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code rewards.yml}: every player's permanent Dragon Hoard progress ({@link PlayerProgress}) (Joe's
 * reward spec, 2026-10-09). Read-modify-write like {@link PendingRewardsStore} - callers only run
 * on the main thread (a dragon death, a join, a command), so there is no concurrent writer to race.
 */
public final class RewardProgressStore {

    private final File file;
    private final Logger logger;

    public RewardProgressStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public PlayerProgress load(UUID id) {
        YamlConfiguration yaml = readAll();
        ConfigurationSection section = yaml.getConfigurationSection("players." + id);
        if (section == null) {
            return PlayerProgress.empty();
        }
        Set<ArmorPiece> armor = parseEnumSet(section.getStringList("armor"), ArmorPiece.class);
        Set<DragonAura> auras = parseEnumSet(section.getStringList("auras"), DragonAura.class);
        DragonAura active = null;
        String activeRaw = section.getString("active-aura");
        if (activeRaw != null) {
            try {
                active = DragonAura.valueOf(activeRaw);
            } catch (IllegalArgumentException ignored) {
                // a hand-edited or stale value; treat as no aura worn
            }
        }
        Set<String> heads = new LinkedHashSet<>(section.getStringList("heads"));
        Map<Tier, String> weeklyHoards = new EnumMap<>(Tier.class);
        ConfigurationSection weeklySection = section.getConfigurationSection("weekly-hoards");
        if (weeklySection != null) {
            for (String key : weeklySection.getKeys(false)) {
                try {
                    weeklyHoards.put(Tier.valueOf(key), weeklySection.getString(key));
                } catch (IllegalArgumentException ignored) {
                    // a hand-edited or stale tier name; skip it
                }
            }
        }
        return new PlayerProgress(armor, auras, active, heads, weeklyHoards);
    }

    public void save(UUID id, PlayerProgress progress) {
        YamlConfiguration yaml = readAll();
        String base = "players." + id;
        yaml.set(base + ".armor", progress.armor().stream().map(Enum::name).toList());
        yaml.set(base + ".auras", progress.auras().stream().map(Enum::name).toList());
        yaml.set(base + ".active-aura", progress.activeAura() == null ? null : progress.activeAura().name());
        yaml.set(base + ".heads", new ArrayList<>(progress.heads()));
        for (Tier tier : Tier.values()) {
            yaml.set(base + ".weekly-hoards." + tier.name(), progress.weeklyHoards().get(tier));
        }
        save(yaml);
    }

    private static <E extends Enum<E>> Set<E> parseEnumSet(List<String> raw, Class<E> type) {
        EnumSet<E> set = EnumSet.noneOf(type);
        for (String name : raw) {
            try {
                set.add(Enum.valueOf(type, name));
            } catch (IllegalArgumentException ignored) {
                // a hand-edited or stale value; skip it
            }
        }
        return set;
    }

    private YamlConfiguration readAll() {
        try {
            return YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file + "; treating reward progress as empty", e);
            return new YamlConfiguration();
        }
    }

    private void save(YamlConfiguration yaml) {
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file + "; a restart would forget reward progress", e);
        }
    }
}
