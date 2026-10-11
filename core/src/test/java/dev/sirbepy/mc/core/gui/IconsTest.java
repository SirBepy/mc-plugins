package dev.sirbepy.mc.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class IconsTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aBangLineIsHighlightedWithoutItsBangAndTheRestUseTheLoreColour() {
        ItemStack base = new ItemStack(Material.IRON_SWORD);

        ItemStack icon = Icons.named(base, "Warrior", NamedTextColor.RED, List.of("Hits hard", "!Your class"),
                NamedTextColor.GRAY, NamedTextColor.GREEN);

        assertSame(base, icon);
        Component name = icon.getItemMeta().displayName();
        assertEquals("Warrior", PlainTextComponentSerializer.plainText().serialize(name));
        assertEquals(NamedTextColor.RED, name.color());
        assertEquals(TextDecoration.State.FALSE, name.decoration(TextDecoration.ITALIC));
        List<Component> lore = icon.getItemMeta().lore();
        assertEquals("Hits hard", PlainTextComponentSerializer.plainText().serialize(lore.get(0)));
        assertEquals(NamedTextColor.GRAY, lore.get(0).color());
        assertEquals("Your class", PlainTextComponentSerializer.plainText().serialize(lore.get(1)));
        assertEquals(NamedTextColor.GREEN, lore.get(1).color());
        assertEquals(TextDecoration.State.FALSE, lore.get(1).decoration(TextDecoration.ITALIC));
    }
}
