package dev.sirbepy.mc.classes;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.Locale;

/**
 * The seven classes of the wish and every number they use. Each value goes from its level-1 figure
 * to its level-25 figure in even steps ({@link Levels#scale}). The caps from the wish: passives at
 * most +10%, cooldowns never under 30 s, everything below AuraSkills' own comparable stats and
 * abilities (docs/classes.md compares them), and half strength against players ({@link #VS_PLAYERS}).
 * Guardian, Rogue and Paladin were added later, via GutGr1nder's own Nether Shenron wish cashing in
 * the terms the council promised him when the first four were built (docs/classes.md).
 */
public enum RpgClass {

    WARRIOR("Warrior", NamedTextColor.RED, Material.IRON_SWORD,
            "Weapon Master", "melee damage",
            "Battle Cry", 90, 60,
            "Heavy Hands", "arrows and tridents deal 10% less damage"),
    RANGER("Ranger", NamedTextColor.GREEN, Material.BOW,
            "Steady Aim", "arrow and trident damage",
            "Fleet Foot", 90, 60,
            "Thin Skin", "take 5% more damage from mobs' melee hits"),
    ARCANIST("Arcanist", NamedTextColor.LIGHT_PURPLE, Material.AMETHYST_SHARD,
            "Insight", "experience from orbs",
            "Mend", 120, 60,
            "Frail Body", "1 heart less max health"),
    ARTIFICER("Artificer", NamedTextColor.GOLD, Material.IRON_PICKAXE,
            "Careful Hands", "chance a tool or armour piece takes no durability damage",
            "Overclock", 90, 60,
            "Soft Hands", "melee damage 5% lower"),
    GUARDIAN("Guardian", NamedTextColor.AQUA, Material.SHIELD,
            "Stalwart", "melee damage reduction from mobs",
            "Last Stand", 90, 60,
            "Heavy Plate", "5% slower movement speed"),
    ROGUE("Rogue", NamedTextColor.DARK_GRAY, Material.FEATHER,
            "Sneak Attack", "melee damage while sneaking",
            "Shadow Step", 90, 60,
            "Light Fingers", "take 5% more damage from mobs' ranged attacks"),
    PALADIN("Paladin", NamedTextColor.YELLOW, Material.GOLDEN_SWORD,
            "Blessed Vigor", "healing received from any source",
            "Smite", 90, 60,
            "Merciful Hand", "melee damage 5% lower");

    /** Every class modifier counts half when the other side is a player, so PvP stays fair. */
    public static final double VS_PLAYERS = 0.5;

    /** Passive bonus at level 1 and at level 25, in percent. */
    public static final double PASSIVE_MIN = 2.0;
    public static final double PASSIVE_MAX = 10.0;

    // Weakness sizes, in percent (Frail Body and Heavy Plate are flat attribute modifiers instead).
    public static final double HEAVY_HANDS = 10.0;
    public static final double THIN_SKIN = 5.0;
    public static final double SOFT_HANDS = 5.0;
    public static final double FRAIL_BODY_HP = 2.0;
    public static final double LIGHT_FINGERS = 5.0;
    public static final double MERCIFUL_HAND = 5.0;
    public static final double HEAVY_PLATE_SPEED = 5.0;

    private final String displayName;
    private final TextColor color;
    private final Material icon;
    private final String passiveName;
    private final String passiveWhat;
    private final String abilityName;
    private final int cooldownAtOne;
    private final int cooldownAtMax;
    private final String weaknessName;
    private final String weaknessWhat;

    RpgClass(String displayName, TextColor color, Material icon, String passiveName, String passiveWhat,
             String abilityName, int cooldownAtOne, int cooldownAtMax, String weaknessName, String weaknessWhat) {
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
        this.passiveName = passiveName;
        this.passiveWhat = passiveWhat;
        this.abilityName = abilityName;
        this.cooldownAtOne = cooldownAtOne;
        this.cooldownAtMax = cooldownAtMax;
        this.weaknessName = weaknessName;
        this.weaknessWhat = weaknessWhat;
    }

    public String displayName() {
        return displayName;
    }

    /** "a Warrior", "an Arcanist". */
    public String withArticle() {
        return ("AEIOU".indexOf(displayName.charAt(0)) >= 0 ? "an " : "a ") + displayName;
    }

    public TextColor color() {
        return color;
    }

    public Material icon() {
        return icon;
    }

    public String abilityName() {
        return abilityName;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The class whose {@link #id()} or display name is {@code text}, or null. */
    public static RpgClass parse(String text) {
        for (RpgClass c : values()) {
            if (c.id().equalsIgnoreCase(text) || c.displayName.equalsIgnoreCase(text)) return c;
        }
        return null;
    }

    /** The passive's bonus in percent at {@code level}. */
    public double passivePercent(int level) {
        return Levels.scale(level, PASSIVE_MIN, PASSIVE_MAX);
    }

    public int cooldownSeconds(int level) {
        return (int) Math.round(Levels.scale(level, cooldownAtOne, cooldownAtMax));
    }

    // Ability sizes. Warrior: Battle Cry's damage cut in percent and its length. Ranger: Speed I
    // length. Arcanist: health healed. Artificer: Haste I length. Guardian: Last Stand's length.
    // Rogue: Shadow Step's dash distance. Paladin: Smite's damage to the hostile mob hit.

    public static double battleCryPercent(int level) {
        return Levels.scale(level, 10, 20);
    }

    /** Last Stand's damage cut, same size and duration as Battle Cry (both are timed, self-only). */
    public static double lastStandPercent(int level) {
        return Levels.scale(level, 10, 20);
    }

    /** Ability length in seconds (Battle Cry, Fleet Foot, Overclock, Last Stand); 0 when instant. */
    public int abilitySeconds(int level) {
        return (int) Math.round(switch (this) {
            case WARRIOR, GUARDIAN -> Levels.scale(level, 6, 10);
            case RANGER -> Levels.scale(level, 8, 15);
            case ARCANIST, ROGUE, PALADIN -> 0;
            case ARTIFICER -> Levels.scale(level, 10, 20);
        });
    }

    public static double mendHealth(int level) {
        return Levels.scale(level, 2, 6);
    }

    /** Shadow Step's dash distance in blocks. */
    public static double shadowStepBlocks(int level) {
        return Levels.scale(level, 3, 6);
    }

    /** Smite's damage to the nearest hostile mob hit, in health points (2 per heart). */
    public static double smiteDamage(int level) {
        return Levels.scale(level, 4, 12);
    }

    public String passiveLine(int level) {
        return passiveName + ": +" + percent(passivePercent(level)) + " " + passiveWhat;
    }

    public String abilityLine(int level) {
        String what = switch (this) {
            case WARRIOR -> "take " + percent(battleCryPercent(level)) + " less damage for " + abilitySeconds(level) + " s";
            case RANGER -> "Speed I for " + abilitySeconds(level) + " s";
            case ARCANIST -> "heal " + hearts(mendHealth(level)) + " and cure poison";
            case ARTIFICER -> "Haste I for " + abilitySeconds(level) + " s";
            case GUARDIAN -> "take " + percent(lastStandPercent(level)) + " less damage for " + abilitySeconds(level) + " s";
            case ROGUE -> "dash " + (int) Math.round(shadowStepBlocks(level)) + " blocks";
            case PALADIN -> "deal " + hearts(smiteDamage(level)) + " of damage to the nearest hostile mob and cure poison and wither";
        };
        return abilityName + ": " + what + " (every " + cooldownSeconds(level) + " s)";
    }

    public String weaknessLine() {
        return weaknessName + ": " + weaknessWhat;
    }

    static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    static String hearts(double health) {
        double hearts = health / 2;
        String number = hearts == Math.rint(hearts) ? String.valueOf((int) hearts) : String.format(Locale.ROOT, "%.1f", hearts);
        return number + (hearts == 1 ? " heart" : " hearts");
    }
}
