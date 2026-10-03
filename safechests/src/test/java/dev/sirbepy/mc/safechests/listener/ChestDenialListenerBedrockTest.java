package dev.sirbepy.mc.safechests.listener;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.safechests.SafeChestsConfig;
import dev.sirbepy.mc.safechests.bolt.BoltAccess;
import dev.sirbepy.mc.safechests.grant.FakeBoltAPI;
import dev.sirbepy.mc.safechests.request.RequestManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.popcraft.bolt.protection.BlockProtection;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves a denied Bedrock requester gets the typed {@code denied-bedrock} instruction instead of the
 * chat click-event message (todo 04 part B), gated by {@code bedrock.forms} exactly like every other
 * Bedrock fork in this feature.
 */
class ChestDenialListenerBedrockTest {

    private ServerMock server;
    private PluginMock plugin;
    private FakeBoltAPI api;
    private BoltAccess bolt;
    private RequestManager requests;
    private SafeChestsConfig config;
    private PlayerMock requester;
    private Block chestBlock;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("SafeChests");
        api = new FakeBoltAPI();
        bolt = new BoltAccess(api);
        requests = new RequestManager();
        seedMessages(plugin);
        config = new SafeChestsConfig(plugin.getConfig());
        requester = server.addPlayer();

        chestBlock = server.addSimpleWorld("world").getBlockAt(0, 64, 0);
        BlockProtection protection = new BlockProtection(UUID.randomUUID(), UUID.randomUUID(), "container", 0, 0,
                new HashMap<>(), chestBlock.getWorld().getName(), chestBlock.getX(), chestBlock.getY(), chestBlock.getZ(), "CHEST");
        api.protections.add(protection);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** MockBukkit.createMockPlugin has no backing jar, so SafeChests' real config.yml never loads;
     * seed the exact keys this test exercises instead. */
    private static void seedMessages(PluginMock plugin) {
        plugin.getConfig().set("messages.denied", "This chest belongs to <owner>. Click to request access.");
        plugin.getConfig().set("messages.denied-bedrock", "This chest belongs to <owner>. Type /safe-chests-one-time to ask for access.");
    }

    private String plain(Player player) {
        return PlainTextComponentSerializer.plainText().serialize(((PlayerMock) player).nextComponentMessage());
    }

    @Test
    void bedrockRequesterGetsTheTypedDeniedMessage() {
        ChestDenialListener listener = new ChestDenialListener(bolt, requests, () -> config, new FakeBedrockForms(true));

        listener.handleDenied(requester, chestBlock);

        assertTrue(plain(requester).contains("/safe-chests-one-time"),
                "a Bedrock requester must be told to type the command, not shown a clickable chat message");
    }

    @Test
    void javaRequesterStillGetsTheClickableChatMessage() {
        ChestDenialListener listener = new ChestDenialListener(bolt, requests, () -> config, new FakeBedrockForms(false));

        listener.handleDenied(requester, chestBlock);

        assertTrue(plain(requester).contains("Click to request access"));
    }

    @Test
    void bedrockFormsDisabledFallsBackToTheJavaMessageForABedrockRequester() {
        plugin.getConfig().set("bedrock.forms", false);
        config = new SafeChestsConfig(plugin.getConfig());
        ChestDenialListener listener = new ChestDenialListener(bolt, requests, () -> config, new FakeBedrockForms(true));

        listener.handleDenied(requester, chestBlock);

        assertTrue(plain(requester).contains("Click to request access"));
    }

    /** Only isBedrock matters for ChestDenialListener; the form-opening methods are never reached. */
    private static final class FakeBedrockForms implements BedrockForms {
        private final boolean bedrock;

        FakeBedrockForms(boolean bedrock) {
            this.bedrock = bedrock;
        }

        @Override
        public boolean isBedrock(UUID player) {
            return bedrock;
        }

        @Override
        public void openInput(Player player, String title, String label, String prefill, Consumer<String> onSubmit) {
            throw new UnsupportedOperationException("ChestDenialListener never opens a form");
        }

        @Override
        public void openButtons(Player player, String title, String content, List<String> buttons, IntConsumer onClick) {
            throw new UnsupportedOperationException("ChestDenialListener never opens a form");
        }
    }
}
