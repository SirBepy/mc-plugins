package dev.sirbepy.mc.haru.data;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code plugins/Haru/data.yml}: Haru's spot and entity ids, plus one {@link OwnerState} per owner
 * UUID (normally just ImMijo; the e2e bot gets its own). Saved atomically after every change.
 */
public final class HaruStore {

    private final File file;
    private final Logger logger;
    private final Map<UUID, OwnerState> owners = new HashMap<>();
    private NpcAnchor anchor;
    private UUID npcId;
    private UUID titleId;
    private boolean dirty;

    public HaruStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    public OwnerState state(UUID owner) {
        return owners.computeIfAbsent(owner, id -> new OwnerState());
    }

    public NpcAnchor anchor() {
        return anchor;
    }

    public void setAnchor(NpcAnchor anchor) {
        this.anchor = anchor;
    }

    public UUID npcId() {
        return npcId;
    }

    public void setNpcId(UUID npcId) {
        this.npcId = npcId;
    }

    public UUID titleId() {
        return titleId;
    }

    public void setTitleId(UUID titleId) {
        this.titleId = titleId;
    }

    private void load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not read " + file + "; starting empty (the file is left untouched until the next save)", e);
            return;
        }
        ConfigurationSection npc = yaml.getConfigurationSection("npc");
        if (npc != null) {
            ConfigurationSection a = npc.getConfigurationSection("anchor");
            if (a != null && a.getString("world") != null) {
                anchor = new NpcAnchor(a.getString("world"), a.getDouble("x"), a.getDouble("y"), a.getDouble("z"),
                        (float) a.getDouble("yaw"));
            }
            npcId = parseUuid(npc.getString("entity"));
            titleId = parseUuid(npc.getString("title"));
        }
        ConfigurationSection ownersSection = yaml.getConfigurationSection("owners");
        if (ownersSection != null) {
            for (String key : ownersSection.getKeys(false)) {
                UUID id = parseUuid(key);
                ConfigurationSection section = ownersSection.getConfigurationSection(key);
                if (id != null && section != null) {
                    owners.put(id, readState(section));
                }
            }
        }
    }

    private static OwnerState readState(ConfigurationSection s) {
        OwnerState state = new OwnerState();
        state.setPetals(s.getInt("petals"));
        state.setReputation(s.getInt("reputation"));
        state.setQuestsCompleted(s.getInt("quests-completed"));
        state.setBoardDate(s.getString("board-date"));
        state.setRerollsUsed(s.getInt("rerolls-used"));
        for (Map<?, ?> entry : s.getMapList("board")) {
            Object id = entry.get("id");
            if (id != null) {
                state.board().add(new ActiveQuest(id.toString(), asInt(entry.get("progress")),
                        Boolean.TRUE.equals(entry.get("claimed"))));
            }
        }
        ConfigurationSection purchases = s.getConfigurationSection("purchases");
        if (purchases != null) {
            for (String offer : purchases.getKeys(false)) {
                state.purchases().put(offer, new OwnerState.Purchase(
                        purchases.getString(offer + ".period", ""), purchases.getInt(offer + ".count")));
            }
        }
        state.unlocks().addAll(s.getStringList("unlocks"));
        state.setActiveAura(s.getString("aura"));
        ConfigurationSection blessings = s.getConfigurationSection("blessings");
        if (blessings != null) {
            for (String effect : blessings.getKeys(false)) {
                state.blessings().put(effect, new OwnerState.Blessing(effect,
                        blessings.getInt(effect + ".amplifier"), blessings.getLong(effect + ".expiry")));
            }
        }
        state.setAnnouncedTier(s.getInt("announced-tier"));
        ConfigurationSection charms = s.getConfigurationSection("charms");
        if (charms != null) {
            for (String charm : charms.getKeys(false)) {
                state.charms().put(charm, charms.getLong(charm));
            }
        }
        for (Object item : s.getList("lost-and-found", List.of())) {
            if (item instanceof ItemStack stack) {
                state.lostAndFound().add(stack);
            }
        }
        state.setClosedUntil(s.getLong("closed-until"));
        return state;
    }

    /** For frequent small changes (quest progress): written by the next {@link #flush}, not right away. */
    public void markDirty() {
        dirty = true;
    }

    public void flush() {
        if (dirty) {
            save();
        }
    }

    public void save() {
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        if (anchor != null) {
            yaml.set("npc.anchor.world", anchor.world());
            yaml.set("npc.anchor.x", anchor.x());
            yaml.set("npc.anchor.y", anchor.y());
            yaml.set("npc.anchor.z", anchor.z());
            yaml.set("npc.anchor.yaw", (double) anchor.yaw());
        }
        if (npcId != null) {
            yaml.set("npc.entity", npcId.toString());
        }
        if (titleId != null) {
            yaml.set("npc.title", titleId.toString());
        }
        for (Map.Entry<UUID, OwnerState> entry : owners.entrySet()) {
            writeState(yaml.createSection("owners." + entry.getKey()), entry.getValue());
        }
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file, e);
        }
    }

    private static void writeState(ConfigurationSection s, OwnerState state) {
        s.set("petals", state.petals());
        s.set("reputation", state.reputation());
        s.set("quests-completed", state.questsCompleted());
        s.set("board-date", state.boardDate());
        s.set("rerolls-used", state.rerollsUsed());
        List<Map<String, Object>> board = new ArrayList<>();
        for (ActiveQuest quest : state.board()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", quest.questId());
            row.put("progress", quest.progress());
            row.put("claimed", quest.claimed());
            board.add(row);
        }
        s.set("board", board);
        for (Map.Entry<String, OwnerState.Purchase> entry : state.purchases().entrySet()) {
            s.set("purchases." + entry.getKey() + ".period", entry.getValue().period());
            s.set("purchases." + entry.getKey() + ".count", entry.getValue().count());
        }
        s.set("unlocks", new ArrayList<>(state.unlocks().stream().sorted().toList()));
        s.set("aura", state.activeAura());
        for (OwnerState.Blessing blessing : state.blessings().values()) {
            s.set("blessings." + blessing.effectKey() + ".amplifier", blessing.amplifier());
            s.set("blessings." + blessing.effectKey() + ".expiry", blessing.expiryMillis());
        }
        s.set("announced-tier", state.announcedTier());
        state.charms().forEach((charm, expiry) -> s.set("charms." + charm, expiry));
        s.set("lost-and-found", new ArrayList<>(state.lostAndFound()));
        s.set("closed-until", state.closedUntil());
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
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
}
