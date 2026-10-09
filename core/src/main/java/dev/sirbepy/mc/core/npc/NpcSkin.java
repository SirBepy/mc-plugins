package dev.sirbepy.mc.core.npc;

import com.destroystokyo.paper.profile.ProfileProperty;
import dev.sirbepy.mc.core.item.Heads;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Mannequin;
import org.bukkit.profile.PlayerTextures;

import java.util.UUID;

/**
 * A player-shaped NPC's skin. {@code texture} is a Mojang-hosted skin hash every Java client can load;
 * {@code packTexture} and {@code packCape} name textures in the server resource pack (such as
 * {@code claudesmp:entity/haru}, read by the client from {@code textures/entity/haru.png}) that override
 * it for players who accepted the pack. Blank strings mean unset.
 */
public record NpcSkin(String texture, String packTexture, String packCape, boolean slim) {

    public static final NpcSkin NONE = new NpcSkin("", "", "", false);

    /** A skin drawn only from the server pack; {@code cape} may be blank. */
    public static NpcSkin pack(String texture, String cape, boolean slim) {
        return new NpcSkin("", texture, cape, slim);
    }

    /** True when the skin is drawn from the server pack, so only players with the pack can see it. */
    public boolean needsPack() {
        return !packTexture.isBlank() || !packCape.isBlank();
    }

    /**
     * The Mannequin profile for this skin. {@code name} (at most 16 characters) and {@code id} are only
     * used alongside a Mojang texture.
     */
    public ResolvableProfile profile(String name, UUID id) {
        if (texture.isBlank() && packTexture.isBlank()) {
            return Mannequin.defaultProfile();
        }
        // No name or id unless a texture property comes with them: a profile with only a name/id is
        // "dynamic", and an online-mode server resolves it against Mojang and replaces it, which wiped
        // the skin patch on live (2026-10-06, Haru's Mannequin profile came back as {}).
        ResolvableProfile.Builder builder = ResolvableProfile.resolvableProfile();
        if (!texture.isBlank()) {
            builder.name(name).uuid(id).addProperty(new ProfileProperty("textures", Heads.textureValue(texture)));
        }
        if (needsPack() || slim) {
            builder.skinPatch(patch -> {
                if (!packTexture.isBlank()) {
                    patch.body(Key.key(packTexture));
                }
                if (!packCape.isBlank()) {
                    patch.cape(Key.key(packCape));
                }
                patch.model(slim ? PlayerTextures.SkinModel.SLIM : PlayerTextures.SkinModel.CLASSIC);
            });
        }
        return builder.build();
    }

    /** True when this skin needs the pack but {@code mannequin}'s profile lost its patch. */
    public boolean undressed(Mannequin mannequin) {
        return needsPack() && mannequin.getProfile().skinPatch().isEmpty();
    }
}
