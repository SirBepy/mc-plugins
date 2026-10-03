package dev.sirbepy.mc.names.command;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * docs/plan.md requires every player-facing string to come from config.yml; the reload usage line
 * used to be a raw {@code Component.text("Usage: /names reload")} (todo 07). This proves a configured
 * override of {@code reload-usage} is what a sender actually receives, not just that the key exists.
 */
class NamesReloadCommandTest {

    private ServerMock server;
    private PlayerMock sender;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        sender = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Messages messagesWith(String reloadUsage) {
        YamlConfiguration root = new YamlConfiguration();
        root.set("messages.reload-usage", reloadUsage);
        return new Messages(root.getConfigurationSection("messages"));
    }

    @Test
    void sendUsageRendersTheConfiguredOverride() {
        Messages messages = messagesWith("<red>Try: /names reload</red>");

        NamesReloadCommand.sendUsage(sender, messages);

        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(sender.nextComponentMessage());
        assertEquals("Try: /names reload", text,
                "a configured override of reload-usage must be what the sender actually receives");
    }

    @Test
    void sendUsageDefaultMatchesThePreviousHardcodedWording() {
        Messages messages = messagesWith("Usage: /names reload");

        NamesReloadCommand.sendUsage(sender, messages);

        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(sender.nextComponentMessage());
        assertEquals("Usage: /names reload", text, "the shipped default must read exactly as before");
    }
}
