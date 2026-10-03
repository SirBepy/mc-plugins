package dev.sirbepy.mc.safechests.command;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.safechests.SafeChestsConfig;
import dev.sirbepy.mc.safechests.bolt.BoltAccess;
import dev.sirbepy.mc.safechests.grant.FakeBoltAPI;
import dev.sirbepy.mc.safechests.grant.GrantManager;
import dev.sirbepy.mc.safechests.grant.GrantStore;
import dev.sirbepy.mc.safechests.request.AccessRequest;
import dev.sirbepy.mc.safechests.request.RequestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves /safe-chests-pending's Bedrock fork (todo 04 part B): a Bedrock owner gets the answer form
 * directly for one pending request, or a chooser first for two or more, and a click answers through
 * the exact same validated path {@link SafeChestsCommands#respondAsPlayer} gives the chat buttons. A
 * Java owner keeps the chat lines and never sees a form. Auto-open opens once per request and then
 * stays suppressed until the owner explicitly runs the command again.
 */
class SafeChestsCommandsBedrockTest {

    private ServerMock server;
    private PluginMock plugin;
    private FakeBoltAPI api;
    private BoltAccess bolt;
    private GrantManager grants;
    private RequestManager requests;
    private SafeChestsConfig config;
    private PlayerMock owner;
    private PlayerMock requester;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("SafeChests");
        api = new FakeBoltAPI();
        bolt = new BoltAccess(api);
        File store = new File(tempDir.toFile(), "grants.yml");
        grants = new GrantManager(bolt, new GrantStore(store, Logger.getAnonymousLogger()), () -> "normal");
        requests = new RequestManager();
        seedMessages(plugin);
        config = new SafeChestsConfig(plugin.getConfig());
        owner = server.addPlayer();
        requester = server.addPlayer();
    }

    /**
     * MockBukkit.createMockPlugin gives a bare plugin with no backing jar, so SafeChests' real
     * config.yml never loads; seed the exact keys this test exercises instead (same approach as
     * NickCommandBedrockTest's non-blank-only assertions, just explicit enough here to check content).
     */
    private static void seedMessages(PluginMock plugin) {
        plugin.getConfig().set("messages.request-prompt", "<requester> wants to open your chest at <x> <y> <z>");
        plugin.getConfig().set("messages.request-prompt-bedrock",
                "<requester> wants to open your chest at <x> <y> <z>. Type /safe-chests-pending to answer.");
        plugin.getConfig().set("messages.no-pending", "No pending chest requests.");
        plugin.getConfig().set("messages.form-request-title", "Chest access request");
        plugin.getConfig().set("messages.form-request-content", "<requester> wants to open your chest at <x> <y> <z>");
        plugin.getConfig().set("messages.form-button-allow-once", "Allow once");
        plugin.getConfig().set("messages.form-button-five-min", "5 minutes");
        plugin.getConfig().set("messages.form-button-always", "Always");
        plugin.getConfig().set("messages.form-button-deny", "Deny");
        plugin.getConfig().set("messages.whitelist-granted", "<owner> granted <duration>");
        plugin.getConfig().set("messages.request-denied-by-owner", "<owner> denied your request");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private SafeChestsCommands commandWith(FakeBedrockForms forms) {
        return new SafeChestsCommands(bolt, grants, requests, () -> config, () -> {}, forms);
    }

    private AccessRequest createRequest() {
        return requests.create(requester.getUniqueId(), owner.getUniqueId(), "world", 1, 64, 1, Duration.ofMinutes(2));
    }

    private String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void bedrockOwnerGetsTypedPromptAndNoAutoOpenByDefault() {
        FakeBedrockForms forms = new FakeBedrockForms(true);
        SafeChestsCommands command = commandWith(forms);
        AccessRequest request = createRequest();

        command.notifyOwnerOfRequest(owner, request);

        assertTrue(plain(owner.nextComponentMessage()).contains("/safe-chests-pending"),
                "a Bedrock owner must be told to type the command, not shown chat buttons");
        assertTrue(forms.calls.isEmpty(), "auto-open defaults to false, so no form must open on its own");
    }

    @Test
    void autoOpenFormOpensOnceAndNotAgainWhileFlaggedOpen() {
        // SafeChestsConfig is immutable per instance; production rebuilds it wholesale on
        // /safechests reload, so the test does the same instead of calling a nonexistent reload().
        plugin.getConfig().set("bedrock.auto-open-request-form", true);
        config = new SafeChestsConfig(plugin.getConfig());
        FakeBedrockForms forms = new FakeBedrockForms(true);
        SafeChestsCommands command = commandWith(forms);
        AccessRequest first = createRequest();

        command.notifyOwnerOfRequest(owner, first);
        assertEquals(1, forms.calls.size(), "auto-open must open the answer form for the first request");

        AccessRequest second = createRequest();
        command.notifyOwnerOfRequest(owner, second);
        assertEquals(1, forms.calls.size(),
                "a second request must not auto-open another form while the first is still open");
    }

    @Test
    void javaOwnerGetsChatLinesAndNoFormForPending() {
        FakeBedrockForms forms = new FakeBedrockForms(false);
        SafeChestsCommands command = commandWith(forms);
        createRequest();

        command.handlePending(owner);

        assertTrue(plain(owner.nextComponentMessage()).contains("wants to open your chest"));
        assertTrue(forms.calls.isEmpty(), "a Java owner must never be offered a form");
    }

    @Test
    void noPendingSendsTheNoPendingMessage() {
        FakeBedrockForms forms = new FakeBedrockForms(true);
        SafeChestsCommands command = commandWith(forms);

        command.handlePending(owner);

        assertTrue(plain(owner.nextComponentMessage()).contains("No pending"));
        assertTrue(forms.calls.isEmpty());
    }

    @Test
    void bedrockOwnerWithOnePendingOpensTheAnswerFormDirectly() {
        FakeBedrockForms forms = new FakeBedrockForms(true);
        SafeChestsCommands command = commandWith(forms);
        createRequest();

        command.handlePending(owner);

        assertEquals(1, forms.calls.size(), "one pending request must open the answer form directly, no chooser");
        assertEquals(4, forms.calls.get(0).buttons().size(), "allow-once / 5 min / always / deny");
    }

    @Test
    void bedrockOwnerWithTwoPendingOpensAChooserFirst() {
        FakeBedrockForms forms = new FakeBedrockForms(true);
        SafeChestsCommands command = commandWith(forms);
        createRequest();
        createRequest();

        command.handlePending(owner);

        assertEquals(1, forms.calls.size());
        assertEquals(2, forms.calls.get(0).buttons().size(), "the chooser must list one button per pending request");
    }

    @Test
    void clickingAlwaysOnTheAnswerFormGrantsTheWhitelistJustLikeTheChatButton() {
        FakeBedrockForms forms = new FakeBedrockForms(true);
        SafeChestsCommands command = commandWith(forms);
        createRequest();
        command.handlePending(owner);
        FakeBedrockForms.OpenButtonsCall answerForm = forms.calls.get(0);

        answerForm.onClick().accept(2); // "Always" is button index 2

        assertTrue(grants.isWhitelisted(owner.getUniqueId(), requester.getUniqueId()),
                "clicking Always on the form must reach the same outcome as the chat [Always] button");
        assertFalse(requests.hasOpenForm(owner.getUniqueId()), "a real click must clear the open-form flag");
    }

    @Test
    void clickingDenyOnTheAnswerFormDeniesJustLikeTheChatButton() {
        FakeBedrockForms forms = new FakeBedrockForms(true);
        SafeChestsCommands command = commandWith(forms);
        createRequest();
        command.handlePending(owner);
        FakeBedrockForms.OpenButtonsCall answerForm = forms.calls.get(0);

        answerForm.onClick().accept(3); // "Deny" is button index 3

        assertFalse(grants.isWhitelisted(owner.getUniqueId(), requester.getUniqueId()));
        assertTrue(plain(requester.nextComponentMessage()).contains("denied"));
    }

    @Test
    void bedrockFormsDisabledFallsBackToTheJavaMessageForABedrockOwner() {
        plugin.getConfig().set("bedrock.forms", false);
        config = new SafeChestsConfig(plugin.getConfig());
        FakeBedrockForms forms = new FakeBedrockForms(true);
        SafeChestsCommands command = commandWith(forms);
        createRequest();

        command.handlePending(owner);

        assertTrue(plain(owner.nextComponentMessage()).contains("wants to open your chest"));
        assertTrue(forms.calls.isEmpty());
    }

    /** Captures every openButtons call in order; openInput is never used by SafeChests. */
    private static final class FakeBedrockForms implements BedrockForms {
        private final boolean bedrock;
        final List<OpenButtonsCall> calls = new ArrayList<>();

        FakeBedrockForms(boolean bedrock) {
            this.bedrock = bedrock;
        }

        @Override
        public boolean isBedrock(UUID player) {
            return bedrock;
        }

        @Override
        public void openInput(Player player, String title, String label, String prefill, Consumer<String> onSubmit) {
            throw new UnsupportedOperationException("SafeChests never uses openInput");
        }

        @Override
        public void openButtons(Player player, String title, String content, List<String> buttons, IntConsumer onClick) {
            calls.add(new OpenButtonsCall(title, content, buttons, onClick));
        }

        record OpenButtonsCall(String title, String content, List<String> buttons, IntConsumer onClick) {
        }
    }
}
