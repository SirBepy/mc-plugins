package dev.sirbepy.mc.claudebridge.command;

import dev.sirbepy.mc.claudebridge.config.ClaudeBridgeConfig;
import dev.sirbepy.mc.claudebridge.cooldown.CooldownTracker;
import dev.sirbepy.mc.claudebridge.heartbeat.HeartbeatTracker;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link ClaudeCommand#handle} directly (the sender-type/permission checks in execute()
 * need a real Brigadier CommandSourceStack, which the rest of this repo's command tests avoid
 * building too; see NickCommandBedrockTest). Covers the spec's accept-path contract: the exact
 * console log line, the inbox.jsonl append, and the cooldown gate.
 */
class ClaudeCommandTest {

    private ServerMock server;
    private PluginMock plugin;
    private PlayerMock player;
    private File inboxFile;
    private AtomicLong clock;
    private List<String> logMessages;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("ClaudeBridge");
        player = server.addPlayer("Steve");
        inboxFile = new File(tempDir.toFile(), "inbox.jsonl");
        clock = new AtomicLong(0L);

        logMessages = new ArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logMessages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        plugin.getLogger().setUseParentHandlers(false);
        plugin.getLogger().addHandler(capture);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ClaudeBridgeConfig(plugin) calls plugin.reloadConfig() internally, which (for a mock plugin
    // with no backing jar/file) discards any plugin.getConfig().set(...) seeded beforehand and
    // falls back to the hardcoded default - 20s, same as the spec and config.yml's own default -
    // so no seeding is needed here.
    //
    // Heartbeat defaults to "just received" here so these accept-path tests keep exercising the
    // "sent" reply they were written against; the online/offline branch gets its own tests below.
    private ClaudeCommand commandWithDefaultCooldown() {
        ClaudeBridgeConfig config = new ClaudeBridgeConfig(plugin);
        CooldownTracker cooldown = new CooldownTracker(clock::get);
        HeartbeatTracker heartbeat = new HeartbeatTracker(clock::get);
        heartbeat.recordHeartbeat();
        return new ClaudeCommand(plugin, config, cooldown, inboxFile, heartbeat);
    }

    private ClaudeCommand commandWithHeartbeat(HeartbeatTracker heartbeat) {
        ClaudeBridgeConfig config = new ClaudeBridgeConfig(plugin);
        CooldownTracker cooldown = new CooldownTracker(clock::get);
        return new ClaudeCommand(plugin, config, cooldown, inboxFile, heartbeat);
    }

    private List<String> inboxLines() throws Exception {
        if (!inboxFile.exists()) {
            return List.of();
        }
        return Files.readAllLines(inboxFile.toPath(), StandardCharsets.UTF_8);
    }

    @Test
    void acceptedMessageLogsTheExactConsoleLine() {
        ClaudeCommand command = commandWithDefaultCooldown();

        command.handle(player, "how do I craft a beacon?");

        assertEquals(1, logMessages.size());
        assertEquals("[ClaudeBridge] Steve (" + player.getUniqueId() + "): how do I craft a beacon?", logMessages.get(0));
    }

    @Test
    void acceptedMessageStripsNewlinesFromTheLoggedLine() {
        ClaudeCommand command = commandWithDefaultCooldown();

        // The validator already strips every control character (newlines included) before this
        // reaches the log line, so "removed" means gone, not replaced with a space.
        command.handle(player, "line one\nline two");

        assertEquals(1, logMessages.size());
        assertFalse(logMessages.get(0).contains("\n"));
        assertTrue(logMessages.get(0).endsWith("line oneline two"));
    }

    @Test
    void acceptedMessageAppendsOneInboxLineWithAllFields() throws Exception {
        ClaudeCommand command = commandWithDefaultCooldown();

        command.handle(player, "hello there");

        List<String> lines = inboxLines();
        assertEquals(1, lines.size());
        String line = lines.get(0);
        assertTrue(line.contains("\"player\":\"Steve\""), line);
        assertTrue(line.contains("\"uuid\":\"" + player.getUniqueId() + "\""), line);
        assertTrue(line.contains("\"message\":\"hello there\""), line);
        assertTrue(line.contains("\"time\":\""), line);
    }

    @Test
    void blankMessageIsRejectedWithNoLogAndNoInboxEntry() throws Exception {
        ClaudeCommand command = commandWithDefaultCooldown();

        command.handle(player, "   ");

        assertEquals(0, logMessages.size());
        assertEquals(0, inboxLines().size());
    }

    @Test
    void tooLongMessageIsRejectedWithNoLogAndNoInboxEntry() throws Exception {
        ClaudeCommand command = commandWithDefaultCooldown();

        command.handle(player, "a".repeat(257));

        assertEquals(0, logMessages.size());
        assertEquals(0, inboxLines().size());
    }

    @Test
    void secondMessageWithinCooldownWindowIsRejected() throws Exception {
        ClaudeCommand command = commandWithDefaultCooldown();

        command.handle(player, "first message");
        command.handle(player, "second message");

        // Only the first accepted message produced a log line / inbox entry; the cooldown
        // blocked the second without touching either.
        assertEquals(1, logMessages.size());
        assertEquals(1, inboxLines().size());
    }

    @Test
    void messageAfterCooldownElapsesIsAccepted() throws Exception {
        ClaudeCommand command = commandWithDefaultCooldown();

        command.handle(player, "first message");
        clock.set(20_000L);
        command.handle(player, "second message");

        assertEquals(2, logMessages.size());
        assertEquals(2, inboxLines().size());
    }

    @Test
    void rejectedAttemptDuringCooldownDoesNotExtendIt() throws Exception {
        ClaudeCommand command = commandWithDefaultCooldown();

        command.handle(player, "first message");
        clock.set(10_000L);
        command.handle(player, ""); // rejected as blank, must not touch the cooldown clock
        clock.set(20_000L);
        command.handle(player, "second message");

        assertEquals(2, logMessages.size());
        assertEquals(2, inboxLines().size());
    }

    // ClaudeBridgeConfig(plugin) on this mock plugin has no backing jar, so its "messages" config
    // section is empty and Messages.render falls back to "[missing message: <key>]" (see
    // Messages#render) - same fallback NamesReloadCommandTest documents for a missing key. That
    // fallback still proves which key got picked, which is exactly what these tests check: the
    // online/offline branch in ClaudeCommand#handle, not the configured wording (covered by
    // config.yml itself + the live text chosen in this feature's commit).

    @Test
    void recentHeartbeatGetsTheSentReply() throws Exception {
        HeartbeatTracker heartbeat = new HeartbeatTracker(clock::get);
        heartbeat.recordHeartbeat();
        ClaudeCommand command = commandWithHeartbeat(heartbeat);

        command.handle(player, "hello");

        String text = PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
        assertEquals("[missing message: sent]", text);
        // Still written through either way: the offline branch only changes the reply, never
        // whether the message reaches Shenron.
        assertEquals(1, inboxLines().size());
    }

    @Test
    void staleHeartbeatGetsTheOfflineReply() throws Exception {
        HeartbeatTracker heartbeat = new HeartbeatTracker(clock::get);
        heartbeat.recordHeartbeat();
        clock.set(46_000L); // past the 45s default threshold
        ClaudeCommand command = commandWithHeartbeat(heartbeat);

        command.handle(player, "hello");

        String text = PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
        assertEquals("[missing message: shenron-offline]", text);
        assertEquals(1, inboxLines().size());
    }

    @Test
    void noHeartbeatEverReceivedDefaultsToOfflineReply() throws Exception {
        // A fresh plugin boot (e.g. right after a live restart) must not assume Shenron is awake
        // just because nothing has proven otherwise yet.
        HeartbeatTracker heartbeat = new HeartbeatTracker(clock::get);
        ClaudeCommand command = commandWithHeartbeat(heartbeat);

        command.handle(player, "hello");

        String text = PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
        assertEquals("[missing message: shenron-offline]", text);
    }
}
