package dev.sirbepy.mc.names;

import dev.sirbepy.mc.names.render.NameComposer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameComposerTest {

    @Test
    void joinsPrefixesInGivenOrder() {
        Component joined = NameComposer.joinPrefixes(List.of(Component.text("[A]"), Component.text("[B]")));
        assertEquals(Component.textOfChildren(Component.text("[A]"), Component.text("[B]")), joined);
    }

    @Test
    void noPrefixesYieldsAnEmptyComponent() {
        assertEquals(Component.empty(), NameComposer.joinPrefixes(List.of()));
    }

    @Test
    void fullNameAppendsColoredNameAfterPrefixes() {
        Component prefixed = NameComposer.fullName(
                List.of(Component.text("[IT] ")),
                Component.text("Steve", NamedTextColor.RED));

        Component expected = Component.textOfChildren(Component.text("[IT] "), Component.text("Steve", NamedTextColor.RED));
        assertEquals(expected, prefixed);
    }

    @Test
    void aColouredPrefixNeverColoursTheNameOrTheNextPrefix() {
        // The Guild's title tag is a gold root component; children inherit their parent's style.
        Component title = Component.text("[Night Owl] ", NamedTextColor.GOLD);
        Component name = Component.text("Alice");

        Component prefixed = NameComposer.fullName(List.of(title, Component.text("[IT] ")), name);

        assertEquals(null, prefixed.color());
        assertEquals(List.of(title, Component.text("[IT] "), name), prefixed.children(),
                "every part a sibling under the unstyled root, so none inherits the gold");
    }

    @Test
    void fullNameWithNoPrefixesIsJustTheName() {
        Component name = Component.text("Steve", NamedTextColor.RED);
        assertEquals(name, NameComposer.fullName(List.of(), name));
    }
}
