package dev.sirbepy.mc.core.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/**
 * The PLAYER_HEAD + SkullMeta + {@code Bukkit.createProfile} + {@code ProfileProperty("textures", ...)}
 * sequence every textured-head plugin needs, so the texture-property wiring lives in one place instead
 * of 3 near-identical copies.
 */
public final class Heads {

    private Heads() {
    }

    /** A profile carrying {@code textureValue} as its skin under {@code profileId}, or null when the
     * value is blank (plain head, no Mojang session-server lookup needed). */
    public static PlayerProfile texturedProfile(UUID profileId, String textureValue) {
        if (textureValue == null || textureValue.isBlank()) {
            return null;
        }
        PlayerProfile profile = Bukkit.createProfile(profileId);
        profile.setProperty(new ProfileProperty("textures", textureValue));
        return profile;
    }

    /** A player head item wearing {@code textureValue} under {@code profileId}; a plain head when the
     * value is blank. */
    public static ItemStack texturedHead(UUID profileId, String textureValue) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PlayerProfile profile = texturedProfile(profileId, textureValue);
        if (profile != null) {
            meta.setPlayerProfile(profile);
        }
        item.setItemMeta(meta);
        return item;
    }
}
