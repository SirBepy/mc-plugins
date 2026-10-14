package dev.sirbepy.mc.minibosses.registry;

import java.util.List;

/**
 * One {@link Tier}'s stat block for a single Guardian, read from that Guardian's own YAML (mirrors
 * the shape of {@code endfight.reward.TierRewardRow}, but per-Guardian instead of shared: each
 * Guardian picks its own health/damage/abilities/loot per tier, there is no approved-defaults table
 * to fall back to here). {@code abilityIds} are looked up in a {@code GuardianAbilityRegistry} by
 * the per-Guardian plugin code that implements them; {@code lootTable} is a reference key only,
 * resolved by whichever reward code a later per-Guardian todo writes.
 */
public record GuardianTierRow(double health, double damage, List<String> abilityIds, String lootTable) {
}
