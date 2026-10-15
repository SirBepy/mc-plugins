package dev.sirbepy.mc.classes.talent;

import java.util.Locale;

/** The three ability slots: the class's own ability, the level-10 second one, the level-25 ultimate. */
public enum Slot {
    ONE(1, "Ability 1"),
    TWO(10, "Ability 2"),
    ULTIMATE(25, "Ultimate");

    /** Every ultimate's cooldown, on top of needing a full resource bar. */
    public static final int ULTIMATE_COOLDOWN_SECONDS = 300;
    /** Abilities 1 and 2 never cool down faster than this, whatever talents say. */
    public static final int MIN_COOLDOWN_SECONDS = 30;

    private final int unlockLevel;
    private final String label;

    Slot(int unlockLevel, String label) {
        this.unlockLevel = unlockLevel;
        this.label = label;
    }

    public int unlockLevel() {
        return unlockLevel;
    }

    public String label() {
        return label;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Slot parse(String text) {
        for (Slot s : values()) {
            if (s.id().equalsIgnoreCase(text) || String.valueOf(s.ordinal() + 1).equals(text)) return s;
        }
        return null;
    }
}
