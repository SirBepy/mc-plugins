package dev.sirbepy.mc.classes.talent;

import org.bukkit.entity.Player;

/**
 * A kit-provided ability: the class's ability 2, its ultimate, or a keystone's replacement. The
 * engine ({@link AbilitySlots}) checks the slot's unlock level, the cooldown and (for ultimates) the
 * full resource bar, then calls {@link #cast}. A cast that returns false (no target in range...)
 * costs nothing and starts no cooldown.
 */
public interface Ability {

    String name();

    /** One line for the menu and {@code /class info}, e.g. "dash 6 blocks, knock the first mob back". */
    String describe(Player player);

    /** Cooldown before talent modifiers; the engine applies those and the 30 s floor. Ignored for ultimates. */
    int cooldownSeconds(Player player);

    /** Fires the ability; false when it couldn't (nothing happens, no cooldown, no resource spent). */
    boolean cast(Player player);

    /** A passive keystone (Undying Fury) leaves its slot without a button: pressing it only explains. */
    default boolean passive() {
        return false;
    }
}
