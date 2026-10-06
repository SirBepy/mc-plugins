package dev.sirbepy.mc.haru.gui;

import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.item.Goods;
import dev.sirbepy.mc.haru.shop.Offer;
import dev.sirbepy.mc.haru.shop.Restock;
import dev.sirbepy.mc.haru.shop.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Menu icons: an offer's item with price and stock lines added, and plain labelled buttons. */
public final class Icons {

    private Icons() {
    }

    public static ItemStack offer(Shop shop, OwnerState state, Offer offer) {
        ItemStack icon = offer.icon().get();
        List<Component> lore = new ArrayList<>();
        ItemMeta meta = icon.getItemMeta();
        if (meta.hasLore() && meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        lore.add(Component.empty());
        int remaining = shop.remaining(state, offer);
        boolean owned = offer.restock() == Restock.ONCE && remaining <= 0 && offer.stock() == 1;
        if (owned) {
            lore.add(line("Already yours", NamedTextColor.GREEN));
        } else {
            lore.add(line("Price: " + price(shop.emeraldPrice(state, offer), offer.emeralds(), offer.petals()),
                    NamedTextColor.YELLOW));
            if (offer.limited()) {
                lore.add(remaining > 0
                        ? line("Stock: " + remaining + " left, " + restockText(offer.restock()), NamedTextColor.GRAY)
                        : line("Sold out, back " + restockText(offer.restock()), NamedTextColor.RED));
            }
            if (remaining > 0) {
                lore.add(line("Click to buy", Goods.SAKURA));
            }
        }
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    static String price(int emeralds, int undiscounted, int petals) {
        List<String> parts = new ArrayList<>();
        if (emeralds > 0) {
            parts.add(emeralds + (emeralds == 1 ? " Emerald" : " Emeralds")
                    + (emeralds < undiscounted ? " (was " + undiscounted + ")" : ""));
        }
        if (petals > 0) {
            parts.add(petals + " Spirit Petals");
        }
        return parts.isEmpty() ? "free" : String.join(" + ", parts);
    }

    public static String restockText(Restock restock) {
        return switch (restock) {
            case MINECRAFT_DAY -> "restocks at dawn";
            case ROTATION -> "until the wares change";
            case REAL_DAY -> "restocks tomorrow";
            case REAL_WEEK -> "restocks next week";
            case ONCE -> "only ever once";
            case UNLIMITED -> "";
        };
    }

    public static ItemStack button(Material material, String name, TextColor color, String... lore) {
        return Goods.named(new ItemStack(material), name, color, lore);
    }

    public static ItemStack button(Material material, String name, TextColor color, List<String> lore) {
        return button(new ItemStack(material), name, color, lore);
    }

    public static ItemStack button(ItemStack base, String name, TextColor color, List<String> lore) {
        return Goods.named(base, name, color, lore.toArray(String[]::new));
    }

    static Component line(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
