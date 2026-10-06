package dev.sirbepy.mc.dragonballs.effects;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end wiring: a summon spawns the dragon and darkens the sky, ending it despawns the
 * dragon and launches the balls, and shutdown clears everything with no animation. */
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
        effects = new SpectacleEffects(plugin, ballItems);
        Map<Integer, Location> origins = Map.of(1, new Location(world, 0, 70, 0));
        scene = new SummonScene(UUID.randomUUID(), "Goku", DragonType.SHENRON, dev.sirbepy.mc.dragonballs.ball.BallSet.OVERWORLD,
                new Location(world, 0, 70, 0), origins);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onSummonSpawnsTheDragonAndDarkensTheSkyForPlayersInTheWorld() {
        PlayerMock player = server.addPlayer();
        player.setLocation(new Location(world, 0, 70, 0));

        effects.onSummon(scene);

        assertEquals(1, world.getEntitiesByClass(EnderDragon.class).size());
        assertEquals(WeatherType.DOWNFALL, player.getPlayerWeather());
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
