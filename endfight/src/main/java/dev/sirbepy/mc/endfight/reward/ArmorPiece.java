package dev.sirbepy.mc.endfight.reward;

import org.bukkit.Material;

/** The 4 Dragonscale armor pieces a Dragon Hoard roll can award - see {@link DragonscaleArmor}. */
public enum ArmorPiece {
    HELMET(Material.NETHERITE_HELMET, "Dragonscale Helmet"),
    CHESTPLATE(Material.NETHERITE_CHESTPLATE, "Dragonscale Chestplate"),
    LEGGINGS(Material.NETHERITE_LEGGINGS, "Dragonscale Leggings"),
    BOOTS(Material.NETHERITE_BOOTS, "Dragonscale Boots");

    private final Material material;
    private final String displayName;

    ArmorPiece(Material material, String displayName) {
        this.material = material;
        this.displayName = displayName;
    }

    public Material material() {
        return material;
    }

    public String displayName() {
        return displayName;
    }
}
