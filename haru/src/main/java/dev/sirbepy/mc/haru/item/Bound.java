package dev.sirbepy.mc.haru.item;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Items from Haru's owner-only shelves carry their buyer's UUID ({@code haru:bound}) and a lore line
 * saying so. {@code BoundListener} keeps everyone else from holding them, which is what stops Haru
 * becoming a resale engine (Joe's balance spec, 2026-10-06).
 */
public final class Bound {

    private final NamespacedKey key;

    public Bound(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "bound");
    }

    public ItemStack bind(ItemStack item, UUID owner, String ownerName) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, owner.toString());
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Goods.text("Bound to " + ownerName, Goods.PETAL));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** The bound owner, or null for an unbound item. */
    public UUID ownerOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** True if the item is bound to someone other than {@code player}. */
    public boolean foreignTo(ItemStack item, UUID player) {
        UUID owner = ownerOf(item);
        return owner != null && !owner.equals(player);
    }
}
