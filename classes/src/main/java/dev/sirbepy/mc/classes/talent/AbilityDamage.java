package dev.sirbepy.mc.classes.talent;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The one way class abilities hurt something. It refuses players and tamed pets outright (rule 1
 * of docs/classes.md: ability damage only ever hits mobs), credits the caster so kills drop loot
 * and count as theirs, and flags the hit so the class damage modifiers ({@code ClassCombat}, melee
 * talents) leave the ability's own number alone.
 */
public final class AbilityDamage {

    // Main-thread only, like every damage event; a depth count so a kill that chains into another
    // ability hit (Inferno, Contagion) stays flagged until the outermost one returns.
    private static int depth;

    private AbilityDamage() {
    }

    /** True while an ability's own hit is being dealt; damage listeners should skip it. */
    public static boolean inProgress() {
        return depth > 0;
    }

    /** Deals {@code amount} health points to a hostile mob as {@code caster}; false if it may not. */
    public static boolean deal(LivingEntity target, double amount, Player caster) {
        if (amount <= 0 || !Mobs.hostile(target)) return false;
        depth++;
        try {
            target.damage(amount, caster);
        } finally {
            depth--;
        }
        return true;
    }
}
