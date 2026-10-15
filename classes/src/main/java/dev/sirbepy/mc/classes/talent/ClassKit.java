package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.classes.RpgClass;
import net.kyori.adventure.bossbar.BossBar;

import java.util.List;

/**
 * Everything one class adds on top of the engine: its 33 talents, its resource, its ability 2 and
 * ultimate, and the keystones' replacement abilities. A kit gets its {@link KitContext} in the
 * constructor and registers its own listeners in {@link #enable}; every listener must check
 * {@link KitContext#active} first, since only the active class's tree counts.
 */
public interface ClassKit {

    RpgClass rpgClass();

    /** "Rage", "Focus"... */
    String resourceName();

    BossBar.Color barColor();

    /** How the resource fills, for the menu: "+5 per melee hit on a mob, +3 per mob hit taken". */
    String resourceGain();

    /** The three branch names, index = {@link Talent#branch()}. */
    List<String> branchNames();

    /**
     * Every node, in display order: within a branch and tier, nodes are laid out left to right in
     * this order. 2 nodes in tiers 1 and 4, 3 in tiers 2 and 3, one keystone in tier 5, per branch.
     */
    List<Talent> talents();

    Ability secondAbility();

    Ability ultimate();

    /** The ability a keystone puts in its slot ({@link Talent#replaces()}). */
    Ability keystoneAbility(Talent keystone);

    /** Registers listeners and timers through the context. Called once, at plugin enable. */
    void enable();
}
