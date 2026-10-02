package dev.sirbepy.mc.names.data;

import dev.sirbepy.mc.core.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * A color a player can pick in {@code /color}. {@link #storageKey()} is what gets persisted in
 * players.yml; {@link ColorPalette#resolve(String)} turns it back into one of these.
 */
public sealed interface ColorChoice {

    String storageKey();

    Component colorize(String text);

    record Named(NamedTextColor color, String key) implements ColorChoice {
        @Override
        public String storageKey() {
            return "named:" + key;
        }

        @Override
        public Component colorize(String text) {
            return Component.text(text, color);
        }
    }

    record Hex(TextColor color, String label) implements ColorChoice {
        @Override
        public String storageKey() {
            return "hex:" + color.asHexString().substring(1);
        }

        @Override
        public Component colorize(String text) {
            return Component.text(text, color);
        }
    }

    record Gradient(String name, TextColor from, TextColor to) implements ColorChoice {
        @Override
        public String storageKey() {
            return "gradient:" + name;
        }

        @Override
        public Component colorize(String text) {
            // The nickname/username charset is validated upstream (letters, digits, underscore,
            // space) so it can never break out of the MiniMessage tag built here.
            String template = "<gradient:" + from.asHexString() + ":" + to.asHexString() + ">" + text + "</gradient>";
            return Messages.mini().deserialize(template);
        }
    }
}
