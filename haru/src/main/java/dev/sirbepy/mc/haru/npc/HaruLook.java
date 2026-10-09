package dev.sirbepy.mc.haru.npc;

import dev.sirbepy.mc.core.npc.NpcSkin;
import dev.sirbepy.mc.haru.config.HaruConfig;
import dev.sirbepy.mc.haru.dialogue.Speech;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Display;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;
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
        return config.get().skin().needsPack();
    }

    /** True when a pack skin is configured but the Mannequin's profile lost its patch. */
    public boolean undressed(Mannequin mannequin) {
        return config.get().skin().undressed(mannequin);
    }

    public void dress(Mannequin mannequin) {
        mannequin.setDescription(Component.text(HaruNpc.TITLE, Speech.HARU));
        mannequin.setProfile(config.get().skin().profile(HaruNpc.NAME, PROFILE_ID));
    }

    static void styleTitle(TextDisplay display) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(0.7f, 0.7f, 0.7f), new AxisAngle4f()));
    }
}
