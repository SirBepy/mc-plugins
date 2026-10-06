package dev.sirbepy.mc.haru.npc;

import com.destroystokyo.paper.profile.ProfileProperty;
import dev.sirbepy.mc.haru.config.HaruConfig;
import dev.sirbepy.mc.haru.dialogue.Speech;
import dev.sirbepy.mc.haru.item.Goods;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Display;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * How the Java-side Haru looks: the skin from {@code config.yml}. {@code skin.texture} is a Mojang-hosted
 * skin hash every Java client can load; {@code skin.pack-texture} (and {@code skin.pack-cape}) name
 * textures in the server resource pack that override it for players who accepted the pack.
 */
public final class HaruLook {

    private static final UUID PROFILE_ID = UUID.fromString("00000000-0000-4000-8000-000000004a72");

    private final Supplier<HaruConfig> config;

    public HaruLook(Supplier<HaruConfig> config) {
        this.config = config;
    }

    /** True when the skin is drawn from the server pack, so only players with the pack can see it. */
    public boolean needsPack() {
        HaruConfig.Skin skin = config.get().skin();
        return !skin.packTexture().isBlank() || !skin.packCape().isBlank();
    }

    /** True when a pack skin is configured but the Mannequin's profile lost its patch. */
    public boolean undressed(Mannequin mannequin) {
        return needsPack() && mannequin.getProfile().skinPatch().isEmpty();
    }

    public void dress(Mannequin mannequin) {
        mannequin.setDescription(Component.text(HaruNpc.TITLE, Speech.HARU));
        mannequin.setProfile(profile(config.get().skin()));
    }

    static ResolvableProfile profile(HaruConfig.Skin skin) {
        if (skin.texture().isBlank() && skin.packTexture().isBlank()) {
            return Mannequin.defaultProfile();
        }
        // No name or id unless a texture property comes with them: a profile with only a name/id is
        // "dynamic", and an online-mode server resolves it against Mojang and replaces it, which wiped
        // the skin patch on live (2026-10-06, the Mannequin's profile came back as {}).
        ResolvableProfile.Builder builder = ResolvableProfile.resolvableProfile();
        if (!skin.texture().isBlank()) {
            builder.name(HaruNpc.NAME).uuid(PROFILE_ID)
                    .addProperty(new ProfileProperty("textures", Goods.textureValue(skin.texture())));
        }
        if (!skin.packTexture().isBlank() || !skin.packCape().isBlank() || skin.slim()) {
            builder.skinPatch(patch -> {
                if (!skin.packTexture().isBlank()) {
                    patch.body(Key.key(skin.packTexture()));
                }
                if (!skin.packCape().isBlank()) {
                    patch.cape(Key.key(skin.packCape()));
                }
                patch.model(skin.slim() ? PlayerTextures.SkinModel.SLIM : PlayerTextures.SkinModel.CLASSIC);
            });
        }
        return builder.build();
    }

    static void styleTitle(TextDisplay display) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(0.7f, 0.7f, 0.7f), new AxisAngle4f()));
    }
}
