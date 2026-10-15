package dev.sirbepy.mc.classes;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The rules of the wish: picking and changing classes, class XP with its daily cap, and the ability
 * cooldowns. Cooldowns and Battle Cry live in memory only, so a restart makes every ability ready.
 */
public final class Classes {

    /** What the plugin shows players when something happens; tests record it instead. */
    public interface Feedback {
        void levelUp(UUID player, RpgClass c, int level);

        void dailyCapReached(UUID player, int cap);

        /** The class, its XP or today's earnings changed. */
        void changed(UUID player);

        Feedback NONE = new Feedback() {
            @Override
            public void levelUp(UUID player, RpgClass c, int level) {
            }

            @Override
            public void dailyCapReached(UUID player, int cap) {
            }

            @Override
            public void changed(UUID player) {
            }
        };
    }

    public enum Choice { CHOSEN, ALREADY, LOCKED }

    /** Result of picking a second class (Sivra21's dual-classing wish, docs/dragonballs.md). */
    public enum DualChoice { CHOSEN, NOT_GRANTED, ALREADY_SET, SAME_AS_MAIN }

    private final ClassStore store;
    private final LongSupplier now;
    private final ZoneId zone;
    private final int dailyCap;
    private final Duration changeLock;
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Long> battleCryUntil = new HashMap<>();
    private final Map<UUID, Long> lastStandUntil = new HashMap<>();
    /** Players allowed to pick a secondary class at all; everyone else never sees one. */
    private final Set<UUID> dualClassGrants = new HashSet<>();
    private Feedback feedback = Feedback.NONE;

    public Classes(ClassStore store, LongSupplier now, ZoneId zone, int dailyCap, Duration changeLock) {
        this.store = store;
        this.now = now;
        this.zone = zone;
        this.dailyCap = dailyCap;
        this.changeLock = changeLock;
    }

    public void setFeedback(Feedback feedback) {
        this.feedback = feedback;
    }

    public ClassProfile profile(UUID player) {
        ClassProfile p = store.get(player);
        rollDay(p);
        return p;
    }

    public RpgClass active(UUID player) {
        return store.get(player).active;
    }

    /** The active class's level, 0 with no class. */
    public int level(UUID player) {
        return store.get(player).activeLevel();
    }

    /** Replaces the whole dual-class grant list (config-driven; revoking just drops a UUID from it). */
    public void setDualClassGrants(Collection<UUID> players) {
        dualClassGrants.clear();
        dualClassGrants.addAll(players);
    }

    public boolean hasDualClassGrant(UUID player) {
        return dualClassGrants.contains(player);
    }

    /** The granted secondary class, or null with no grant (even if one was picked before revoking). */
    public RpgClass secondary(UUID player) {
        return hasDualClassGrant(player) ? store.get(player).secondary : null;
    }

    /** The secondary class's own level, 0 with none. */
    public int secondaryLevel(UUID player) {
        RpgClass c = secondary(player);
        return c == null ? 0 : store.get(player).level(c);
    }

    /**
     * Picks the permanent secondary class: once set it can never change, including through
     * {@link #unlock} or any other admin repair, since none of them touch {@code secondary}.
     */
    public DualChoice chooseSecondary(UUID player, RpgClass target) {
        if (!hasDualClassGrant(player)) return DualChoice.NOT_GRANTED;
        ClassProfile p = store.get(player);
        if (p.secondary != null) return DualChoice.ALREADY_SET;
        if (target == p.active) return DualChoice.SAME_AS_MAIN;
        p.secondary = target;
        store.markDirty();
        feedback.changed(player);
        return DualChoice.CHOSEN;
    }

    public int dailyCap() {
        return dailyCap;
    }

    public Duration changeLock() {
        return changeLock;
    }

    /** When the player may switch to {@code target}, epoch ms, or 0 if they may now. */
    public long lockedUntil(UUID player, RpgClass target) {
        ClassProfile p = store.get(player);
        if (p.lastPickAt == 0 || target == p.lastPicked) return 0;
        long free = p.lastPickAt + changeLock.toMillis();
        return free > now.getAsLong() ? free : 0;
    }

    public Choice choose(UUID player, RpgClass target) {
        ClassProfile p = store.get(player);
        if (p.active == target) return Choice.ALREADY;
        if (lockedUntil(player, target) != 0) return Choice.LOCKED;
        if (target != p.lastPicked || p.lastPickAt == 0) {
            p.lastPicked = target;
            p.lastPickAt = now.getAsLong();
        }
        p.active = target;
        cooldownUntil.remove(player);
        battleCryUntil.remove(player);
        lastStandUntil.remove(player);
        store.markDirty();
        feedback.changed(player);
        return Choice.CHOSEN;
    }

    /** Back to no class. Picking the same class again later is free; any other waits out the lock. */
    public boolean leave(UUID player) {
        ClassProfile p = store.get(player);
        if (p.active == null) return false;
        p.active = null;
        cooldownUntil.remove(player);
        battleCryUntil.remove(player);
        lastStandUntil.remove(player);
        store.markDirty();
        feedback.changed(player);
        return true;
    }

    /** Adds class XP to the active class, within today's cap; returns what was actually added. */
    public int addXp(UUID player, int amount) {
        ClassProfile p = store.get(player);
        if (p.active == null || amount <= 0) return 0;
        rollDay(p);
        int before = p.xp(p.active);
        int granted = Math.min(amount, Math.min(dailyCap - p.earnedToday, Levels.MAX_XP - before));
        if (granted <= 0) return 0;
        p.xp.put(p.active, before + granted);
        p.earnedToday += granted;
        store.markDirty();
        int oldLevel = Levels.level(before);
        int newLevel = Levels.level(before + granted);
        for (int l = oldLevel + 1; l <= newLevel; l++) feedback.levelUp(player, p.active, l);
        if (p.earnedToday >= dailyCap && !p.capNoticeSent) {
            p.capNoticeSent = true;
            feedback.dailyCapReached(player, dailyCap);
        }
        feedback.changed(player);
        return granted;
    }

    private void rollDay(ClassProfile p) {
        String today = Instant.ofEpochMilli(now.getAsLong()).atZone(zone).toLocalDate().toString();
        if (!today.equals(p.day)) {
            p.day = today;
            p.earnedToday = 0;
            p.capNoticeSent = false;
            store.markDirty();
        }
    }

    public long cooldownLeftMillis(UUID player) {
        return Math.max(0, cooldownUntil.getOrDefault(player, 0L) - now.getAsLong());
    }

    void startCooldown(UUID player, int seconds) {
        cooldownUntil.put(player, now.getAsLong() + seconds * 1000L);
    }

    /** Takes {@code millis} off ability 1's running cooldown (talents like Finisher and Zeal). */
    public void shortenCooldown(UUID player, long millis) {
        Long until = cooldownUntil.get(player);
        if (until != null) cooldownUntil.put(player, until - millis);
    }

    /** For the talent engine, which keeps ranks and binds in the profile but has no store of its own. */
    public void markDirty() {
        store.markDirty();
    }

    public long now() {
        return now.getAsLong();
    }

    void startBattleCry(UUID player, int seconds) {
        battleCryUntil.put(player, now.getAsLong() + seconds * 1000L);
    }

    public boolean battleCryActive(UUID player) {
        return active(player) == RpgClass.WARRIOR && battleCryUntil.getOrDefault(player, 0L) > now.getAsLong();
    }

    void startLastStand(UUID player, int seconds) {
        lastStandUntil.put(player, now.getAsLong() + seconds * 1000L);
    }

    public boolean lastStandActive(UUID player) {
        return active(player) == RpgClass.GUARDIAN && lastStandUntil.getOrDefault(player, 0L) > now.getAsLong();
    }

    // Admin repairs (/class admin), also used by the e2e suite.

    public void setXp(UUID player, RpgClass c, int xp) {
        store.get(player).xp.put(c, Math.max(0, Math.min(Levels.MAX_XP, xp)));
        store.markDirty();
        feedback.changed(player);
    }

    /** Lifts the change lock: the next pick is free, like a first pick. */
    public void unlock(UUID player) {
        store.get(player).lastPickAt = 0;
        store.markDirty();
    }

    /** Lets the player earn today's XP again (testing and repairs). */
    public void resetToday(UUID player) {
        ClassProfile p = store.get(player);
        p.earnedToday = 0;
        p.capNoticeSent = false;
        store.markDirty();
        feedback.changed(player);
    }

    public void readyAbility(UUID player) {
        cooldownUntil.remove(player);
    }
}
