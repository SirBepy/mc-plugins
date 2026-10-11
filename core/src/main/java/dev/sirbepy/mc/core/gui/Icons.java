package dev.sirbepy.mc.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Menu icons: a non-italic name and lore, where a lore line starting with "!" is highlighted. */
public final class Icons {

    private Icons() {
    }

    /** Names and lores {@code item} in place and returns it; colours are each plugin's own palette. */
    public static ItemStack named(ItemStack item, String name, TextColor nameColor, List<String> lore,
                                  TextColor loreColor, TextColor highlightColor) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, nameColor).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            boolean highlight = line.startsWith("!");
            lines.add(Component.text(highlight ? line.substring(1) : line, highlight ? highlightColor : loreColor)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }
}
