package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The display an onEnd flight spawns must look like a plain head (no registry marker, so it can
 * never be mistaken for a real ball), carry only the flying-ball tag, and vanish once its
 * animation runs out - immediately on shutdown, or at the end of its flight otherwise.
 */
class FlyingBallsTest {

    private ServerMock server;
    private WorldMock world;
    private PluginMock plugin;
    private BallItems ballItems;
    private FlyingBalls flyingBalls;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        ballItems = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        flyingBalls = new FlyingBalls(plugin, ballItems);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void launchSpawnsAPlainUnmarkedHeadDisplayPerStar() {
        Map<Integer, Location> origins = Map.of(3, new Location(world, 0, 70, 0));
        Map<Integer, Location> destinations = Map.of(3, new Location(world, 100, 70, 0));

        flyingBalls.launch(origins, destinations, BallSet.OVERWORLD);

        List<ItemDisplay> displays = List.copyOf(world.getEntitiesByClass(ItemDisplay.class));
        assertEquals(1, displays.size());
        ItemDisplay display = displays.get(0);
        assertEquals(Material.PLAYER_HEAD, display.getItemStack().getType());
        assertFalse(ballItems.isBall(display.getItemStack()), "the displayed head must not be a real, registry-tracked ball");
        assertTrue(display.getScoreboardTags().contains(EffectMarkers.BALL_DISPLAY_TAG));
    }

    @Test
    void launchTexturesTheDisplayAsTheGivenSet() {
        Map<Integer, Location> origins = Map.of(4, new Location(world, 0, 70, 0));

        flyingBalls.launch(origins, Map.of(), BallSet.NETHER);

        ItemDisplay display = world.getEntitiesByClass(ItemDisplay.class).iterator().next();
        assertEquals(BallSet.NETHER, ballItems.setOf(display.getItemStack()),
                "a Nether wish's flying balls must be textured as Nether balls, not Overworld ones");
    }

    @Test
    void theDisplayVanishesOnceItsFlightRunsOut() {
        Map<Integer, Location> origins = Map.of(5, new Location(world, 0, 70, 0));
        flyingBalls.launch(origins, Map.of(), BallSet.OVERWORLD);

        server.getScheduler().performTicks(FlightMath.TOTAL_TICKS_STRAIGHT + 10);

        assertTrue(world.getEntitiesByClass(ItemDisplay.class).isEmpty());
    }

    @Test
    void removeAllImmediatelyClearsEveryInFlightDisplayRightAway() {
        Map<Integer, Location> origins = Map.of(1, new Location(world, 0, 70, 0), 2, new Location(world, 10, 70, 0));
        flyingBalls.launch(origins, Map.of(), BallSet.OVERWORLD);

        flyingBalls.removeAllImmediately();

        assertTrue(world.getEntitiesByClass(ItemDisplay.class).isEmpty());
    }

    @Test
    void removeStrayDeletesAnUntrackedTaggedDisplayButKeepsAnActiveFlight() {
        Map<Integer, Location> origins = Map.of(4, new Location(world, 0, 70, 0));
        flyingBalls.launch(origins, Map.of(), BallSet.OVERWORLD);
        ItemDisplay stray = world.spawn(new Location(world, 50, 70, 50), ItemDisplay.class);
        stray.addScoreboardTag(EffectMarkers.BALL_DISPLAY_TAG);

        flyingBalls.removeStray(world);

        assertEquals(1, world.getEntitiesByClass(ItemDisplay.class).size());
        assertFalse(stray.isValid());
    }
}
