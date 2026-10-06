package dev.sirbepy.mc.cartel.phone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * The cartel flip phone: a clock (no recipe uses one and no villager buys one, so it can't be
 * crafted or traded away) marked {@code cartel:phone}. The look is the server pack's
 * {@code cartel_phone} custom model data; without the pack, or on Bedrock, it is a plain clock.
 */
public final class Phone {

    public static final String MODEL = "cartel_phone";

    private final NamespacedKey key;

    public Phone(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "phone");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Cartel Flip Phone", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                line("Right-click to call Leonel and Marco."),
                line("Guard Mode: they protect you."),
                line("Assassinate Mode: they hunt a player.")));
        meta.setMaxStackSize(1);
        CustomModelDataComponent model = meta.getCustomModelDataComponent();
        model.setStrings(List.of(MODEL));
        meta.setCustomModelDataComponent(model);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPhone(ItemStack item) {
        return item != null && item.getType() == Material.CLOCK && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
