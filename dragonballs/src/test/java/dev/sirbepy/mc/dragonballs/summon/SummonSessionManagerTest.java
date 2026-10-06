package dev.sirbepy.mc.dragonballs.summon;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.effects.DragonType;
import dev.sirbepy.mc.dragonballs.effects.SummonEffects;
import dev.sirbepy.mc.dragonballs.effects.SummonScene;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallScatterer;
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
 * MockBukkit coverage for the session orchestration: starting (and refusing to start), the judge's
 * grant/dismiss ending a session through the exact {@code scatterAll(center, true)} +
 * {@code effects.onEnd} path, and the idle timeout firing through a real scheduled task.
 */
class SummonSessionManagerTest {

    private ServerMock server;
    private WorldMock world;
    private BallRegistry registry;
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
        plugin = MockBukkit.createMockPlugin();
        BallItems items = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        registry = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items);
        scatterer = new FakeScatterer();
        registry.setScatterer(scatterer);
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
        return new SummonSessionManager(plugin.getLogger(), registry, () -> new DragonBallsConfig(new YamlConfiguration()),
                () -> new SummonConfig(2, 600, 180), effects, curses, wishesFile, () -> clockBox[0], new Random(1));
    }

    /** Places all 7 together, PLACED, non-stone, within radius 2 of {@code (100,64,100)}. */
    private void placeAllSevenTogether() {
        for (int star = 1; star <= 7; star++) {
            registry.markPlaced(star, new Location(world, 100 + (star % 2), 64, 100 + (star % 2)), false);
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

        boolean started = sessions.tryStart(player, DragonType.SHENRON, new Location(world, 100, 64, 100));

        assertFalse(started);
        assertFalse(sessions.hasActiveSession());
    }

    @Test
    void tryStartFailsWhenDormant() {
        placeAllSevenTogether();
        registry.setStoneUntil(dev.sirbepy.mc.dragonballs.config.StoneMode.MINECRAFT_DAYS, 999);
        SummonSessionManager sessions = newManager();
        PlayerMock player = server.addPlayer();

        boolean started = sessions.tryStart(player, DragonType.SHENRON, new Location(world, 100, 64, 100));

        assertFalse(started);
        assertFalse(sessions.hasActiveSession());
    }

    @Test
    void tryStartSucceedsAndCallsOnSummon() {
        placeAllSevenTogether();
        SummonSessionManager sessions = newManager();
        PlayerMock player = server.addPlayer("Goku");

        boolean started = sessions.tryStart(player, DragonType.PORUNGA, new Location(world, 100, 64, 100));

        assertTrue(started);
        assertTrue(sessions.hasActiveSession());
        assertEquals(3, sessions.wishesLeft());
        assertEquals(DragonType.PORUNGA, sessions.activeDragon());
        assertEquals(player.getUniqueId(), sessions.activeSummoner());
        assertNotNull(effects.lastSummonScene);
    }

    @Test
    void grantingTheLastWishEndsTheSessionAndScattersWithStoneTrue() {
        placeAllSevenTogether();
        SummonSessionManager sessions = newManager();
        PlayerMock player = server.addPlayer("Goku");
        sessions.tryStart(player, DragonType.SHENRON, new Location(world, 100, 64, 100));

        String error = sessions.grant(1, "granted the one wish");

        assertEquals(null, error);
        assertFalse(sessions.hasActiveSession());
        assertTrue(scatterer.scatterAllCalled);
        assertTrue(scatterer.lastStoneFlag, "the post-wish scatter must mark the balls stone");
        assertNotNull(effects.lastEndScene, "effects.onEnd must be called when the session ends normally");
        assertEquals(1, registry.wishCount(), "a granted wish increments the registry's wish count");
    }

    @Test
    void overGrantingIsRejectedAndTheSessionStaysOpen() {
        placeAllSevenTogether();
        SummonSessionManager sessions = newManager();
        PlayerMock player = server.addPlayer("Goku");
        sessions.tryStart(player, DragonType.PORUNGA, new Location(world, 100, 64, 100));

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
        sessions.tryStart(player, DragonType.PORUNGA, new Location(world, 100, 64, 100));

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
        sessions.tryStart(player, DragonType.SHENRON, new Location(world, 100, 64, 100));

        sessions.dismiss(null);

        assertEquals(0, registry.wishCount());
    }

    @Test
    void idleTimeoutFiresThroughARealScheduledTickAndEndsTheSession() {
        placeAllSevenTogether();
        SummonSessionManager sessions = newManager();
        PlayerMock player = server.addPlayer("Goku");
        sessions.tryStart(player, DragonType.SHENRON, new Location(world, 100, 64, 100));
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
