package dev.sirbepy.mc.dragonballs.station;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.placed.FloatingBalls;
import dev.sirbepy.mc.dragonballs.placed.FloatingBallsTestSupport;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers floating the 7 decor balls (on enable and on chunk load), never doubling one up, and
 * clearing a pedestal's old decor head block, using the real {@code station.display-heads} default. */
class StationDisplayTest {

    private ServerMock server;
    private WorldMock world;
    private PluginMock plugin;
    private BallItems items;
    private FloatingBalls balls;
    private StationDisplay display;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        DragonBallsConfig dragonBallsConfig = new DragonBallsConfig(new YamlConfiguration());
        items = new BallItems(plugin, () -> dragonBallsConfig);
        balls = new FloatingBalls(plugin, items, id -> false);
        display = new StationDisplay(plugin, () -> StationConfig.from(plugin.getConfig()), balls, items);
        world.getChunkAt(415 >> 4, -873 >> 4).load();
        world.getChunkAt(414 >> 4, -877 >> 4).load();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private List<StationConfig.HeadLocation> heads() {
        return StationConfig.from(plugin.getConfig()).displayHeads();
    }

    @Test
    void placeAllFloatsTheOneToSevenStarBallsOverThePedestals() {
        display.placeAll();

        List<StationConfig.HeadLocation> heads = heads();
        for (int i = 0; i < heads.size(); i++) {
            StationConfig.HeadLocation head = heads.get(i);
            ItemDisplay ball = display.displayAt(head);
            assertNotNull(ball, "pedestal " + i);
            assertEquals(head.x() + 0.5, ball.getLocation().getX(), 1e-9);
            assertEquals(head.y() + 0.5, ball.getLocation().getY(), 1e-9);
            assertEquals(i + 1, items.starOf(ball.getItemStack()).orElseThrow());
            assertEquals(BallSet.OVERWORLD, items.setOf(ball.getItemStack()));
            assertFalse(ball.isPersistent(), "respawned on chunk load, never saved");
        }
    }

    @Test
    void placeAllIsSafeToRunTwice() {
        display.placeAll();
        ItemDisplay first = display.displayAt(heads().get(0));

        display.placeAll();

        assertSame(first, display.displayAt(heads().get(0)));
        assertEquals(heads().size(), world.getEntitiesByClass(ItemDisplay.class).size());
    }

    @Test
    void anOldDecorHeadBlockMakesWayForTheFloatingBall() {
        StationConfig.HeadLocation head = heads().get(0);
        Block block = world.getBlockAt(head.x(), head.y(), head.z());
        block.setType(Material.PLAYER_HEAD);
        Skull skull = (Skull) block.getState();
        skull.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "decor"), PersistentDataType.BOOLEAN, true);
        skull.update(true);

        display.placeAll();

        assertEquals(Material.AIR, block.getType());
        assertNotNull(display.displayAt(head));
    }

    @Test
    void someOtherBlockOnAPedestalIsLeftAlone() {
        StationConfig.HeadLocation head = heads().get(0);
        Block block = world.getBlockAt(head.x(), head.y(), head.z());
        block.setType(Material.PLAYER_HEAD);

        display.placeAll();

        assertEquals(Material.PLAYER_HEAD, block.getType(), "only a head marked as decor is cleared");
    }

    @Test
    void onChunkLoadOnlyFloatsTheBallsInThatChunk() {
        plugin.getConfig().set("station.display-heads", List.of(
                java.util.Map.of("world", "world", "x", 415, "y", 116, "z", -873),
                java.util.Map.of("world", "world", "x", 500, "y", 116, "z", -873)));
        world.getChunkAt(500 >> 4, -873 >> 4).load();

        display.onChunkLoad(new ChunkLoadEvent(world.getChunkAt(415 >> 4, -873 >> 4), false));

        assertNotNull(display.displayAt(heads().get(0)));
        assertNull(display.displayAt(heads().get(1)),
                "a different chunk loading must not float the ball that lives in another chunk");
    }

    @Test
    void decorBallsSpinWithThePlacedBalls() {
        balls.start();
        display.placeAll();
        ItemDisplay ball = display.displayAt(heads().get(0));
        float before = FloatingBallsTestSupport.yaw(ball.getTransformation());

        server.getScheduler().performTicks(FloatingBalls.SPIN_PERIOD_TICKS);

        assertTrue(Math.abs(FloatingBallsTestSupport.yaw(ball.getTransformation()) - before) > 1e-3);
        assertEquals(FloatingBalls.SPIN_PERIOD_TICKS, ball.getInterpolationDuration());
    }
}
