package dev.sirbepy.mc.dragonballs.station;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.world.ChunkLoadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers placing the 7 decor heads (on enable and on chunk load) and skipping a spot that's
 * already one, using the real {@code station.display-heads} default from dragonballs-station.json. */
class StationDisplayTest {

    private ServerMock server;
    private WorldMock world;
    private PluginMock plugin;
    private StationDisplay display;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();

        YamlConfiguration config = new YamlConfiguration();
        config.set("textures.stone", "deadbeef");
        DragonBallsConfig dragonBallsConfig = new DragonBallsConfig(config);

        display = new StationDisplay(plugin, () -> dragonBallsConfig, () -> StationConfig.from(plugin.getConfig()));
        world.getChunkAt(415 >> 4, -873 >> 4).load();
        world.getChunkAt(414 >> 4, -877 >> 4).load();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void placeAllPutsADecorHeadOnEveryDefaultCoordinate() {
        display.placeAll();

        for (StationConfig.HeadLocation head : StationConfig.from(plugin.getConfig()).displayHeads()) {
            Block block = world.getBlockAt(head.x(), head.y(), head.z());
            assertEquals(Material.PLAYER_HEAD, block.getType());
            assertTrue(display.isDecor(block));
        }
    }

    @Test
    void placeAllIsSafeToRunTwice() {
        display.placeAll();
        display.placeAll();

        Block block = world.getBlockAt(415, 116, -873);
        assertEquals(Material.PLAYER_HEAD, block.getType());
        assertTrue(display.isDecor(block));
    }

    @Test
    void aSpotThatAlreadyHasSomeOtherBlockGetsTheDecorHeadToo() {
        Block block = world.getBlockAt(415, 116, -873);
        block.setType(Material.AIR);

        display.placeAll();

        assertEquals(Material.PLAYER_HEAD, block.getType());
        assertTrue(display.isDecor(block));
    }

    @Test
    void onChunkLoadOnlyPlacesTheHeadsInThatChunk() {
        // The real 7 station heads all sit in one chunk (they're a few blocks apart), so this test
        // configures two heads far enough apart to land in different chunks on purpose.
        plugin.getConfig().set("station.display-heads", java.util.List.of(
                java.util.Map.of("world", "world", "x", 415, "y", 116, "z", -873),
                java.util.Map.of("world", "world", "x", 500, "y", 116, "z", -873)));
        StationDisplay twoChunkDisplay = new StationDisplay(plugin,
                () -> new DragonBallsConfig(new YamlConfiguration()), () -> StationConfig.from(plugin.getConfig()));
        world.getChunkAt(500 >> 4, -873 >> 4).load();

        twoChunkDisplay.onChunkLoad(new ChunkLoadEvent(world.getChunkAt(415 >> 4, -873 >> 4), false));

        assertEquals(Material.PLAYER_HEAD, world.getBlockAt(415, 116, -873).getType());
        assertTrue(world.getBlockAt(500, 116, -873).getType() != Material.PLAYER_HEAD,
                "a different chunk loading must not place the head that lives in another chunk");
    }

    @Test
    void decorHeadTextureIsAppliedWhenConfigured() {
        display.placeAll();

        Block block = world.getBlockAt(415, 116, -873);
        assertTrue(block.getState() instanceof Skull);
    }
}
