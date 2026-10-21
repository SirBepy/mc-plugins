package dev.sirbepy.mc.minibosses.relic;

import org.bukkit.entity.Player;

/**
 * A relic's sneak + swap-hands ability. One implementation per Guardian, registered with
 * {@link RelicActiveListener}; the listener owns the trigger, the cooldown bookkeeping and the
 * rank lookup, so an implementation is only the effect and its per-rank numbers.
 */
public interface RelicActive {

    /** The Guardian whose relic triggers this ability. */
    String guardianId();

    /** Ability name, used in the cooldown message. */
    String name();

    int cooldownSeconds(int rank);

    void fire(Player player, int rank);
}
