package dev.sirbepy.mc.guild.gui;

import dev.sirbepy.mc.guild.Chat;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

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
        return dev.sirbepy.mc.core.gui.Icons.named(base.clone(), name, color, lore, Chat.LORE, HIGHLIGHT);
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
