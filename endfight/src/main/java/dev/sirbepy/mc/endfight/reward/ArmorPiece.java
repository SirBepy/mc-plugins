package dev.sirbepy.mc.endfight.reward;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

/** The 4 Dragonscale armor pieces a Dragon Hoard roll can award - see {@link DragonscaleArmor}. */
public enum ArmorPiece {
    HELMET(Material.NETHERITE_HELMET, "Dragonscale Helmet", EquipmentSlot.HEAD),
    CHESTPLATE(Material.NETHERITE_CHESTPLATE, "Dragonscale Chestplate", EquipmentSlot.CHEST),
    LEGGINGS(Material.NETHERITE_LEGGINGS, "Dragonscale Leggings", EquipmentSlot.LEGS),
    BOOTS(Material.NETHERITE_BOOTS, "Dragonscale Boots", EquipmentSlot.FEET);

    private final Material material;
    private final String displayName;
    private final EquipmentSlot slot;

    ArmorPiece(Material material, String displayName, EquipmentSlot slot) {
        this.material = material;
        this.displayName = displayName;
        this.slot = slot;
    }

    public EquipmentSlot slot() {
        return slot;
    }

    public Material material() {
        return material;
    }

    public String displayName() {
        return displayName;
    }
}
