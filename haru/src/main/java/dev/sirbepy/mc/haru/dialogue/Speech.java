package dev.sirbepy.mc.haru.dialogue;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/** Haru's voice in chat: only the player he's talking to sees it. */
public final class Speech {

    public static final TextColor HARU = TextColor.color(0xFFB7C5);

    private Speech() {
    }

    public static Component line(String text) {
        return Component.text("✿ Haru", HARU)
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(Component.text(text, NamedTextColor.WHITE));
    }

    public static void say(Audience audience, String text) {
        audience.sendMessage(line(text));
    }

    /** A grey system note (what was bought, what's missing), not Haru speaking. */
    public static void note(Audience audience, String text) {
        audience.sendMessage(Component.text(text, NamedTextColor.GRAY));
    }
}
