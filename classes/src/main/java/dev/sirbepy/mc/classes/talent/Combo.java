package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.classes.RpgClass;
import org.bukkit.Material;

import java.util.List;

/**
 * The three-click spell combos (docs/classes.md, Casting). Every combo starts with the weapon's
 * own first click: left for bows and crossbows, whose right click draws or fires, right for
 * everything else, whose left click swings. Then ability 1 is first-other-first, ability 2
 * first-first-other and the ultimate first-other-other, so with a sword R-L-R, R-R-L, R-L-L and
 * with a bow L-R-L, L-L-R, L-R-R.
 */
public final class Combo {

    public enum Click {
        LEFT("L"), RIGHT("R");

        private final String letter;

        Click(String letter) {
            this.letter = letter;
        }

        public String letter() {
            return letter;
        }

        Click other() {
            return this == LEFT ? RIGHT : LEFT;
        }
    }

    private Combo() {
    }

    /** The clicks that cast {@code slot} when the combo opens with {@code first}. */
    public static List<Click> pattern(Slot slot, Click first) {
        Click other = first.other();
        return switch (slot) {
            case ONE -> List.of(first, other, first);
            case TWO -> List.of(first, first, other);
            case ULTIMATE -> List.of(first, other, other);
        };
    }

    /** The slot three clicks cast, or null (first-first-first casts nothing). */
    public static Slot match(List<Click> clicks) {
        if (clicks.size() != 3) return null;
        for (Slot s : Slot.values()) {
            if (pattern(s, clicks.getFirst()).equals(clicks)) return s;
        }
        return null;
    }

    /** "R-L-R". */
    public static String label(Slot slot, Click first) {
        return letters(pattern(slot, first), 3);
    }

    /** {@code clicks} padded to {@code length} with "?": "R-L-?". */
    public static String letters(List<Click> clicks, int length) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i > 0) out.append('-');
            out.append(i < clicks.size() ? clicks.get(i).letter() : "?");
        }
        return out.toString();
    }

    /** The click a combo opens with while holding {@code weapon}. */
    public static Click first(Material weapon) {
        return weapon == Material.BOW || weapon == Material.CROSSBOW ? Click.LEFT : Click.RIGHT;
    }

    /**
     * The first click {@code c}'s combos show when the player holds none of its weapons: the bow
     * classes' left, everyone else's right.
     */
    public static Click defaultFirst(RpgClass c) {
        return c == RpgClass.RANGER || c == RpgClass.ARTIFICER ? Click.LEFT : Click.RIGHT;
    }

    /** Whether {@code item} casts {@code c}'s combos (Joe, 2026-10-17: class weapons only). */
    public static boolean weapon(RpgClass c, Material item) {
        String name = item.name();
        boolean sword = name.endsWith("_SWORD");
        // endsWith("_AXE") leaves pickaxes out: theirs is "_PICKAXE".
        boolean axe = name.endsWith("_AXE");
        boolean mace = item == Material.MACE;
        return switch (c) {
            case WARRIOR, GUARDIAN -> sword || axe || mace;
            case PALADIN -> sword || mace;
            case ROGUE -> sword;
            case RANGER -> item == Material.BOW || item == Material.CROSSBOW || item == Material.TRIDENT;
            case ARCANIST -> item == Material.BLAZE_ROD;
            case ARTIFICER -> item == Material.CROSSBOW;
        };
    }

    /** "swords, axes or a mace": what {@code c} casts with, for the tree and the class info. */
    public static String weapons(RpgClass c) {
        return switch (c) {
            case WARRIOR, GUARDIAN -> "a sword, axe or mace";
            case PALADIN -> "a sword or mace";
            case ROGUE -> "a sword";
            case RANGER -> "a bow, crossbow or trident";
            case ARCANIST -> "a blaze rod";
            case ARTIFICER -> "a crossbow";
        };
    }
}
