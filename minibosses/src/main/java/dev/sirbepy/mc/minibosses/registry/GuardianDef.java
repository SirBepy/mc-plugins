package dev.sirbepy.mc.minibosses.registry;

import org.bukkit.World;

import java.util.Map;

/**
 * One Guardian boss, pure data: a YAML file under {@code guardians/} parses straight into this
 * (see {@link GuardianRegistry}). {@code summonHook} is a bare reference key (a structure id, a
 * ritual id, whatever the Guardian's own future summon mechanic wants) - this foundation todo does
 * not interpret it. {@code tiers} always has exactly the three {@link Tier} values; a Guardian
 * missing one is rejected by the registry rather than loaded with a hole in it.
 */
public record GuardianDef(String id, String displayName, World.Environment dimension, String description,
                           Map<Tier, GuardianTierRow> tiers, String summonHook) {
}
