package dev.sirbepy.mc.haru.dialogue;

import dev.sirbepy.mc.haru.item.HaruItems;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Names the notable thing in a player's hand for {@link Dialogue}: other plugins' items are found by
 * their own markers (the Katana and Pollos gun by custom-model-data string, Dragon Balls by the
 * {@code dragonballs:star} PDC key, the radar by {@code dragonballs:radar}).
 */
public final class HeldItems {

    private static final NamespacedKey DRAGON_BALL = new NamespacedKey("dragonballs", "star");
    private static final NamespacedKey DRAGON_RADAR = new NamespacedKey("dragonballs", "radar");

    private HeldItems() {
    }

    public static String tag(ItemStack item, HaruItems haruItems) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            var pdc = meta.getPersistentDataContainer();
            if (pdc.has(DRAGON_BALL, PersistentDataType.INTEGER)) {
                return "dragon_ball";
            }
            if (pdc.has(DRAGON_RADAR, PersistentDataType.BOOLEAN)) {
                return "dragon_radar";
            }
            String haru = haruItems.idOf(item);
            if (HaruItems.SPIRIT_HORN.equals(haru)) {
                return haru;
            }
            if (meta.hasCustomModelDataComponent()) {
                List<String> strings = meta.getCustomModelDataComponent().getStrings();
                if (strings.contains("katana")) {
                    return "katana";
                }
                if (strings.stream().anyMatch(s -> s.startsWith("pollos_gun"))) {
                    return "pollos_gun";
                }
            }
            if (item.getType() == Material.PLAYER_HEAD && meta.hasLore() && meta.lore() != null
                    && meta.lore().stream().map(PlainTextComponentSerializer.plainText()::serialize)
                    .anyMatch("Sanctuary Collection"::equals)) {
                return "collectible";
            }
        }
        Material type = item.getType();
        return switch (type) {
            case ELYTRA -> "elytra";
            case TRIDENT -> "trident";
            case MACE -> "mace";
            case TOTEM_OF_UNDYING -> "totem";
            case FISHING_ROD -> "fishing_rod";
            case CAKE -> "cake";
            case EMERALD_BLOCK -> "emerald_block";
            case DIAMOND, DIAMOND_BLOCK -> "diamond";
            case NETHERITE_SWORD, NETHERITE_PICKAXE, NETHERITE_AXE, NETHERITE_INGOT, NETHERITE_HELMET,
                 NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS -> "netherite";
            case CHERRY_SAPLING, CHERRY_LOG, CHERRY_LEAVES, PINK_PETALS -> "cherry";
            default -> Tag.FLOWERS.isTagged(type) ? "flower" : null;
        };
    }
}
