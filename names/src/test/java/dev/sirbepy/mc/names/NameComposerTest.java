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
        assertEquals(Component.text("[A]").append(Component.text("[B]")), joined);
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

        Component expected = Component.text("[IT] ").append(Component.text("Steve", NamedTextColor.RED));
        assertEquals(expected, prefixed);
    }

    @Test
    void fullNameWithNoPrefixesIsJustTheName() {
        Component name = Component.text("Steve", NamedTextColor.RED);
        assertEquals(name, NameComposer.fullName(List.of(), name));
    }
}
