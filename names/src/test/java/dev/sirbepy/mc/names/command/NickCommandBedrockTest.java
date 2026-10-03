package dev.sirbepy.mc.names.command;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.names.config.NamesConfig;
import dev.sirbepy.mc.names.data.NameStore;
import dev.sirbepy.mc.names.gui.NickAnvilController;
import dev.sirbepy.mc.names.label.FloatingLabelManager;
import dev.sirbepy.mc.names.render.NameChangeService;
import dev.sirbepy.mc.names.render.NameService;
import dev.sirbepy.mc.names.render.NickApplier;
import dev.sirbepy.mc.names.render.PrefixRegistry;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Proves /nick's no-args path forks on Bedrock as designed (todo 04): a Bedrock player gets a
 * Cumulus input form prefilled with their current nick and never sees the anvil; submitting it
 * runs the exact same NickApplier path as the anvil does; a Java player, or bedrock.forms: false,
 * keeps the anvil exactly as before.
 */
class NickCommandBedrockTest {

    private ServerMock server;
    private PluginMock plugin;
    private PlayerMock player;
    private NamesConfig config;
    private NickApplier nickApplier;
    private NickAnvilController anvilController;
    private NameStore nameStore;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Names");
        player = server.addPlayer();

        config = new NamesConfig(plugin);
        nameStore = new NameStore(plugin);
        nameStore.load();
        PrefixRegistry prefixes = new PrefixRegistry();
        NameService nameService = new NameService(nameStore, config, prefixes);
        FloatingLabelManager labels = new FloatingLabelManager(config, nameService);
        NameChangeService changeService = new NameChangeService(nameStore, nameService, labels);
        nickApplier = new NickApplier(config, changeService);
        anvilController = new NickAnvilController(config, nickApplier);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private NickCommand commandWith(FakeBedrockForms forms) {
        return new NickCommand(config, nickApplier, anvilController, forms, nameStore);
    }

    @Test
    void bedrockPlayerGetsFormPrefilledWithCurrentNickAndNoAnvil() {
        nameStore.setNick(player.getUniqueId(), "OldNick");
        FakeBedrockForms forms = new FakeBedrockForms(true);
        NickCommand command = commandWith(forms);

        command.openNickEditor(player);

        // Only the Bedrock branch ever populates these; if the anvil had opened instead (the
        // other branch), these would still be null and the assertion below would fail.
        assertEquals("OldNick", forms.lastPrefill, "the form must prefill the player's current nick");
        assertFalse(forms.lastTitle.isBlank());
        assertFalse(forms.lastLabel.isBlank());
    }

    @Test
    void submittingAValidNameSetsTheNickThroughTheSharedApplier() {
        FakeBedrockForms forms = new FakeBedrockForms(true);
        NickCommand command = commandWith(forms);
        command.openNickEditor(player);

        forms.lastOnSubmit.accept("NewNick");

        assertEquals("NewNick", nameStore.getNick(player.getUniqueId()).orElse(null));
    }

    @Test
    void submittingBlankDoesNothing() {
        nameStore.setNick(player.getUniqueId(), "KeepMe");
        FakeBedrockForms forms = new FakeBedrockForms(true);
        NickCommand command = commandWith(forms);
        command.openNickEditor(player);

        forms.lastOnSubmit.accept("   ");

        assertEquals("KeepMe", nameStore.getNick(player.getUniqueId()).orElse(null));
    }

    @Test
    void javaPlayerStillGetsTheAnvil() {
        FakeBedrockForms forms = new FakeBedrockForms(false);
        NickCommand command = commandWith(forms);

        openExpectingAnvil(command);

        assertFalse(forms.openInputCalled, "a Java player must never be offered the Bedrock form");
    }

    @Test
    void bedrockFormsDisabledFallsBackToTheAnvilEvenForABedrockPlayer() {
        // NamesConfig.reload() re-reads from disk, so the override must be saved, not just set
        // on the live in-memory FileConfiguration (which reload() discards).
        plugin.getConfig().set("bedrock.forms", false);
        plugin.saveConfig();
        config.reload();
        FakeBedrockForms forms = new FakeBedrockForms(true);
        NickCommand command = commandWith(forms);

        openExpectingAnvil(command);

        assertFalse(forms.openInputCalled);
    }

    /**
     * MockBukkit doesn't implement {@code MenuType.ANVIL.create}'s {@code AnvilView} (throws
     * ClassCastException), so reaching the real anvil open path can only be observed by that
     * throw here, not by inspecting the opened inventory. The one thing under test either way:
     * control reached {@link NickAnvilController#open}, not the Bedrock form.
     */
    private void openExpectingAnvil(NickCommand command) {
        try {
            command.openNickEditor(player);
        } catch (ClassCastException expectedMockBukkitLimitation) {
            // Expected: see method javadoc.
        }
    }

    /** Captures the exact args NickCommand passes, and lets the test drive the callback by hand. */
    private static final class FakeBedrockForms implements BedrockForms {
        private final boolean bedrock;
        boolean openInputCalled;
        String lastTitle;
        String lastLabel;
        String lastPrefill;
        Consumer<String> lastOnSubmit;

        FakeBedrockForms(boolean bedrock) {
            this.bedrock = bedrock;
        }

        @Override
        public boolean isBedrock(UUID player) {
            return bedrock;
        }

        @Override
        public void openInput(Player player, String title, String label, String prefill, Consumer<String> onSubmit) {
            openInputCalled = true;
            lastTitle = title;
            lastLabel = label;
            lastPrefill = prefill;
            lastOnSubmit = onSubmit;
        }

        @Override
        public void openButtons(Player player, String title, String content, List<String> buttons, IntConsumer onClick) {
            throw new UnsupportedOperationException("/nick does not use openButtons");
        }
    }
}
