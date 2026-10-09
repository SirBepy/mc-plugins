package dev.sirbepy.mc.core.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.util.Optional;

/**
 * When a player head was taken, as a PDC long under one fixed key: the market's Wanted board (a
 * fresh kill pays full, an old head the configured share, Joe 2026-10-09) and the cartel's trophy
 * heads both read and write this same stamp, so a head counts as fresh no matter which plugin's
 * drop it came from. The key is built from a literal namespace rather than a {@code Plugin}
 * instance (the usual pattern, see {@code haru.item.Bound}): the two plugins' jars never depend on
 * each other, so the key has to be constructible identically in both without sharing a class.
 */
public final class KillStamp {

    public static final NamespacedKey KEY = new NamespacedKey("sirbepy", "killed_at");

    private KillStamp() {
    }

    public static void stamp(ItemMeta meta, Instant at) {
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.LONG, at.toEpochMilli());
    }

    /** Stamps {@code stack} in place; a no-op when it carries no item meta at all (e.g. an air stack). */
    public static void stamp(ItemStack stack, Instant at) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        stamp(meta, at);
        stack.setItemMeta(meta);
    }

    /** When {@code stack}'s head was taken, or empty for one with no stamp at all (every head that
     * existed before this feature, among others). */
    public static Optional<Instant> killedAt(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        Long millis = stack.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.LONG);
        return millis == null ? Optional.empty() : Optional.of(Instant.ofEpochMilli(millis));
    }
}
