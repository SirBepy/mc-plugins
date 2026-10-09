package dev.sirbepy.mc.endfight.fight;

import java.util.Locale;

/**
 * The mob groups a fight honour goes to (10ca's Fight Honours spec, Joe decided 2026-10-09): one
 * honour per group for the player with the most kills of it that fight. A new wave type needs a
 * group here, or its kills count for no honour.
 */
public enum HonourGroup {
    /** The illager cult; its ravagers are the cult's beasts, so they count here too. */
    CULTISTS("Cultists"),
    PIGLINS("Piglins"),
    PHANTOMS("Phantoms"),
    PARATROOPERS("Paratroopers");

    private final String displayName;

    HonourGroup(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** The group a cult type name ({@code CultMarker.type}) belongs to, or null if none. */
    public static HonourGroup of(String type) {
        if (type == null) {
            return null;
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "pillager", "vindicator", "evoker", "illusioner", "ravager" -> CULTISTS;
            case "piglin", "piglin_brute" -> PIGLINS;
            case "phantom" -> PHANTOMS;
            case "paratrooper" -> PARATROOPERS;
            default -> null;
        };
    }
}
