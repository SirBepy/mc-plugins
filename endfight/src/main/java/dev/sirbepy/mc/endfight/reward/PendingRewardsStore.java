package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.core.YamlFiles;
import dev.sirbepy.mc.endfight.tier.Tier;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code pending-rewards.yml}: a participant offline at the kill gets their XP + Dragon Hoard
 * chain run on their next join instead ({@link #take}), and every fight they missed survives a
 * restart until then - the file is keyed by player UUID, each holding a list of flattened
 * {@link PendingReward} entries.
 */
public final class PendingRewardsStore {

    private final File file;
    private final Logger logger;

    public PendingRewardsStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void add(UUID id, PendingReward reward) {
        YamlConfiguration yaml = readAll();
        List<Map<?, ?>> existing = new ArrayList<>(yaml.getMapList(id.toString()));
        existing.add(toMap(reward));
        yaml.set(id.toString(), existing);
        save(yaml);
    }

    /** Every entry owed to {@code id}, removing them from the file (the caller is about to deliver
     * every one it gets back; nothing stays half-delivered since the whole list is cleared at once). */
    public List<PendingReward> take(UUID id) {
        YamlConfiguration yaml = readAll();
        List<Map<?, ?>> raw = yaml.getMapList(id.toString());
        if (raw.isEmpty()) {
            return List.of();
        }
        List<PendingReward> owed = new ArrayList<>(raw.size());
        for (Map<?, ?> entry : raw) {
            owed.add(fromMap(entry));
        }
        yaml.set(id.toString(), null);
        save(yaml);
        return owed;
    }

    /** Every pending entry across every player, for {@code /endfight rewards status}. */
    public int totalEntries() {
        YamlConfiguration yaml = readAll();
        int total = 0;
        for (String key : yaml.getKeys(false)) {
            total += yaml.getMapList(key).size();
        }
        return total;
    }

    private static Map<String, Object> toMap(PendingReward reward) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("xp", reward.xp());
        PendingReward.HoardSpec hoard = reward.hoard();
        if (hoard != null) {
            Map<String, Object> hoardMap = new LinkedHashMap<>();
            hoardMap.put("tier", hoard.tier().name());
            hoardMap.put("date", hoard.dateLabel());
            hoardMap.put("diamonds", hoard.diamonds());
            hoardMap.put("shells", hoard.shells());
            hoardMap.put("gapples", hoard.gapples());
            hoardMap.put("scrap", hoard.scrap());
            List<Map<String, Object>> rolls = new ArrayList<>();
            for (PendingReward.RollGrant grant : hoard.rolls()) {
                rolls.add(Map.of("kind", grant.kind().name(), "id", grant.id()));
            }
            hoardMap.put("rolls", rolls);
            map.put("hoard", hoardMap);
        }
        return map;
    }

    private static PendingReward fromMap(Map<?, ?> entry) {
        int xp = entry.get("xp") instanceof Number n ? n.intValue() : 0;
        PendingReward.HoardSpec hoard = null;
        if (entry.get("hoard") instanceof Map<?, ?> hoardMap) {
            hoard = hoardFromMap(hoardMap);
        }
        return new PendingReward(xp, hoard);
    }

    private static PendingReward.HoardSpec hoardFromMap(Map<?, ?> hoardMap) {
        Tier tier;
        try {
            tier = Tier.valueOf(String.valueOf(hoardMap.get("tier")));
        } catch (IllegalArgumentException e) {
            return null;
        }
        String dateLabel = String.valueOf(hoardMap.get("date"));
        List<PendingReward.RollGrant> rolls = new ArrayList<>();
        if (hoardMap.get("rolls") instanceof List<?> rawRolls) {
            for (Object rawRoll : rawRolls) {
                if (rawRoll instanceof Map<?, ?> rollMap) {
                    try {
                        RollEngine.Kind kind = RollEngine.Kind.valueOf(String.valueOf(rollMap.get("kind")));
                        rolls.add(new PendingReward.RollGrant(kind, String.valueOf(rollMap.get("id"))));
                    } catch (IllegalArgumentException ignored) {
                        // a hand-edited or stale value; skip it
                    }
                }
            }
        }
        return new PendingReward.HoardSpec(tier, dateLabel, intOf(hoardMap.get("diamonds")), intOf(hoardMap.get("shells")),
                intOf(hoardMap.get("gapples")), intOf(hoardMap.get("scrap")), rolls);
    }

    private static int intOf(Object raw) {
        return raw instanceof Number n ? n.intValue() : 0;
    }

    private YamlConfiguration readAll() {
        try {
            return YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file + "; treating pending rewards as empty", e);
            return new YamlConfiguration();
        }
    }

    private void save(YamlConfiguration yaml) {
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file + "; a restart would forget pending rewards", e);
        }
    }
}
