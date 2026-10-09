package dev.sirbepy.mc.endfight.reward;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code rewards.bundle} from {@code config.yml}: a display name, lore and a flat list of items,
 * built into one {@link Material#BUNDLE} stack via {@link BundleMeta}. Contents are still being
 * designed in another chat (Joe, 2026-10-09); {@link #from} falls back to a placeholder "Dragon
 * Hoard" bundle if the section is missing entirely.
 */
public final class RewardBundle {

    private static final String DEFAULT_NAME = "<gold>Dragon Hoard";

    private final String name;
    private final List<String> lore;
    private final List<ItemStack> items;

    private RewardBundle(String name, List<String> lore, List<ItemStack> items) {
        this.name = name;
        this.lore = lore;
        this.items = items;
    }

    public static RewardBundle from(ConfigurationSection section) {
        if (section == null) {
            return new RewardBundle(DEFAULT_NAME, List.of(), List.of());
        }
        String name = section.getString("name", DEFAULT_NAME);
        List<String> lore = section.getStringList("lore");
        List<ItemStack> items = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("items")) {
            ItemStack item = ConfiguredItem.fromConfig(raw);
            if (item != null) {
                items.add(item);
            }
        }
        return new RewardBundle(name, lore, items);
    }

    /** A fresh {@code BUNDLE} item stack holding a clone of every configured item. */
    public ItemStack toItemStack() {
        ItemStack stack = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) stack.getItemMeta();
        MiniMessage mini = MiniMessage.miniMessage();
        meta.displayName(plain(mini, name));
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>(lore.size());
            for (String line : lore) {
                lines.add(plain(mini, line));
            }
            meta.lore(lines);
        }
        for (ItemStack item : items) {
            meta.addItem(item.clone());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component plain(MiniMessage mini, String text) {
        return mini.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    /** The configured items, for tests - the public API is {@link #toItemStack()}. */
    List<ItemStack> items() {
        return items;
    }
}
