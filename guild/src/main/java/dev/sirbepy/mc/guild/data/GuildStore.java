package dev.sirbepy.mc.guild.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    /** Hall of Fame "firsts" (first to D-Rank, first contract...), set once and never overwritten. */
    public record First(UUID id, String name, long time) {
    }

    private final Map<String, First> firsts = new LinkedHashMap<>();
    /** Server-wide state for bounties, Grand Quests and Guildmaster activities, by key. */
    private final YamlConfiguration events = new YamlConfiguration();
    /** Staff id -> {villager, title display, Mannequin}; an id present here means that staff member is placed. */
    private final Map<String, UUID[]> staff = new LinkedHashMap<>();
    /** Staff id -> where they stand, as ImMijo asked for it in the hall he built. */
    private final Map<String, Place> staffSpots = new LinkedHashMap<>();
    private Place.Box guard;
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

    /** The backing map, for {@link GuildStoreYaml} to populate on load. */
    Map<UUID, Member> membersById() {
        return members;
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

    public Map<String, UUID[]> staff() {
        return staff;
    }

    public Map<String, Place> staffSpots() {
        return staffSpots;
    }

    /** The Guild Hall's guarded box, or null while none is set. */
    public Place.Box guard() {
        return guard;
    }

    public void setGuard(Place.Box guard) {
        this.guard = guard;
    }

    public Map<String, First> firsts() {
        return firsts;
    }

    /** Records a first once; true if this call set it. */
    public boolean claimFirst(String key, UUID id, String name, long time) {
        if (firsts.containsKey(key)) {
            return false;
        }
        firsts.put(key, new First(id, name, time));
        return true;
    }

    /** Free-form server-wide event state (bounty, Grand Quest, activity), saved under "events". */
    public YamlConfiguration events() {
        return events;
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
        if (GuildStoreYaml.save(this, file, logger)) {
            dirty = false;
        }
    }

    private void load() {
        GuildStoreYaml.load(this, file, logger);
    }

    int rawNextCommissionId() {
        return nextCommissionId;
    }

    void setNextCommissionId(int nextCommissionId) {
        this.nextCommissionId = nextCommissionId;
    }

    void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
}
