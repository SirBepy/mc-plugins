package dev.sirbepy.mc.dragonballs.summon;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.effects.DragonType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Formats a dragon's spoken line exactly as the rulebook shows it ("{@code [Shenron] text}" in bold
 * dark_green tag + green body; Porunga: dark_aqua/aqua), but built through the Component API rather
 * than {@code MiniMessage.deserialize} on the concatenated string: the spoken text comes from a
 * player's wish chat or the judge's free-typed {@code /shenron say}, and parsing untrusted text as
 * MiniMessage would let it inject its own color/click tags into a line every player on the server
 * receives.
 */
final class DragonSpeech {

    private DragonSpeech() {
    }

    static Component line(DragonType dragon, BallSet set, String text) {
        NamedTextColor tagColor = dragon == DragonType.SHENRON ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_AQUA;
        NamedTextColor bodyColor = dragon == DragonType.SHENRON ? NamedTextColor.GREEN : NamedTextColor.AQUA;
        Component tag = Component.text("[" + dragon.displayName(set) + "]", tagColor).decorate(TextDecoration.BOLD);
        return tag.append(Component.text(" " + text, bodyColor));
    }
}
