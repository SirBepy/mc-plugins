package dev.sirbepy.mc.classes.talent;

import java.util.Locale;

/**
 * Inputs the server can see while a player sneaks, so they can fire an ability. The vanilla client
 * sends nothing for other keys (Ctrl, R, G...), so nothing else can ever be offered.
 */
public enum Bind {
    HOTBAR_1("1"), HOTBAR_2("2"), HOTBAR_3("3"), HOTBAR_4("4"), HOTBAR_5("5"),
    HOTBAR_6("6"), HOTBAR_7("7"), HOTBAR_8("8"), HOTBAR_9("9"),
    SWAP("F"),
    DROP("Q"),
    LEFT_AIR("Left click"),
    RIGHT_AIR("Right click");

    private final String key;

    Bind(String key) {
        this.key = key;
    }

    /** "1", "F", "Left click": what the legend prints in brackets. */
    public String key() {
        return key;
    }

    /** "Sneak + 1". */
    public String label() {
        return "Sneak + " + key;
    }

    /** Clicks collide with missed swings, shields, bows and food, so the binds menu warns. */
    public boolean mayClash() {
        return this == LEFT_AIR || this == RIGHT_AIR;
    }

    public static Bind hotbar(int slotIndex) {
        return values()[slotIndex];
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Bind parse(String text) {
        for (Bind b : values()) {
            if (b.id().equalsIgnoreCase(text)) return b;
        }
        return null;
    }
}
