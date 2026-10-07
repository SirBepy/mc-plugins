package dev.sirbepy.mc.guild.data;

import dev.sirbepy.mc.core.YamlFiles;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.rank.Rank;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code plugins/Guild/guild.yml}: every member, the Treasury and its ledger, the Guildmaster's
 * commissions and stores. Saved atomically; claims and payments save at once, progress on a timer.
 */
public final class GuildStore {

    private final File file;
    private final Logger logger;
    private final Map<UUID, Member> members = new LinkedHashMap<>();
    private final Treasury treasury = new Treasury();
    private final List<Commission> commissions = new ArrayList<>();
    private final List<ItemStack> stores = new ArrayList<>();
    private int nextCommissionId = 1;
    private String featuredId;
    private long lastAnnouncement;
    private boolean dirty;
    // A file that failed to parse is never overwritten, so a bad hand edit can't wipe every member.
    private boolean readOnly;

    public GuildStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    public Member member(UUID id) {
        return members.get(id);
    }

    public Member register(UUID id, String name, long now) {
        Member member = new Member(id, name);
        member.setJoinedAt(now);
        member.setLastActive(now);
        members.put(id, member);
        return member;
    }

    public Collection<Member> members() {
        return members.values();
    }

    public Treasury treasury() {
        return treasury;
    }

    public List<Commission> commissions() {
        return commissions;
    }

    public Commission commission(String id) {
        return commissions.stream().filter(c -> c.id().equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    public String nextCommissionId() {
        return "c" + nextCommissionId++;
    }

    public List<ItemStack> stores() {
        return stores;
    }

    public String featuredId() {
        return featuredId;
    }

    public void setFeaturedId(String featuredId) {
        this.featuredId = featuredId;
    }

    public long lastAnnouncement() {
        return lastAnnouncement;
    }

    public void setLastAnnouncement(long lastAnnouncement) {
        this.lastAnnouncement = lastAnnouncement;
    }

    public void markDirty() {
        dirty = true;
    }

    public void flush() {
        if (dirty) {
            save();
        }
    }

    public void save() {
        if (readOnly) {
            logger.warning("Not saving " + file + ": it failed to load, fix or move it and reload");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-commission", nextCommissionId);
        yaml.set("featured", featuredId);
        yaml.set("last-announcement", lastAnnouncement);
        writeTreasury(yaml.createSection("treasury"));
        ConfigurationSection membersSection = yaml.createSection("members");
        for (Member member : members.values()) {
            writeMember(membersSection.createSection(member.id().toString()), member);
        }
        List<Map<String, Object>> commissionList = new ArrayList<>();
        for (Commission commission : commissions) {
            commissionList.add(writeCommission(commission));
        }
        yaml.set("commissions", commissionList);
        yaml.set("stores", new ArrayList<>(stores));
        try {
            YamlFiles.saveAtomically(yaml, file);
            dirty = false;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file, e);
        }
    }

    private void load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(file);
        } catch (IOException e) {
            readOnly = true;
            logger.log(Level.SEVERE, "Could not read " + file + "; the Guild starts empty and will not save over it", e);
            return;
        }
        nextCommissionId = Math.max(1, yaml.getInt("next-commission", 1));
        featuredId = yaml.getString("featured");
        lastAnnouncement = yaml.getLong("last-announcement");
        ConfigurationSection t = yaml.getConfigurationSection("treasury");
        if (t != null) {
            readTreasury(t);
        }
        ConfigurationSection membersSection = yaml.getConfigurationSection("members");
        if (membersSection != null) {
            for (String key : membersSection.getKeys(false)) {
                UUID id = parseUuid(key);
                ConfigurationSection section = membersSection.getConfigurationSection(key);
                if (id != null && section != null) {
                    members.put(id, readMember(id, section));
                }
            }
        }
        for (Map<?, ?> entry : yaml.getMapList("commissions")) {
            Commission commission = readCommission(entry);
            if (commission != null) {
                commissions.add(commission);
            }
        }
        for (Object item : yaml.getList("stores", List.of())) {
            if (item instanceof ItemStack stack) {
                stores.add(stack);
            }
        }
    }

    private void writeTreasury(ConfigurationSection s) {
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

    private void readTreasury(ConfigurationSection s) {
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
        s.set("dailies", m.dailies().stream().map(GuildStore::writeActive).toList());
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
        if (id == null || dev.sirbepy.mc.guild.contract.ContractPool.byId(id.toString()) == null) {
            return null;
        }
        int[] progress = new int[0];
        if (map.get("progress") instanceof List<?> list) {
            progress = list.stream().mapToInt(GuildStore::asInt).toArray();
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

    private Commission readCommission(Map<?, ?> map) {
        try {
            List<String> targets = new ArrayList<>();
            if (map.get("targets") instanceof List<?> list) {
                list.forEach(t -> targets.add(String.valueOf(t)));
            }
            Objective objective = new Objective(Objective.Kind.valueOf(String.valueOf(map.get("kind"))),
                    new HashSet<>(targets), asInt(map.get("amount")), String.valueOf(map.get("text")));
            Commission c = new Commission(String.valueOf(map.get("id")), UUID.fromString(String.valueOf(map.get("poster"))),
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
