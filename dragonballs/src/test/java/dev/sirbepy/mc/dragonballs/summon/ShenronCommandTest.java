package dev.sirbepy.mc.dragonballs.summon;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.config.StoneMode;
import dev.sirbepy.mc.dragonballs.effects.DragonType;
import dev.sirbepy.mc.dragonballs.effects.SummonEffects;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallScatterer;
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
import java.nio.file.Path;
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
    private PluginMock plugin;
    private BallRegistry registry;
    private SummonSessionManager sessions;
    private ShenronCommand command;
    private ConsoleCommandSenderMock console;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        BallItems items = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        registry = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items);
        registry.setScatterer(new BallScatterer() {
            @Override
            public Map<Integer, Location> scatterAll(Location center, boolean stone, BallRegistry r) {
                return Map.of();
            }

            @Override
            public void scatterOne(int star, Location center, boolean stone, BallRegistry r) {
            }
        });
        sessions = new SummonSessionManager(plugin.getLogger(), registry, () -> new DragonBallsConfig(new YamlConfiguration()),
                () -> new SummonConfig(2, 600, 180), SummonEffects.NONE,
                new CurseStore(new File(tempDir.toFile(), "curses.yml"), plugin.getLogger()),
                new File(tempDir.toFile(), "wishes.jsonl"), System::currentTimeMillis, new Random(1));
        CurseStore curses = new CurseStore(new File(tempDir.toFile(), "curses2.yml"), plugin.getLogger());
        CurseListener curseListener = new CurseListener(curses, System::currentTimeMillis);
        command = new ShenronCommand(sessions, curses, curseListener, registry);
        console = new ConsoleCommandSenderMock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private String nextConsoleMessage() {
        return PlainTextComponentSerializer.plainText().serialize(console.nextComponentMessage());
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
        sessions.tryStart(first, DragonType.SHENRON, new Location(world, 100, 64, 100));
        server.addPlayer("Vegeta");

        command.summon(console, "Vegeta", "porunga");

        assertEquals(DragonType.SHENRON, sessions.activeDragon(), "the first session must stay active, untouched");
        assertEquals(first.getUniqueId(), sessions.activeSummoner());
    }
}
