package dev.sirbepy.mc.classes;

import dev.sirbepy.mc.classes.talent.Bind;
import dev.sirbepy.mc.classes.talent.Slot;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** One player's classes: the active one (null = none), each class's XP, and today's earnings. */
public final class ClassProfile {

    RpgClass active;
    /** Sivra21's dual-classing wish grant (docs/dragonballs.md): picked once, never again. */
    RpgClass secondary;
    /** The class of the last pick that started a 7-day lock; going back to it is always free. */
    RpgClass lastPicked;
    /** When that pick happened, epoch ms; 0 = never picked. */
    long lastPickAt;
    final Map<RpgClass, Integer> xp = new EnumMap<>(RpgClass.class);
    /** The day (ISO date in the configured zone) {@link #earnedToday} counts for. */
    String day = "";
    int earnedToday;
    boolean capNoticeSent;
    /** Spent talent ranks per class, talent id to rank; every class keeps its own tree. */
    final Map<RpgClass, Map<String, Integer>> tree = new EnumMap<>(RpgClass.class);
    /** The player's own key binds; a slot missing here uses the server default. Shared by every class. */
    final Map<Slot, Bind> binds = new EnumMap<>(Slot.class);

    /** {@code c}'s spent ranks, live: the talent engine edits it in place, then marks the store dirty. */
    public Map<String, Integer> tree(RpgClass c) {
        return tree.computeIfAbsent(c, k -> new HashMap<>());
    }

    /** The player's own binds, live, like {@link #tree}. */
    public Map<Slot, Bind> binds() {
        return binds;
    }

    public RpgClass active() {
        return active;
    }

    /** The raw stored pick, regardless of whether the dual-class grant is still active. */
    public RpgClass secondary() {
        return secondary;
    }

    public int xp(RpgClass c) {
        return xp.getOrDefault(c, 0);
    }

    public int level(RpgClass c) {
        return Levels.level(xp(c));
    }

    /** The active class's level, or 0 with no class. */
    public int activeLevel() {
        return active == null ? 0 : level(active);
    }

    public int earnedToday() {
        return earnedToday;
    }
}
