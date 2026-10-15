package dev.sirbepy.mc.classes.talent;

import org.bukkit.Material;

import java.util.function.IntFunction;

/**
 * One node in a class's tree. {@code branch} is 0-2, {@code tier} 1-5; tier 5 is the branch's
 * keystone, which replaces the ability in {@code replaces}. {@code effect} describes the node at a
 * given rank (1..maxRank) for the menu lore, e.g. rank 2 of Bloodlust: "+8% melee damage to mobs
 * while under half health".
 */
public record Talent(String id, String name, int branch, int tier, int maxRank, Material icon,
                     IntFunction<String> effect, Slot replaces) {

    /** Points a branch needs in lower tiers before a node of tier {@code i} opens (index = tier). */
    static final int[] TIER_GATE = {0, 0, 3, 6, 10, 14};
    /** Keystones also need this class level. */
    public static final int KEYSTONE_LEVEL = 20;
    public static final int KEYSTONE_TIER = 5;

    public Talent {
        if (branch < 0 || branch > 2) throw new IllegalArgumentException(id + ": branch must be 0-2");
        if (tier < 1 || tier > KEYSTONE_TIER) throw new IllegalArgumentException(id + ": tier must be 1-5");
        if (maxRank < 1) throw new IllegalArgumentException(id + ": maxRank must be at least 1");
        if ((tier == KEYSTONE_TIER) != (replaces != null)) {
            throw new IllegalArgumentException(id + ": exactly the tier-5 keystones replace an ability");
        }
    }

    /** A normal node. */
    public static Talent node(String id, String name, int branch, int tier, int maxRank, Material icon,
                              IntFunction<String> effect) {
        return new Talent(id, name, branch, tier, maxRank, icon, effect, null);
    }

    /** A branch's tier-5 keystone, which swaps out {@code replaces} for the kit's keystone ability. */
    public static Talent keystone(String id, String name, int branch, Slot replaces, Material icon, String effect) {
        return new Talent(id, name, branch, KEYSTONE_TIER, 1, icon, rank -> effect, replaces);
    }

    public boolean keystone() {
        return tier == KEYSTONE_TIER;
    }

    public int gate() {
        return TIER_GATE[tier];
    }
}
