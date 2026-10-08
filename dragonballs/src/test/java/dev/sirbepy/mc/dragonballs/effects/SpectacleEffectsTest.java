package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.ItemDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end wiring: a summon darkens the sky immediately (Overworld only) and spawns the dragon
 * once the ball-strike sequence completes, ending it despawns the dragon and launches the balls,
 * and shutdown clears everything with no animation.
 *
 * <p>The dragon-spawn-after-the-sequence timing is checked using a Nether scene rather than the
 * shared Overworld one: the Overworld/End strike is real lightning, and MockBukkit's
 * {@code World#strikeLightningEffect} aborts the whole test (see BallStrikeSequenceTest) rather
 * than just failing an assertion, so advancing ticks far enough to run it here would be unsafe.
 */
class SpectacleEffectsTest {

    private ServerMock server;
    private WorldMock world;
    private PluginMock plugin;
    private SpectacleEffects effects;
    private SummonScene scene;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        BallItems ballItems = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        effects = new SpectacleEffects(plugin, ballItems, uuid -> false, name -> { });
        Map<Integer, Location> origins = Map.of(1, new Location(world, 0, 70, 0));
        scene = new SummonScene(UUID.randomUUID(), "Goku", DragonType.SHENRON, BallSet.OVERWORLD,
                new Location(world, 0, 70, 0), origins);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onSummonDarkensTheSkyImmediatelyForTheOverworld() {
        PlayerMock player = server.addPlayer();
        player.setLocation(new Location(world, 0, 70, 0));

        effects.onSummon(scene);

        assertEquals(WeatherType.DOWNFALL, player.getPlayerWeather());
    }

    @Test
    void onSummonNeverDarkensTheSkyForTheNether() {
        PlayerMock player = server.addPlayer();
        player.setLocation(new Location(world, 0, 70, 0));
        SummonScene netherScene = new SummonScene(UUID.randomUUID(), "Goku", DragonType.SHENRON, BallSet.NETHER,
                scene.center(), scene.ballOrigins());

        effects.onSummon(netherScene);

        assertNotEquals(WeatherType.DOWNFALL, player.getPlayerWeather(),
            "the Nether has no sky to darken, so SkyEffect must stay a no-op there");
    }

    @Test
    void onSummonSpawnsTheDragonOnlyAfterTheStrikeSequenceCompletes() {
        SummonScene netherScene = new SummonScene(UUID.randomUUID(), "Goku", DragonType.SHENRON, BallSet.NETHER,
                scene.center(), scene.ballOrigins());

        effects.onSummon(netherScene);
        server.getScheduler().performTicks(BallStrikeSequence.STEP_TICKS - 1);
        assertTrue(world.getEntitiesByClass(EnderDragon.class).isEmpty(),
            "the dragon must wait for the single ball's own strike step to finish");

        server.getScheduler().performTicks(1);
        assertEquals(1, world.getEntitiesByClass(EnderDragon.class).size());
    }

    @Test
    void onEndRemovesTheDragonAndLaunchesTheBalls() {
        effects.onSummon(scene);

        effects.onEnd(scene, Map.of());

        assertTrue(world.getEntitiesByClass(EnderDragon.class).isEmpty());
        assertEquals(1, world.getEntitiesByClass(ItemDisplay.class).size());
    }

    @Test
    void shutdownClearsEverythingImmediately() {
        effects.onSummon(scene);
        effects.onEnd(scene, Map.of());

        effects.shutdown();

        assertTrue(world.getEntitiesByClass(EnderDragon.class).isEmpty());
        assertTrue(world.getEntitiesByClass(ItemDisplay.class).isEmpty());
    }

    @Test
    void onSpeakDoesNothingHarmfulWithoutAnActiveDragon() {
        assertDoesNotThrow(() -> effects.onSpeak(scene, "hello"));
    }
}
