package dev.sirbepy.mc.dragonballs.summon.curse;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists wish-bans and timed effect curses to {@code plugins/DragonBalls/curses.yml}, atomically
 * (same {@link YamlFiles} pattern {@code BallRegistry} uses for {@code balls.yml}). Expired effect
 * entries are dropped the moment anything reads or mutates a player's list - this class never scans
 * on a timer; applying the remaining ones to an online player is {@code CurseListener}'s job, driven
 * by join/respawn/command events only.
 */
public final class CurseStore {

    private final File file;
    private final Logger logger;
    private final Map<UUID, List<EffectCurse>> effects = new LinkedHashMap<>();
    private final Set<UUID> wishBanned = new HashSet<>();

    public CurseStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    public boolean isWishBanned(UUID player) {
        return wishBanned.contains(player);
    }

    public void banFromWishing(UUID player) {
        wishBanned.add(player);
        save();
    }

    public void addEffect(UUID player, String effectKey, int amplifier, long expiryMillis) {
        effects.computeIfAbsent(player, id -> new ArrayList<>())
                .add(new EffectCurse(player, effectKey, amplifier, expiryMillis));
        save();
    }

    /** Removes every curse (effect curses + wish ban) for the player; {@code /shenron curse lift}. */
    public void liftAll(UUID player) {
        effects.remove(player);
        wishBanned.remove(player);
        save();
    }

    /** Active (non-expired) effect curses for the player. Expired ones found along the way are
     * dropped from the stored list as a side effect, so the file never accumulates dead entries. */
    public List<EffectCurse> activeEffects(UUID player, long nowMillis) {
        List<EffectCurse> stored = effects.get(player);
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        List<EffectCurse> live = new ArrayList<>();
        boolean droppedAny = false;
        for (EffectCurse curse : stored) {
            if (CurseMath.isExpired(curse.expiryMillis(), nowMillis)) {
                droppedAny = true;
            } else {
                live.add(curse);
            }
        }
        if (droppedAny) {
            if (live.isEmpty()) {
                effects.remove(player);
            } else {
                effects.put(player, live);
            }
            save();
        }
        return live;
    }

    private void load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file + "; starting with no curses", e);
            return;
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection section = players.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            if (section.getBoolean("nowish", false)) {
                wishBanned.add(playerId);
            }
            List<?> rawEffects = section.getList("effects");
            if (rawEffects == null) {
                continue;
            }
            List<EffectCurse> parsed = new ArrayList<>();
            for (Object entry : rawEffects) {
                if (entry instanceof Map<?, ?> map) {
                    Object effectKey = map.get("key");
                    Object amplifier = map.get("amplifier");
                    Object expiry = map.get("expiry");
                    if (effectKey != null && amplifier instanceof Number amplifierNumber && expiry instanceof Number expiryNumber) {
                        parsed.add(new EffectCurse(playerId, effectKey.toString(), amplifierNumber.intValue(), expiryNumber.longValue()));
                    }
                }
            }
            if (!parsed.isEmpty()) {
                effects.put(playerId, parsed);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        Set<UUID> allPlayers = new HashSet<>(wishBanned);
        allPlayers.addAll(effects.keySet());
        for (UUID playerId : allPlayers) {
            String path = "players." + playerId;
            if (wishBanned.contains(playerId)) {
                yaml.set(path + ".nowish", true);
            }
            List<EffectCurse> list = effects.get(playerId);
            if (list != null && !list.isEmpty()) {
                List<Map<String, Object>> serialized = new ArrayList<>();
                for (EffectCurse curse : list) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("key", curse.effectKey());
                    entry.put("amplifier", curse.amplifier());
                    entry.put("expiry", curse.expiryMillis());
                    serialized.add(entry);
                }
                yaml.set(path + ".effects", serialized);
            }
        }
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file + "; a restart could lose recent curse state", e);
        }
    }
}
