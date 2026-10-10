package dev.sirbepy.mc.classes;

import java.util.EnumMap;
import java.util.Map;

/** One player's classes: the active one (null = none), each class's XP, and today's earnings. */
public final class ClassProfile {

    RpgClass active;
    /** The class of the last pick that started a 7-day lock; going back to it is always free. */
    RpgClass lastPicked;
    /** When that pick happened, epoch ms; 0 = never picked. */
    long lastPickAt;
    final Map<RpgClass, Integer> xp = new EnumMap<>(RpgClass.class);
    /** The day (ISO date in the configured zone) {@link #earnedToday} counts for. */
    String day = "";
    int earnedToday;
    boolean capNoticeSent;

    public RpgClass active() {
        return active;
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
