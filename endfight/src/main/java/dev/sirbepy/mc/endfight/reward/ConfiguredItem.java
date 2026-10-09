package dev.sirbepy.mc.endfight.reward;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.Map;

/**
 * One {@code rewards.bundle.items} entry: {@code material} (required), {@code amount} (default 1),
 * optional {@code name} (MiniMessage) and {@code enchantments} (name -&gt; level). Enchantment names
 * are matched against a fixed set of vanilla ids below rather than a registry lookup - the bundle
 * is a small placeholder (a few modest items), and the fixed set avoids relying on
 * {@code Registry.ENCHANTMENT}/{@code Bukkit.getServer()} resolving under MockBukkit, which
 * {@code World.getEnderDragonBattle()} already does not (it throws unconditionally in the
 * MockBukkit version this repo pins - see {@code RewardServiceTest}'s comment on why XP totals are
 * summed from suppressed orbs instead of read from {@code DragonBattle}).
 */
final class ConfiguredItem {

    private ConfiguredItem() {
    }

    static ItemStack fromConfig(Map<?, ?> raw) {
        Object materialRaw = raw.get("material");
        if (materialRaw == null) {
            return null;
        }
        Material material = Material.matchMaterial(String.valueOf(materialRaw));
        if (material == null) {
            return null;
        }
        int amount = raw.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
        ItemStack item = new ItemStack(material, amount);

        Object nameRaw = raw.get("name");
        Object enchantRaw = raw.get("enchantments");
        if (nameRaw == null && !(enchantRaw instanceof Map<?, ?>)) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (nameRaw != null) {
            meta.displayName(MiniMessage.miniMessage().deserialize(String.valueOf(nameRaw))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (enchantRaw instanceof Map<?, ?> enchantments) {
            for (Map.Entry<?, ?> entry : enchantments.entrySet()) {
                Enchantment enchantment = enchantment(String.valueOf(entry.getKey()));
                if (enchantment != null && entry.getValue() instanceof Number level) {
                    meta.addEnchant(enchantment, level.intValue(), true);
                }
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    private static Enchantment enchantment(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "sharpness" -> Enchantment.SHARPNESS;
            case "protection" -> Enchantment.PROTECTION;
            case "unbreaking" -> Enchantment.UNBREAKING;
            case "mending" -> Enchantment.MENDING;
            case "efficiency" -> Enchantment.EFFICIENCY;
            case "fortune" -> Enchantment.FORTUNE;
            case "looting" -> Enchantment.LOOTING;
            case "power" -> Enchantment.POWER;
            case "infinity" -> Enchantment.INFINITY;
            case "feather_falling" -> Enchantment.FEATHER_FALLING;
            default -> null;
        };
    }
}
