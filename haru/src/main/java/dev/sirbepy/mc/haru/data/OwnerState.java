package dev.sirbepy.mc.haru.data;

import dev.sirbepy.mc.haru.rep.Tier;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything Haru remembers about his owner: Spirit Petals, reputation, today's quest board, what was
 * bought in which stock period, unlocked keepsakes and running blessings.
 * Plain mutable state; {@link HaruStore} persists it.
 */
public final class OwnerState {

    /** How many of one offer were bought, and in which stock period (a new period starts at zero). */
    public record Purchase(String period, int count) {
    }

    /** A timed potion effect Haru granted, re-applied after death or rejoin until it runs out. */
    public record Blessing(String effectKey, int amplifier, long expiryMillis) {
    }

    private int petals;
    private int reputation;
    private int questsCompleted;
    private String boardDate;
    private int rerollsUsed;
    private final List<ActiveQuest> board = new ArrayList<>();
    private final Map<String, Purchase> purchases = new HashMap<>();
    private final Set<String> unlocks = new HashSet<>();
    private String activeAura;
    private final Map<String, Blessing> blessings = new HashMap<>();
    private int announcedTier;
    private final Map<String, Long> charms = new HashMap<>();
    private final List<ItemStack> lostAndFound = new ArrayList<>();
    private long closedUntil;

    public int petals() {
        return petals;
    }

    public void setPetals(int petals) {
        this.petals = Math.max(0, petals);
    }

    public void addPetals(int amount) {
        setPetals(petals + amount);
    }

    public boolean spendPetals(int amount) {
        if (amount > petals) {
            return false;
        }
        petals -= amount;
        return true;
    }

    public int reputation() {
        return reputation;
    }

    public void setReputation(int reputation) {
        this.reputation = Math.max(0, reputation);
    }

    public Tier tier() {
        return Tier.of(reputation);
    }

    public int questsCompleted() {
        return questsCompleted;
    }

    public void setQuestsCompleted(int questsCompleted) {
        this.questsCompleted = questsCompleted;
    }

    public String boardDate() {
        return boardDate;
    }

    public void setBoardDate(String boardDate) {
        this.boardDate = boardDate;
    }

    public int rerollsUsed() {
        return rerollsUsed;
    }

    public void setRerollsUsed(int rerollsUsed) {
        this.rerollsUsed = rerollsUsed;
    }

    public List<ActiveQuest> board() {
        return board;
    }

    public Map<String, Purchase> purchases() {
        return purchases;
    }

    /** Bought in {@code period}; a purchase recorded in any older period no longer counts. */
    public int bought(String offerId, String period) {
        Purchase purchase = purchases.get(offerId);
        return purchase != null && purchase.period().equals(period) ? purchase.count() : 0;
    }

    public void recordPurchase(String offerId, String period) {
        purchases.put(offerId, new Purchase(period, bought(offerId, period) + 1));
    }

    public Set<String> unlocks() {
        return unlocks;
    }

    public boolean has(String unlock) {
        return unlocks.contains(unlock);
    }

    public String activeAura() {
        return activeAura;
    }

    public void setActiveAura(String activeAura) {
        this.activeAura = activeAura;
    }

    public Map<String, Blessing> blessings() {
        return blessings;
    }

    /** Charm id to expiry millis. */
    public Map<String, Long> charms() {
        return charms;
    }

    public boolean charmActive(String id, long nowMillis) {
        return charms.getOrDefault(id, 0L) > nowMillis;
    }

    /** Bound items taken off other players, waiting for the owner at Haru. */
    public List<ItemStack> lostAndFound() {
        return lostAndFound;
    }

    /** Haru's shop stays shut until this time after his owner dies (the dragon's price). */
    public long closedUntil() {
        return closedUntil;
    }

    public void setClosedUntil(long closedUntil) {
        this.closedUntil = closedUntil;
    }

    public boolean closed(long nowMillis) {
        return closedUntil > nowMillis;
    }

    public int announcedTier() {
        return announcedTier;
    }

    public void setAnnouncedTier(int announcedTier) {
        this.announcedTier = announcedTier;
    }
}
