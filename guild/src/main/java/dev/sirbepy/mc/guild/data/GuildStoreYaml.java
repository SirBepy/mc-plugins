package dev.sirbepy.mc.guild.data;

import dev.sirbepy.mc.core.YamlFiles;
import dev.sirbepy.mc.guild.config.GuildConfig;
import dev.sirbepy.mc.guild.contract.ContractPool;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.rank.Rank;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads and writes {@link GuildStore}'s state to {@code plugins/Guild/guild.yml}. Split out of
 * GuildStore so the model stays free of YAML section/key names; the on-disk shape lives here only.
 */
final class GuildStoreYaml {

    private GuildStoreYaml() {
    }

    static boolean save(GuildStore store, File file, Logger logger) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-commission", store.rawNextCommissionId());
        yaml.set("featured", store.featuredId());
        yaml.set("last-announcement", store.lastAnnouncement());
        writeTreasury(yaml.createSection("treasury"), store.treasury());
        ConfigurationSection membersSection = yaml.createSection("members");
        for (Member member : store.members()) {
            writeMember(membersSection.createSection(member.id().toString()), member);
        }
        List<Map<String, Object>> commissionList = new ArrayList<>();
        for (Commission commission : store.commissions()) {
            commissionList.add(writeCommission(commission));
        }
        yaml.set("commissions", commissionList);
        yaml.set("stores", new ArrayList<>(store.stores()));
        ConfigurationSection firstsSection = yaml.createSection("firsts");
        store.firsts().forEach((key, first) -> {
            ConfigurationSection f = firstsSection.createSection(key);
            f.set("id", first.id().toString());
            f.set("name", first.name());
            f.set("time", first.time());
        });
        for (Map.Entry<String, Object> entry : store.events().getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof ConfigurationSection)) {
                yaml.set("events." + entry.getKey(), entry.getValue());
            }
        }
        ConfigurationSection staffSection = yaml.createSection("staff");
        store.staff().forEach((id, ids) -> {
            ConfigurationSection s = staffSection.createSection(id);
            s.set("villager", ids[0] == null ? null : ids[0].toString());
            s.set("title", ids[1] == null ? null : ids[1].toString());
            Place spot = store.staffSpots().get(id);
            if (spot != null) {
                s.set("world", spot.world());
                s.set("x", spot.x());
                s.set("y", spot.y());
                s.set("z", spot.z());
                s.set("yaw", spot.yaw());
            }
        });
        Place.Box guard = store.guard();
        if (guard != null) {
            ConfigurationSection g = yaml.createSection("hall-guard");
            g.set("world", guard.world());
            g.set("x0", guard.x0());
            g.set("y0", guard.y0());
            g.set("z0", guard.z0());
            g.set("x1", guard.x1());
            g.set("y1", guard.y1());
            g.set("z1", guard.z1());
        }
        try {
            YamlFiles.saveAtomically(yaml, file);
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file, e);
            return false;
        }
    }

    static void load(GuildStore store, File file, Logger logger) {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(file);
        } catch (IOException e) {
            store.setReadOnly(true);
            logger.log(Level.SEVERE, "Could not read " + file + "; the Guild starts empty and will not save over it", e);
            return;
        }
        store.setNextCommissionId(Math.max(1, yaml.getInt("next-commission", 1)));
        store.setFeaturedId(yaml.getString("featured"));
        store.setLastAnnouncement(yaml.getLong("last-announcement"));
        ConfigurationSection t = yaml.getConfigurationSection("treasury");
        if (t != null) {
            readTreasury(t, store.treasury(), logger);
        }
        ConfigurationSection membersSection = yaml.getConfigurationSection("members");
        if (membersSection != null) {
            for (String key : membersSection.getKeys(false)) {
                UUID id = parseUuid(key);
                ConfigurationSection section = membersSection.getConfigurationSection(key);
                if (id != null && section != null) {
                    store.membersById().put(id, readMember(id, section));
                }
            }
        }
        for (Map<?, ?> entry : yaml.getMapList("commissions")) {
            Commission commission = readCommission(entry, logger);
            if (commission != null) {
                store.commissions().add(commission);
            }
        }
        for (Object item : yaml.getList("stores", List.of())) {
            if (item instanceof ItemStack stack) {
                store.stores().add(stack);
            }
        }
        ConfigurationSection firstsSection = yaml.getConfigurationSection("firsts");
        if (firstsSection != null) {
            for (String key : firstsSection.getKeys(false)) {
                ConfigurationSection f = firstsSection.getConfigurationSection(key);
                UUID id = f == null ? null : parseUuid(f.getString("id"));
                if (id != null) {
                    store.firsts().put(key, new GuildStore.First(id, f.getString("name", "?"), f.getLong("time")));
                }
            }
        }
        ConfigurationSection eventsSection = yaml.getConfigurationSection("events");
        if (eventsSection != null) {
            for (Map.Entry<String, Object> entry : eventsSection.getValues(true).entrySet()) {
                if (!(entry.getValue() instanceof ConfigurationSection)) {
                    store.events().set(entry.getKey(), entry.getValue());
                }
            }
        }
        ConfigurationSection g = yaml.getConfigurationSection("hall-guard");
        if (g != null && g.getString("world") != null) {
            store.setGuard(Place.Box.of(g.getString("world"), g.getInt("x0"), g.getInt("y0"), g.getInt("z0"),
                    g.getInt("x1"), g.getInt("y1"), g.getInt("z1")));
        }
        ConfigurationSection staffSection = yaml.getConfigurationSection("staff");
        if (staffSection != null) {
            for (String id : staffSection.getKeys(false)) {
                ConfigurationSection s = staffSection.getConfigurationSection(id);
                if (s != null) {
                    store.staff().put(id, new UUID[] {parseUuid(s.getString("villager")), parseUuid(s.getString("title"))});
                    if (s.getString("world") != null) {
                        store.staffSpots().put(id, new Place(s.getString("world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                                (float) s.getDouble("yaw")));
                    }
                }
            }
        }
    }

    private static void writeTreasury(ConfigurationSection s, Treasury treasury) {
        s.set("balance", treasury.balance());
        s.set("total-in", treasury.totalIn());
        s.set("total-out", treasury.totalOut());
        s.set("withdraw-day", treasury.withdrawDay());
        s.set("withdrawn-today", treasury.rawWithdrawnToday());
        List<Map<String, Object>> ledger = new ArrayList<>();
        for (LedgerEntry entry : treasury.ledger()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("time", entry.time());
            map.put("kind", entry.kind().name());
            map.put("amount", entry.amount());
            map.put("who", entry.who());
            map.put("balance", entry.balanceAfter());
            ledger.add(map);
        }
        s.set("ledger", ledger);
    }

    private static void readTreasury(ConfigurationSection s, Treasury treasury, Logger logger) {
        List<LedgerEntry> entries = new ArrayList<>();
        for (Map<?, ?> map : s.getMapList("ledger")) {
            try {
                entries.add(new LedgerEntry(asLong(map.get("time")), LedgerEntry.Kind.valueOf(String.valueOf(map.get("kind"))),
                        asLong(map.get("amount")), String.valueOf(map.get("who")), asLong(map.get("balance"))));
            } catch (IllegalArgumentException e) {
                logger.warning("Skipping an unreadable ledger line: " + map);
            }
        }
        treasury.restore(s.getLong("balance"), s.getLong("total-in"), s.getLong("total-out"),
                s.getString("withdraw-day"), s.getLong("withdrawn-today"), entries);
    }

    private static void writeMember(ConfigurationSection s, Member m) {
        s.set("name", m.name());
        s.set("rank", m.rank().name());
        s.set("reputation", m.reputation());
        s.set("contracts", m.contractsDone());
        s.set("joined", m.joinedAt());
        s.set("last-active", m.lastActive());
        s.set("board-day", m.boardDay());
        s.set("dailies", m.dailies().stream().map(GuildStoreYaml::writeActive).toList());
        s.set("previous-dailies", new ArrayList<>(m.previousDailies()));
        s.set("rerolls", m.rerollsUsed());
        s.set("board-week", m.boardWeek());
        s.set("weekly", m.weekly() == null ? null : writeActive(m.weekly()));
        s.set("earn-day", m.earnDay());
        s.set("coins-today", m.coinsToday());
        s.set("levels-today", m.levelsToday());
        s.set("trial", m.trial() == null ? null : writeActive(m.trial()));
        s.set("trial-started", m.trialStarted());
        s.set("trial-retry-after", m.trialRetryAfter());
        s.set("stats", new LinkedHashMap<>(m.stats()));
        s.set("titles", new ArrayList<>(m.titles()));
        s.set("active-title", m.activeTitle());
        s.set("last-claim-day", m.lastClaimDay());
        s.set("streak", m.streak());
        s.set("weekly-drawn", m.weeklyDrawnAt());
        s.set("structures-seen", new ArrayList<>(m.structuresSeen()));
        s.set("biomes-seen", new ArrayList<>(m.biomesSeen()));
    }

    private static Member readMember(UUID id, ConfigurationSection s) {
        Member m = new Member(id, s.getString("name", id.toString()));
        m.setRank(Rank.parse(s.getString("rank"), Rank.E));
        m.setReputation(s.getInt("reputation"));
        m.setContractsDone(s.getInt("contracts"));
        m.setJoinedAt(s.getLong("joined"));
        m.setLastActive(s.getLong("last-active"));
        m.setBoardDay(s.getString("board-day"));
        for (Map<?, ?> map : s.getMapList("dailies")) {
            ActiveContract active = readActive(map);
            if (active != null) {
                m.dailies().add(active);
            }
        }
        m.previousDailies().addAll(s.getStringList("previous-dailies"));
        m.setRerollsUsed(s.getInt("rerolls"));
        m.setBoardWeek(s.getString("board-week"));
        ConfigurationSection weekly = s.getConfigurationSection("weekly");
        if (weekly != null) {
            m.setWeekly(readActive(weekly.getValues(false)));
        }
        m.setEarnDay(s.getString("earn-day"));
        m.setCoinsToday(s.getInt("coins-today"));
        m.setLevelsToday(s.getInt("levels-today"));
        ConfigurationSection trial = s.getConfigurationSection("trial");
        if (trial != null) {
            m.setTrial(readActive(trial.getValues(false)));
        }
        m.setTrialStarted(s.getLong("trial-started"));
        m.setTrialRetryAfter(s.getLong("trial-retry-after"));
        ConfigurationSection stats = s.getConfigurationSection("stats");
        if (stats != null) {
            for (String key : stats.getKeys(false)) {
                m.stats().put(key, stats.getInt(key));
            }
        }
        m.titles().addAll(s.getStringList("titles"));
        m.setActiveTitle(s.getString("active-title"));
        m.setLastClaimDay(s.getString("last-claim-day"));
        m.setStreak(s.getInt("streak"));
        m.setWeeklyDrawnAt(s.getLong("weekly-drawn"));
        m.structuresSeen().addAll(s.getStringList("structures-seen"));
        m.biomesSeen().addAll(s.getStringList("biomes-seen"));
        return m;
    }

    private static Map<String, Object> writeActive(ActiveContract a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", a.defId());
        map.put("rank", a.rewardRank().name());
        List<Integer> progress = new ArrayList<>();
        for (int value : a.progress()) {
            progress.add(value);
        }
        map.put("progress", progress);
        map.put("claimed", a.claimed());
        return map;
    }

    /** Null for a contract this jar no longer knows, so a removed contract simply drops off the board. */
    private static ActiveContract readActive(Map<?, ?> map) {
        Object id = map.get("id");
        if (id == null || ContractPool.byId(id.toString()) == null) {
            return null;
        }
        int[] progress = new int[0];
        if (map.get("progress") instanceof List<?> list) {
            progress = list.stream().mapToInt(GuildStoreYaml::asInt).toArray();
        }
        return new ActiveContract(id.toString(), Rank.parse(String.valueOf(map.get("rank")), Rank.E), progress,
                Boolean.TRUE.equals(map.get("claimed")));
    }

    private static Map<String, Object> writeCommission(Commission c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.id());
        map.put("poster", c.poster().toString());
        map.put("kind", c.objective().kind().name());
        map.put("targets", new ArrayList<>(c.objective().targets()));
        map.put("amount", c.objective().amount());
        map.put("text", c.objective().text());
        map.put("reward", c.reward());
        map.put("min-rank", c.minRank().name());
        map.put("posted", c.postedAt());
        map.put("week", c.week());
        map.put("status", c.status().name());
        if (c.claimedBy() != null) {
            map.put("claimed-by", c.claimedBy().toString());
            map.put("claimed-name", c.claimedName());
        }
        Map<String, Object> progress = new LinkedHashMap<>();
        c.allProgress().forEach((id, value) -> progress.put(id.toString(), value));
        map.put("progress", progress);
        return map;
    }

    private static Commission readCommission(Map<?, ?> map, Logger logger) {
        try {
            List<String> targets = new ArrayList<>();
            if (map.get("targets") instanceof List<?> list) {
                list.forEach(t -> targets.add(String.valueOf(t)));
            }
            Objective objective = new Objective(Objective.Kind.valueOf(String.valueOf(map.get("kind"))),
                    new HashSet<>(targets), asInt(map.get("amount")), String.valueOf(map.get("text")));
            // Commissions saved before the poster was recorded could only have come from ImMijo.
            UUID poster = parseUuid(map.get("poster") == null ? null : String.valueOf(map.get("poster")));
            Commission c = new Commission(String.valueOf(map.get("id")), poster == null ? GuildConfig.IMMIJO : poster,
                    objective, asInt(map.get("reward")),
                    Rank.parse(String.valueOf(map.get("min-rank")), Rank.D), asLong(map.get("posted")),
                    String.valueOf(map.get("week")));
            c.setStatus(Commission.Status.valueOf(String.valueOf(map.get("status"))));
            Object by = map.get("claimed-by");
            if (by != null) {
                c.setClaimed(UUID.fromString(by.toString()), String.valueOf(map.get("claimed-name")));
            }
            if (map.get("progress") instanceof Map<?, ?> progress) {
                progress.forEach((k, v) -> {
                    UUID id = parseUuid(String.valueOf(k));
                    if (id != null) {
                        c.setProgress(id, asInt(v));
                    }
                });
            }
            return c;
        } catch (IllegalArgumentException e) {
            logger.warning("Skipping an unreadable commission: " + map);
            return null;
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int asInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
