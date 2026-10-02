package dev.sirbepy.mc.names;

import dev.sirbepy.mc.names.render.PrefixRegistry;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefixRegistryTest {

    @Test
    void prefixesAreOrderedByAscendingPriority() {
        PrefixRegistry registry = new PrefixRegistry();
        UUID player = UUID.randomUUID();
        registry.set(player, "tag", Component.text("[IT]"), 10);
        registry.set(player, "rank", Component.text("[Owner]"), 0);

        List<Component> prefixes = registry.prefixesFor(player);
        assertEquals(List.of(Component.text("[Owner]"), Component.text("[IT]")), prefixes);
    }

    @Test
    void removingAPrefixDropsOnlyThatKey() {
        PrefixRegistry registry = new PrefixRegistry();
        UUID player = UUID.randomUUID();
        registry.set(player, "tag", Component.text("[IT]"), 10);
        registry.set(player, "rank", Component.text("[Owner]"), 0);

        registry.remove(player, "tag");

        assertEquals(List.of(Component.text("[Owner]")), registry.prefixesFor(player));
    }

    @Test
    void playerWithNoPrefixesGetsAnEmptyList() {
        PrefixRegistry registry = new PrefixRegistry();
        assertTrue(registry.prefixesFor(UUID.randomUUID()).isEmpty());
    }

    @Test
    void replacingTheSameKeyOverwritesInsteadOfStacking() {
        PrefixRegistry registry = new PrefixRegistry();
        UUID player = UUID.randomUUID();
        registry.set(player, "tag", Component.text("[old]"), 5);
        registry.set(player, "tag", Component.text("[new]"), 5);

        assertEquals(List.of(Component.text("[new]")), registry.prefixesFor(player));
    }
}
