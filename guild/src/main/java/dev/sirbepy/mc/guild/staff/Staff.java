package dev.sirbepy.mc.guild.staff;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Villager;

/** The Guild Hall's staff: who they are, how they look, and the spot id in {@code hall.yml}. */
public enum Staff {
    RECEPTIONIST("receptionist", "Lyra Fennwick", "Guild Receptionist", Villager.Profession.LIBRARIAN, Villager.Type.PLAINS,
            TextColor.color(0xF2B8C6)),
    CLERK("clerk", "Bram Ashgrove", "Quest Clerk", Villager.Profession.CARTOGRAPHER, Villager.Type.TAIGA,
            TextColor.color(0xD9A066)),
    TREASURER("treasurer", "Silas Coinwright", "Guild Treasurer", Villager.Profession.CLERIC, Villager.Type.SAVANNA,
            TextColor.color(0xF4D35E)),
    EXAMINER("examiner", "Captain Maren Holt", "Trial Examiner", Villager.Profession.WEAPONSMITH, Villager.Type.SNOW,
            TextColor.color(0xC0C8D0)),
    ARCHIVIST("archivist", "Pip Quillfeather", "Guild Archivist", Villager.Profession.LIBRARIAN, Villager.Type.SWAMP,
            TextColor.color(0x9CC9A1));

    private final String id;
    private final String displayName;
    private final String title;
    private final Villager.Profession profession;
    private final Villager.Type type;
    private final TextColor color;

    Staff(String id, String displayName, String title, Villager.Profession profession, Villager.Type type, TextColor color) {
        this.id = id;
        this.displayName = displayName;
        this.title = title;
        this.profession = profession;
        this.type = type;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** First name, for chat lines. */
    public String shortName() {
        String[] parts = displayName.split(" ");
        return parts[0].equals("Captain") ? "Captain " + parts[parts.length - 1] : parts[0];
    }

    public String title() {
        return title;
    }

    public Villager.Profession profession() {
        return profession;
    }

    public Villager.Type type() {
        return type;
    }

    public TextColor color() {
        return color;
    }

    public static Staff byId(String id) {
        for (Staff staff : values()) {
            if (staff.id.equals(id)) {
                return staff;
            }
        }
        return null;
    }
}
