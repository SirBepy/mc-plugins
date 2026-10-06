package dev.sirbepy.mc.dragonballs.ball;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * One of the 3 independent 7-ball sets. Each lives only in its own home dimension and has its own
 * registry, radar, textures, wish count, stone cooldown and last-summon point - only one dragon can
 * be summoned at a time server-wide, but that's a session-manager invariant, not a per-set one.
 * Overworld's display word is empty so its item names, radar name and chat wording stay
 * byte-identical to the single-set plugin that predates this split.
 */
public enum BallSet {
    OVERWORLD("overworld", "", NamedTextColor.GOLD),
    NETHER("nether", "Nether", NamedTextColor.RED),
    END("end", "End", NamedTextColor.LIGHT_PURPLE);

    private final String id;
    private final String displayWord;
    private final NamedTextColor color;

    BallSet(String id, String displayWord, NamedTextColor color) {
        this.id = id;
        this.displayWord = displayWord;
        this.color = color;
    }

    /** The PDC value this set is identified by, and its {@code balls-<id>.yml} / {@code textures.<id>}
     * config path segment (overworld's path segment exists too, even though its file stays plain
     * {@code balls.yml} for legacy compatibility). */
    public String id() {
        return id;
    }

    /** "" for overworld (so its own item names/messages are unaffected), else "Nether"/"End". */
    public String displayWord() {
        return displayWord;
    }

    public NamedTextColor color() {
        return color;
    }

    /** "the Overworld"/"the Nether"/"the End", for prose like "it won't leave <home>." */
    public String homeName() {
        return switch (this) {
            case OVERWORLD -> "the Overworld";
            case NETHER -> "the Nether";
            case END -> "the End";
        };
    }

    public String worldName(DragonBallsConfig config) {
        return switch (this) {
            case OVERWORLD -> config.world();
            case NETHER -> config.netherWorld();
            case END -> config.endWorld();
        };
    }

    /** The loaded home world, or null if it isn't (End before {@code allow-end}, a misconfigured name). */
    public World world(DragonBallsConfig config) {
        return Bukkit.getWorld(worldName(config));
    }

    /** Resolves a PDC-stored id back to its set, or null for an unrecognized value (never thrown:
     * callers treat null the same as "absent" and fall back to {@link #OVERWORLD}). */
    public static BallSet byId(String id) {
        for (BallSet set : values()) {
            if (set.id.equals(id)) {
                return set;
            }
        }
        return null;
    }
}
