package dev.sirbepy.mc.guild;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** The Guild's chat voice: a bronze "[Guild]" tag, then plain lines. */
public final class Chat {

    public static final TextColor BRONZE = TextColor.color(0xC8913A);
    public static final TextColor PARCHMENT = TextColor.color(0xE8DCC0);
    public static final TextColor LORE = NamedTextColor.GRAY;

    private Chat() {
    }

    public static Component tag() {
        return Component.text("[Guild] ", BRONZE, TextDecoration.BOLD);
    }

    public static Component line(String text) {
        return tag().append(Component.text(text, PARCHMENT).decoration(TextDecoration.BOLD, false));
    }

    public static Component line(String text, TextColor color) {
        return tag().append(Component.text(text, color).decoration(TextDecoration.BOLD, false));
    }

    public static void send(Audience to, String text) {
        to.sendMessage(line(text));
    }

    public static void send(Audience to, String text, TextColor color) {
        to.sendMessage(line(text, color));
    }

    /** A named NPC speaking, for dialogue: {@code Name: "line"}. */
    public static Component speech(String speaker, TextColor color, String text) {
        return Component.text(speaker + ": ", color, TextDecoration.BOLD)
                .append(Component.text(text, PARCHMENT).decoration(TextDecoration.BOLD, false));
    }
}
