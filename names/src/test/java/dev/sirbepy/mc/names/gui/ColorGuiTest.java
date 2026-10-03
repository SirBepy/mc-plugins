package dev.sirbepy.mc.names.gui;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * docs/plan.md requires every player-facing string to come from config.yml; the Reset item's label
 * used to be a raw {@code Component.text("Reset", NamedTextColor.GRAY)} while every sibling item in
 * this GUI already rendered through {@link Messages} (todo 07). This proves a configured override of
 * {@code color-gui-reset} is what actually ends up on the item, not just that the key exists.
 */
class ColorGuiTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Messages messagesWith(String colorGuiReset) {
        YamlConfiguration root = new YamlConfiguration();
        root.set("messages.color-gui-reset", colorGuiReset);
        root.set("messages.color-gui-apply-hint", "<gray>Click to apply</gray>");
        return new Messages(root.getConfigurationSection("messages"));
    }

    @Test
    void resetItemDisplayNameRendersTheConfiguredOverride() {
        Messages messages = messagesWith("<dark_red>Clear</dark_red>");

        ItemStack item = ColorGui.resetItem(messages);

        ItemMeta meta = item.getItemMeta();
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(meta.displayName());
        assertEquals("Clear", text, "a configured override of color-gui-reset must be what the item actually shows");
    }

    @Test
    void resetItemDefaultMatchesThePreviousHardcodedLabel() {
        Messages messages = messagesWith("<gray>Reset</gray>");

        ItemStack item = ColorGui.resetItem(messages);

        ItemMeta meta = item.getItemMeta();
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(meta.displayName());
        assertEquals("Reset", text, "the shipped default must read exactly as before");
    }
}
