package dev.sirbepy.mc.dragonballs.summon.curse;

import java.util.UUID;

/**
 * A persistent effect curse: {@code effectKey} is a {@link org.bukkit.potion.PotionEffectType}'s
 * {@code NamespacedKey} in string form (e.g. {@code "minecraft:slowness"}), kept as a plain string
 * here so this record (and {@link CurseStore}) never needs a live Bukkit registry to round-trip
 * through YAML.
 */
public record EffectCurse(UUID player, String effectKey, int amplifier, long expiryMillis) {
}
