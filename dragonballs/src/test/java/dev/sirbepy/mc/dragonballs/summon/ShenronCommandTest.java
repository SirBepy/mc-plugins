package dev.sirbepy.mc.dragonballs.summon;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.config.StoneMode;
import dev.sirbepy.mc.dragonballs.effects.DragonType;
import dev.sirbepy.mc.dragonballs.effects.SummonEffects;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallScatterer;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.summon.curse.CurseListener;
import dev.sirbepy.mc.dragonballs.summon.curse.CurseStore;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.command.RemoteConsoleCommandSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@code /shenron summon <player> <dragon>}, the debug-only path a bot/script uses because
 * it can't answer the real right-click dialog. Drives {@link ShenronCommand#summon(CommandSender,
 * String, String)} directly (same reason {@code ClaudeCommandTest} drives {@code handle} directly:
 * building a real Brigadier {@code CommandSourceStack} buys nothing here), and checks the exact
 * same re-validation {@link SummonSessionManager#tryStart} already does for a real dialog click.
 */
class ShenronCommandTest {

    private ServerMock server;
    private WorldMock world;
    private WorldMock netherWorld;
    private PluginMock plugin;
    private BallRegistry registry;
    private BallRegistry netherRegistry;
    private BallSets sets;
    private SummonSessionManager sessions;
    private ShenronCommand command;
    private ConsoleCommandSenderMock console;
    private File wishesFile;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        netherWorld = server.addSimpleWorld("world_nether");
        plugin = MockBukkit.createMockPlugin();
        BallItems items = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        BallScatterer noopScatterer = new BallScatterer() {
            @Override
            public Map<Integer, Location> scatterAll(Location center, boolean stone, BallRegistry r) {
                return Map.of();
            }

            @Override
            public void scatterOne(int star, Location center, boolean stone, BallRegistry r) {
            }
        };
        registry = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items,
                BallSet.OVERWORLD);
        registry.setScatterer(noopScatterer);
        netherRegistry = new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), items,
                BallSet.NETHER);
        netherRegistry.setScatterer(noopScatterer);
        BallRegistry endRegistry = new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), items,
                BallSet.END);
        endRegistry.setScatterer(noopScatterer);
        sets = new BallSets(registry, netherRegistry, endRegistry, items, () -> new DragonBallsConfig(new YamlConfiguration()));
        wishesFile = new File(tempDir.toFile(), "wishes.jsonl");
        sessions = new SummonSessionManager(plugin.getLogger(), sets, () -> new DragonBallsConfig(new YamlConfiguration()),
                () -> new SummonConfig(2, 600, 180), SummonEffects.NONE,
                new CurseStore(new File(tempDir.toFile(), "curses.yml"), plugin.getLogger()),
                wishesFile, System::currentTimeMillis, new Random(1));
        CurseStore curses = new CurseStore(new File(tempDir.toFile(), "curses2.yml"), plugin.getLogger());
        CurseListener curseListener = new CurseListener(curses, System::currentTimeMillis);
        command = new ShenronCommand(sessions, curses, curseListener, sets);
        console = new ConsoleCommandSenderMock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private String nextConsoleMessage() {
        return PlainTextComponentSerializer.plainText().serialize(console.nextComponentMessage());
    }

    /** Pulls the active session's id out of {@link SummonSessionManager#statusText}, which starts
     * {@code "session=<id> set=..."}; there is no narrower getter, and parsing the one line this
     * class already exposes is simpler than adding one just for tests. */
    private String activeSessionId() {
        String status = sessions.statusText();
        String prefix = "session=";
        return status.substring(prefix.length(), status.indexOf(' ', prefix.length()));
    }

    /** Places all 7 together, PLACED, non-stone, within radius 2 of {@code (100,64,100)}. */
    private void placeAllSevenTogether() {
        for (int star = 1; star <= 7; star++) {
            registry.markPlaced(star, new Location(world, 100 + (star % 2), 64, 100 + (star % 2)), false);
        }
    }

    @Test
    void theConsoleAndRconMayJudgeButNoPlayerMay() {
        RemoteConsoleCommandSender rcon = (RemoteConsoleCommandSender) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{RemoteConsoleCommandSender.class},
                (proxy, method, args) -> method.getName().equals("hasPermission") ? Boolean.TRUE : null);
        PlayerMock op = server.addPlayer("Op");
        op.setOp(true);

        assertTrue(ShenronCommand.isJudge(console));
        assertTrue(ShenronCommand.isJudge(rcon), "the e2e suite and remote admin tools drive the dragon over RCON");
        assertFalse(ShenronCommand.isJudge(op), "an op player must never speak as the dragon");
    }

    @Test
    void summonStartsASessionWhenTheSetIsReady() {
        placeAllSevenTogether();
        PlayerMock player = server.addPlayer("Goku");

        command.summon(console, "Goku", "porunga");

        assertTrue(sessions.hasActiveSession());
        assertEquals(DragonType.PORUNGA, sessions.activeDragon());
        assertEquals(player.getUniqueId(), sessions.activeSummoner());
    }

    @Test
    void summonAcceptsShenronCaseInsensitively() {
        placeAllSevenTogether();
        server.addPlayer("Goku");

        command.summon(console, "Goku", "SHENRON");

        assertTrue(sessions.hasActiveSession());
        assertEquals(DragonType.SHENRON, sessions.activeDragon());
    }

    @Test
    void summonReportsAnOfflinePlayerAndStartsNoSession() {
        placeAllSevenTogether();

        command.summon(console, "Ghost", "shenron");

        assertFalse(sessions.hasActiveSession());
        assertTrue(nextConsoleMessage().contains("not online"));
    }

    @Test
    void summonRejectsAnUnknownDragonNameAndStartsNoSession() {
        placeAllSevenTogether();
        server.addPlayer("Goku");

        command.summon(console, "Goku", "mewtwo");

        assertFalse(sessions.hasActiveSession());
        assertTrue(nextConsoleMessage().contains("Unknown dragon"));
    }

    @Test
    void summonRefusesWhenNoBallIsPlacedAndStartsNoSession() {
        // All 7 held, none PLACED: anyPlacedBallLocation() has nothing to anchor tryStart's radius
        // check to, the same as a hunt where every ball is still out in players' inventories.
        PlayerMock holder = server.addPlayer("Krillin");
        for (int star = 1; star <= 7; star++) {
            registry.markHeld(star, holder, false);
        }
        server.addPlayer("Goku");

        command.summon(console, "Goku", "shenron");

        assertFalse(sessions.hasActiveSession());
        assertTrue(nextConsoleMessage().contains("No placed Dragon Ball"));
    }

    @Test
    void summonRefusesWhenTheSetIsIncompleteAndStartsNoSession() {
        for (int star = 1; star <= 6; star++) {
            registry.markPlaced(star, new Location(world, 100, 64, 100), false);
        }
        registry.markPlaced(7, new Location(world, 900, 64, 900), false);
        server.addPlayer("Goku");

        command.summon(console, "Goku", "shenron");

        assertFalse(sessions.hasActiveSession());
        assertTrue(nextConsoleMessage().contains("Cannot summon"));
    }

    @Test
    void summonRefusesWhileDormantAndStartsNoSession() {
        placeAllSevenTogether();
        registry.setStoneUntil(StoneMode.MINECRAFT_DAYS, 999);
        server.addPlayer("Goku");

        command.summon(console, "Goku", "shenron");

        assertFalse(sessions.hasActiveSession());
        assertTrue(nextConsoleMessage().contains("Cannot summon"));
    }

    @Test
    void summonRefusesWhileASessionIsAlreadyActive() {
        placeAllSevenTogether();
        PlayerMock first = server.addPlayer("Goku");
        sessions.tryStart(first, DragonType.SHENRON, new Location(world, 100, 64, 100), BallSet.OVERWORLD);
        server.addPlayer("Vegeta");

        command.summon(console, "Vegeta", "porunga");

        assertEquals(DragonType.SHENRON, sessions.activeDragon(), "the first session must stay active, untouched");
        assertEquals(first.getUniqueId(), sessions.activeSummoner());
    }

    @Test
    void summonWithAnExplicitSetUsesThatSetsOwnBalls() {
        placeAllSevenNetherTogether();
        PlayerMock player = server.addPlayer("Goku");

        command.summon(console, "Goku", "shenron", "nether");

        assertTrue(sessions.hasActiveSession());
        assertEquals(player.getUniqueId(), sessions.activeSummoner());
    }

    @Test
    void summonWithNoSetPicksTheFirstReadySetWhenOverworldIsIncomplete() {
        // Overworld stays incomplete; only the Nether set is fully placed together.
        placeAllSevenNetherTogether();
        server.addPlayer("Goku");

        command.summon(console, "Goku", "shenron");

        assertTrue(sessions.hasActiveSession());
    }

    @Test
    void summonRejectsAnUnknownSetNameAndStartsNoSession() {
        placeAllSevenTogether();
        server.addPlayer("Goku");

        command.summon(console, "Goku", "shenron", "moon");

        assertFalse(sessions.hasActiveSession());
        assertTrue(nextConsoleMessage().contains("Unknown set"));
    }

    @Test
    void statusTextNamesTheActiveSessionsSet() {
        placeAllSevenNetherTogether();
        PlayerMock player = server.addPlayer("Goku");
        sessions.tryStart(player, DragonType.SHENRON, new Location(netherWorld, 100, 64, 100), BallSet.NETHER);

        assertTrue(sessions.statusText().contains("set=nether"));
    }

    /** Places all 7 together, PLACED, non-stone, within radius 2 of {@code (100,64,100)} in the Nether. */
    private void placeAllSevenNetherTogether() {
        for (int star = 1; star <= 7; star++) {
            netherRegistry.markPlaced(star, new Location(netherWorld, 100 + (star % 2), 64, 100 + (star % 2)), false);
        }
    }

    @Test
    void publicRefusesOnAMissingSeparatorAndWritesNothing() throws IOException {
        command.publicize(console, "abc123", "Title without a separator");

        assertTrue(nextConsoleMessage().contains("Usage:"));
        assertFalse(wishesFile.isFile(), "no file should be created for a refused request");
    }

    @Test
    void publicRefusesASessionWithNoGrantLogged() {
        command.publicize(console, "nosuchsession", "Found treasure | Became rich");

        assertTrue(nextConsoleMessage().contains("no grant logged for session nosuchsession"));
    }

    @Test
    void publicRefusesATitleOverSixtyChars() {
        String longTitle = "x".repeat(61);

        command.publicize(console, "abc123", longTitle + " | a line");

        assertTrue(nextConsoleMessage().contains("Title is too long"));
    }

    @Test
    void publicRefusesALineOverTwoHundredChars() {
        String longLine = "x".repeat(201);

        command.publicize(console, "abc123", "Title | " + longLine);

        assertTrue(nextConsoleMessage().contains("Line is too long"));
    }

    @Test
    void publicAppendsARecordForASessionWithAnExistingGrant() throws IOException {
        placeAllSevenTogether();
        server.addPlayer("Goku");
        command.summon(console, "Goku", "shenron");
        String sessionId = activeSessionId();
        sessions.grant(1, "internal note never published");

        command.publicize(console, sessionId, "Found a chest | Got 64 diamonds");

        assertTrue(nextConsoleMessage().contains("Published session " + sessionId));
        List<String> lines = Files.readAllLines(wishesFile.toPath());
        boolean foundPublicRecord = lines.stream().anyMatch(line -> line.contains("\"type\":\"public\"")
                && line.contains("\"session\":\"" + sessionId + "\"")
                && line.contains("\"title\":\"Found a chest\"")
                && line.contains("\"line\":\"Got 64 diamonds\""));
        assertTrue(foundPublicRecord, "expected a public record for session " + sessionId + " in: " + lines);
        assertFalse(lines.stream().anyMatch(line -> line.contains("internal note never published") && line.contains("public")),
                "the public record must never carry the judge's internal grant note");
    }
}
