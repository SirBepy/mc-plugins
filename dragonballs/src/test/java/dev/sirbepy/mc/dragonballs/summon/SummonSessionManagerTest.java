package dev.sirbepy.mc.dragonballs.summon;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.effects.DragonType;
import dev.sirbepy.mc.dragonballs.effects.SummonEffects;
import dev.sirbepy.mc.dragonballs.effects.SummonScene;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallScatterer;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.summon.curse.CurseStore;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MockBukkit coverage for the session orchestration: starting (and refusing to start) each of the 3
 * sets, the judge's grant/dismiss ending a session through the exact {@code scatterAll(center,
 * true)} + {@code effects.onEnd} path (scoped to that session's own set only), and the idle timeout
 * firing through a real scheduled task.
 */
class SummonSessionManagerTest {

    private ServerMock server;
    private WorldMock world;
    private WorldMock netherWorld;
    private BallSets sets;
    private BallRegistry registry;
    private BallRegistry netherRegistry;
    private FakeEffects effects;
    private FakeScatterer scatterer;
    private CurseStore curses;
    private long[] clockBox;
    private PluginMock plugin;
    private File wishesFile;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        netherWorld = server.addSimpleWorld("world_nether");
        plugin = MockBukkit.createMockPlugin();
        BallItems items = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        registry = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items,
                dev.sirbepy.mc.dragonballs.ball.BallSet.OVERWORLD);
        netherRegistry = new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), items,
                BallSet.NETHER);
        BallRegistry endRegistry = new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), items,
                BallSet.END);
        scatterer = new FakeScatterer();
        registry.setScatterer(scatterer);
        netherRegistry.setScatterer(scatterer);
        endRegistry.setScatterer(scatterer);
        sets = new BallSets(registry, netherRegistry, endRegistry, items, () -> new DragonBallsConfig(new YamlConfiguration()));
        effects = new FakeEffects();
        curses = new CurseStore(new File(tempDir.toFile(), "curses.yml"), plugin.getLogger());
        wishesFile = new File(tempDir.toFile(), "wishes.jsonl");
        clockBox = new long[]{0L};
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private SummonSessionManager newManager() {
        return new SummonSessionManager(plugin.getLogger(), sets, () -> new DragonBallsConfig(new YamlConfiguration()),
                () -> new SummonConfig(2, 600, 180), effects, curses, wishesFile, () -> clockBox[0], new Random(1));
    }

    /** Places all 7 together, PLACED, non-stone, within radius 2 of {@code (100,64,100)}. */
    private void placeAllSevenTogether() {
        for (int star = 1; star <= 7; star++) {
            registry.markPlaced(star, new Location(world, 100 + (star % 2), 64, 100 + (star % 2)), false);
        }
    }

    private void placeAllSevenNetherTogether() {
        for (int star = 1; star <= 7; star++) {
            netherRegistry.markPlaced(star, new Location(netherWorld, 100 + (star % 2), 64, 100 + (star % 2)), false);
        }
    }

    @Test
    void tryStartFailsWhenTheSetIsIncomplete() {
        // Only 6 placed together; star 7 stays far away.
        for (int star = 1; star <= 6; star++) {
            registry.markPlaced(star, new Location(world, 100, 64, 100), false);
        }
        registry.markPlaced(7, new Location(world, 900, 64, 900), false);
        SummonSessionManager sessions = newManager();
        PlayerMock player = server.addPlayer();

        boolean started = sessions.tryStart(player, DragonType.SHENRON, new Location(world, 100, 64, 100), BallSet.OVERWORLD);

        assertFalse(started);
        assertFalse(sessions.hasActiveSession());
    }

    @Test
    void tryStartFailsWhenDormant() {
        placeAllSevenTogether();
        registry.setStoneUntil(dev.sirbepy.mc.dragonballs.config.StoneMode.MINECRAFT_DAYS, 999);
        SummonSessionManager sessions = newManager();
        PlayerMock player = server.addPlayer();

        boolean started = sessions.tryStart(player, DragonType.SHENRON, new Location(world, 100, 64, 100), BallSet.OVERWORLD);

        assertFalse(started);
        assertFalse(sessions.hasActiveSession());
    }

    @Test
    void tryStartSucceedsAndCallsOnSummon() {
        placeAllSevenTogether();
        SummonSessionManager sessions = newManager();
        PlayerMock player = server.addPlayer("Goku");

        boolean started = sessions.tryStart(player, DragonType.PORUNGA, new Location(world, 100, 64, 100), BallSet.OVERWORLD);

        assertTrue(started);
        assertTrue(sessions.hasActiveSession());
        assertEquals(3, sessions.wishesLeft());
        assertEquals(DragonType.PORUNGA, sessions.activeDragon());
        assertEquals(player.getUniqueId(), sessions.activeSummoner());
        assertNotNull(effects.lastSummonScene);
    }

    @Test
    void tryStartWorksForTheNetherSetIndependentlyOfTheOverworldOne() {
        placeAllSevenNetherTogether();
        SummonSessionManager sessions = newManager();
        PlayerMock player = server.addPlayer("Goku");

        boolean started = sessions.tryStart(player, DragonType.SHENRON, new Location(netherWorld, 100, 64, 100), BallSet.NETHER);

        assertTrue(started);
        assertEquals(BallSet.NETHER, effects.lastSummonScene.set());
    }

    @Test
    void aSecondSetIsRefusedWhileASessionIsAlreadyActive() {
        placeAllSevenTogether();
        placeAllSevenNetherTogether();
        SummonSessionManager sessions = newManager();
        PlayerMock first = server.addPlayer("Goku");
        PlayerMock second = server.addPlayer("Vegeta");
        sessions.tryStart(first, DragonType.SHENRON, new Location(world, 100, 64, 100), BallSet.OVERWORLD);

        boolean secondStarted = sessions.tryStart(second, DragonType.SHENRON, new Location(netherWorld, 100, 64, 100), BallSet.NETHER);

        assertFalse(secondStarted, "only one session may run server-wide, regardless of which set it tries to use");
        assertEquals(BallSet.OVERWORLD, effects.lastSummonScene.set(), "the active session must stay the overworld one");
    }

    @Test
    void grantingTheLastWishEndsTheSessionAndScattersWithStoneTrue() {
        placeAllSevenTogether();
        SummonSessionManager sessions = newManager();
        PlayerMock player = server.addPlayer("Goku");
        sessions.tryStart(player, DragonType.SHENRON, new Location(world, 100, 64, 100), BallSet.OVERWORLD);

        String error = sessions.grant(1, "granted the one wish");

        assertEquals(null, error);
        assertFalse(sessions.hasActiveSession());
        assertTrue(scatterer.scatterAllCalled);
        assertTrue(scatterer.lastStoneFlag, "the post-wish scatter must mark the balls stone");
        assertNotNull(effects.lastEndScene, "effects.onEnd must be called when the session ends normally");
        assertEquals(1, registry.wishCount(), "a granted wish increments the registry's wish count");
    }

    @Test
    void endingANetherSessionBumpsOnlyTheNetherWishCountNotTheOverworldOne() {
        placeAllSevenNetherTogether();
        SummonSessionManager sessions = newManager();
        PlayerMock player = server.addPlayer("Goku");
        sessions.tryStart(player, DragonType.SHENRON, new Location(netherWorld, 100, 64, 100), BallSet.NETHER);

        sessions.grant(1, "granted");

        assertEquals(1, netherRegistry.wishCount());
        assertEquals(0, registry.wishCount(), "an unrelated set's wish count must never move");
    }

    @Test
    void overGrantingIsRejectedAndTheSessionStaysOpen() {
        placeAllSevenTogether();
        SummonSessionManager sessions = newManager();
        PlayerMock player = server.addPlayer("Goku");
        sessions.tryStart(player, DragonType.PORUNGA, new Location(world, 100, 64, 100), BallSet.OVERWORLD);

        String error = sessions.grant(5, "too many");

        assertNotNull(error);
        assertTrue(sessions.hasActiveSession());
        assertEquals(3, sessions.wishesLeft());
    }

    @Test
    void dismissEndsTheSessionEvenWithWishesRemaining() {
        placeAllSevenTogether();
        SummonSessionManager sessions = newManager();
        PlayerMock player = server.addPlayer("Goku");
        sessions.tryStart(player, DragonType.PORUNGA, new Location(world, 100, 64, 100), BallSet.OVERWORLD);

        String error = sessions.dismiss("Begone.");

        assertEquals(null, error);
        assertFalse(sessions.hasActiveSession());
        assertTrue(scatterer.scatterAllCalled);
    }

    @Test
    void grantAndDismissWithoutASessionReportNoDragonSummoned() {
        SummonSessionManager sessions = newManager();
        assertEquals("No dragon is summoned.", sessions.grant(1, "x"));
        assertEquals("No dragon is summoned.", sessions.dismiss("x"));
        assertEquals("No dragon is summoned.", sessions.say("x"));
    }

    @Test
    void wishCountIsNotIncrementedWhenNothingWasEverGranted() {
        placeAllSevenTogether();
        SummonSessionManager sessions = newManager();
        PlayerMock player = server.addPlayer("Goku");
        sessions.tryStart(player, DragonType.SHENRON, new Location(world, 100, 64, 100), BallSet.OVERWORLD);

        sessions.dismiss(null);

        assertEquals(0, registry.wishCount());
    }

    @Test
    void idleTimeoutFiresThroughARealScheduledTickAndEndsTheSession() {
        placeAllSevenTogether();
        SummonSessionManager sessions = newManager();
        PlayerMock player = server.addPlayer("Goku");
        sessions.tryStart(player, DragonType.SHENRON, new Location(world, 100, 64, 100), BallSet.OVERWORLD);
        assertTrue(sessions.hasActiveSession());

        // The idle timeout is configured at 600s; jump the injected clock far past it, then let a
        // scheduled task (the same shape SummonModule registers in production) run sessions.tick().
        clockBox[0] = 10 * 60 * 60 * 1000L;
        server.getScheduler().runTaskTimer(plugin, sessions::tick, 1L, 1L);
        server.getScheduler().performTicks(2);

        assertFalse(sessions.hasActiveSession(), "idle timeout must end the session");
        assertTrue(scatterer.scatterAllCalled);
    }

    /** Package-visible so it matches BallRegistryTest's own fake-scatterer style. */
    private static final class FakeScatterer implements BallScatterer {
        boolean scatterAllCalled;
        boolean lastStoneFlag;

        @Override
        public Map<Integer, Location> scatterAll(Location center, boolean stone, BallRegistry registry) {
            scatterAllCalled = true;
            lastStoneFlag = stone;
            return Map.of();
        }

        @Override
        public void scatterOne(int star, Location center, boolean stone, BallRegistry registry) {
        }
    }

    private static final class FakeEffects implements SummonEffects {
        SummonScene lastSummonScene;
        SummonScene lastEndScene;
        List<String> spoken = new java.util.ArrayList<>();

        @Override
        public void onSummon(SummonScene scene) {
            lastSummonScene = scene;
        }

        @Override
        public void onSpeak(SummonScene scene, String text) {
            spoken.add(text);
        }

        @Override
        public void onEnd(SummonScene scene, Map<Integer, Location> destinations) {
            lastEndScene = scene;
        }

        @Override
        public void shutdown() {
        }
    }
}
