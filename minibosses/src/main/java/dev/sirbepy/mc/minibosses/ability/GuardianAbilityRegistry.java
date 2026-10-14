package dev.sirbepy.mc.minibosses.ability;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a per-Guardian plugin module registers its {@link GuardianAbility} implementations by id,
 * so a {@code GuardianTierRow.abilityIds()} list can be resolved at fight time. A later registration
 * for the same id replaces the earlier one (keeps this a dumb map, not a validator).
 */
public final class GuardianAbilityRegistry {

    private final Map<String, GuardianAbility> abilities = new ConcurrentHashMap<>();

    public void register(GuardianAbility ability) {
        abilities.put(ability.id(), ability);
    }

    public Optional<GuardianAbility> get(String id) {
        return Optional.ofNullable(abilities.get(id));
    }

    public Collection<GuardianAbility> all() {
        return abilities.values();
    }
}
