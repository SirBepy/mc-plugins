package dev.sirbepy.mc.tag.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Either a plain material or a player-head texture. {@code material} wins if both are set.
 *
 * Default texture is a "Sheep" head from minecraft-heads.com (animals category), hash mirrored
 * via the TheLuca98/MinecraftHeads CSV export of the public minecraft-heads.com API and verified
 * live against textures.minecraft.net on 2026-10-02 (HTTP 200). See config.yml for the full note.
 */
public final class HeadConfig {

    public static final String DEFAULT_SHEEP_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2M2MDdhMzczN2U5OWYxNDI2MjQxNTJhNjg1ODhmNGJmYWZmYjhjZDNmYTk5YzE5OTcxMTU0Y2M3NGYwZGRmNzEifX19";

    public final Material material;
    public final String texture;

    private HeadConfig(Material material, String texture) {
        this.material = material;
        this.texture = texture;
    }

    public boolean isTexture() {
        return material == null;
    }

    public static HeadConfig from(ConfigurationSection section) {
        if (section != null) {
            String materialName = section.getString("material");
            if (materialName != null && !materialName.isBlank()) {
                Material material = Material.matchMaterial(materialName);
                if (material != null) {
                    return new HeadConfig(material, null);
                }
            }
            String texture = section.getString("texture");
            if (texture != null && !texture.isBlank()) {
                return new HeadConfig(null, texture);
            }
        }
        return new HeadConfig(null, DEFAULT_SHEEP_TEXTURE);
    }
}
