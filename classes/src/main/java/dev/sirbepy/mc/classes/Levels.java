package dev.sirbepy.mc.classes;

/** The class XP curve: level 1 to 2 takes 50 XP, each level 10 more, 3960 XP from 1 to 25. */
public final class Levels {

    public static final int MAX = 25;
    public static final int MAX_XP = totalFor(MAX);

    private Levels() {
    }

    /** XP needed to go from {@code level} to the next one. */
    public static int toNext(int level) {
        return 40 + 10 * level;
    }

    /** Total XP at which {@code level} starts. */
    public static int totalFor(int level) {
        int total = 0;
        for (int l = 1; l < level; l++) total += toNext(l);
        return total;
    }

    public static int level(int xp) {
        int level = 1;
        while (level < MAX && xp >= totalFor(level + 1)) level++;
        return level;
    }

    /** A value that grows evenly from {@code atOne} at level 1 to {@code atMax} at level 25. */
    public static double scale(int level, double atOne, double atMax) {
        int clamped = Math.max(1, Math.min(MAX, level));
        return atOne + (atMax - atOne) * (clamped - 1) / (MAX - 1);
    }
}
