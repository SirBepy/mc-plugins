package dev.sirbepy.mc.guild.gui;

import dev.sirbepy.mc.guild.Chat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Named, lored menu icons. Lore lines starting with "!" are drawn in the highlight colour. */
final class Icons {

    static final TextColor HIGHLIGHT = TextColor.color(0x7CD67C);

    private Icons() {
    }

    static ItemStack icon(Material material, String name, TextColor color, List<String> lore) {
        return icon(new ItemStack(material), name, color, lore);
    }

    static ItemStack icon(Material material, String name, TextColor color, String... lore) {
        return icon(new ItemStack(material), name, color, List.of(lore));
    }

    static ItemStack icon(ItemStack base, String name, TextColor color, List<String> lore) {
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            boolean highlight = line.startsWith("!");
            String text = highlight ? line.substring(1) : line;
            lines.add(Component.text(text, highlight ? HIGHLIGHT : Chat.LORE).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack head(OfflinePlayer player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        head.setItemMeta(meta);
        return head;
    }
}
