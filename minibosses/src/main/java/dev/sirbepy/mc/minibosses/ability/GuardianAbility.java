package dev.sirbepy.mc.minibosses.ability;

import org.bukkit.entity.LivingEntity;

/**
 * One ability a Guardian's tier row can name by id (see {@code GuardianTierRow.abilityIds()}). A
 * per-Guardian implementation registers itself under its id with a {@link GuardianAbilityRegistry};
 * this foundation todo defines only the hook, no real ability.
 */
public interface GuardianAbility {

    String id();

    /** Fires the ability against the boss entity it is attached to. */
    void trigger(LivingEntity boss);
}
