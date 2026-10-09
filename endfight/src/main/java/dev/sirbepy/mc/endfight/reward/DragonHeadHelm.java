package dev.sirbepy.mc.endfight.reward;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.EquippableComponent;

import java.util.List;

/**
 * The final-blow trophy (10ca's spec, Joe decided 2026-10-09): whoever deals the dragon's lethal
 * damage gets a "Dragon Head Helm", plain netherite with no enchantments, drawn by the server pack
 * as the vanilla dragon head (custom_model_data string "dragon_head", 10ca 6535d64). No equippable
 * asset id, so the client draws that item model on the head instead of an armor layer.
 */
public final class DragonHeadHelm {

    public static final String CUSTOM_MODEL_DATA = "dragon_head";

    private DragonHeadHelm() {
    }

    public static ItemStack build() {
        ItemStack item = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize("<dark_purple>Dragon Head Helm")
                .decoration(TextDecoration.ITALIC, false));
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of(CUSTOM_MODEL_DATA));
        meta.setCustomModelDataComponent(cmd);
        // Same reason as DragonscaleArmor: a fresh meta's default equippable is a bare HEAD slot.
        EquippableComponent equippable = meta.getEquippable();
        equippable.setSlot(EquipmentSlot.HEAD);
        equippable.setEquipSound(Sound.ITEM_ARMOR_EQUIP_NETHERITE);
        equippable.setModel(null);
        meta.setEquippable(equippable);
        item.setItemMeta(meta);
        return item;
    }

    /** True for a Dragon Head Helm built by {@link #build()}. */
    public static boolean is(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_HELMET || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelDataComponent()
                && meta.getCustomModelDataComponent().getStrings().contains(CUSTOM_MODEL_DATA);
    }
}
