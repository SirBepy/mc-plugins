package dev.sirbepy.mc.tweaks.restart;

import dev.sirbepy.mc.tweaks.TweaksTestMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the Bukkit wiring on top of {@link RestartCountdownTest}'s pure-logic coverage: that
 * {@code tick()} actually broadcasts to online players, that {@code GO} runs save-all and logs the
 * marker exactly once, and that {@code /dont-restart}'s "until they disconnect" half fires through
 * a real {@code PlayerQuitEvent}.
 */
class RestartCountdownManagerTest {

    private ServerMock server;
    private ConsoleCommandSenderMock console;
    private final List<String> loggedLines = new ArrayList<>();
    private final AtomicInteger saveAllCalls = new AtomicInteger();
    private long now = 1_000_000L;
    private RestartCountdownManager manager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        console = new ConsoleCommandSenderMock();
        Logger logger = Logger.getLogger("restart-countdown-test");
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                loggedLines.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        manager = new RestartCountdownManager(server, TweaksTestMessages.jarDefaults(), logger, () -> now, saveAllCalls::incrementAndGet);
        server.getPluginManager().registerEvents(manager, MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void statusAndCancelWithNothingPendingSayNoRestartIsPending() {
        manager.status(console);
        assertTrue(plain(console.nextMessage()).contains("No restart is pending"));
        manager.cancel(console);
        assertTrue(plain(console.nextMessage()).contains("No restart is pending"));
    }

    @Test
    void startBroadcastsThe120sWarningToEveryoneOnline() {
        PlayerMock alice = server.addPlayer("Alice");
        manager.start(console, "tok1", "jar update");
        String warning = plain(alice.nextMessage());
        assertTrue(warning.contains("120 seconds"), warning);
        assertTrue(warning.contains("dont-restart") || warning.contains("/dont-restart"), warning);
    }

    @Test
    void reachingZeroWithNoDelaysSavesAllAndLogsTheGoMarkerExactlyOnce() {
        manager.start(console, "tok7", "jar update");
        for (int i = 0; i < 120; i++) manager.tick();
        assertEquals(1, saveAllCalls.get());
        assertEquals(List.of("RESTART-GO tok7"), loggedLines);

        // a dead countdown ticks quietly forever, never re-firing
        for (int i = 0; i < 5; i++) manager.tick();
        assertEquals(1, saveAllCalls.get());
        assertEquals(1, loggedLines.size());
    }

    @Test
    void dontRestartWithNoPendingRestartSaysSoAndDoesNothing() {
        PlayerMock alice = server.addPlayer("Alice");
        manager.dontRestart(alice);
        String text = plain(alice.nextMessage());
        assertTrue(text.toLowerCase().contains("no restart"), text);
    }

    @Test
    void dontRestartDelaysTheGoMarkerAndBroadcastsToOthers() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        manager.start(console, "tok1", "jar update");
        alice.nextMessage(); // the 120s warning
        bob.nextMessage();

        manager.dontRestart(alice);
        assertTrue(plain(alice.nextMessage()).toLowerCase().contains("pushed"));
        String broadcastToBob = plain(bob.nextMessage());
        assertTrue(broadcastToBob.contains("Alice"), broadcastToBob);

        for (int i = 0; i < 120; i++) manager.tick();
        assertEquals(0, saveAllCalls.get(), "held: Alice is still delaying it");
    }

    @Test
    void aPlayerDisconnectingClearsTheirDelayAndLetsAHeldRestartReArm() {
        PlayerMock alice = server.addPlayer("Alice");
        manager.start(console, "tok1", "jar update");
        manager.dontRestart(alice);
        for (int i = 0; i < 120; i++) manager.tick();
        assertEquals(0, saveAllCalls.get());

        manager.onQuit(new org.bukkit.event.player.PlayerQuitEvent(alice, net.kyori.adventure.text.Component.empty(),
                org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED));
        manager.tick(); // the rearm event
        assertFalse(saveAllCalls.get() > 0);

        for (int i = 0; i < 120; i++) manager.tick();
        assertEquals(1, saveAllCalls.get(), "no one is delaying it anymore, so the re-armed countdown finishes");
    }

    private static String plain(String legacyText) {
        return legacyText == null ? "" : legacyText;
    }
}
