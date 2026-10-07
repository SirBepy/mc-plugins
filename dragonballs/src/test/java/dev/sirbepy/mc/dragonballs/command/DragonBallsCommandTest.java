package dev.sirbepy.mc.dragonballs.command;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallScatterer;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DragonBallsCommandTest {

    private ServerMock server;
    private WorldMock world;
    private BallItems items;
    private BallRegistry nether;
    private DragonBallsCommand command;
    private final List<Location> scatterCenters = new ArrayList<>();

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world_nether");
        PluginMock plugin = MockBukkit.createMockPlugin();
        DragonBallsConfig config = new DragonBallsConfig(new YamlConfiguration());
        items = new BallItems(plugin, () -> config);
        BallRegistry overworld = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items,
                BallSet.OVERWORLD);
        nether = new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), items,
                BallSet.NETHER);
        BallRegistry end = new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), items,
                BallSet.END);
        nether.setScatterer(new BallScatterer() {
            @Override
            public java.util.Map<Integer, Location> scatterAll(Location center, boolean stone, BallRegistry registry) {
                scatterCenters.add(center);
                return java.util.Map.of();
            }

            @Override
            public void scatterOne(int star, Location center, boolean stone, BallRegistry registry) {
            }
        });
        BallSets sets = new BallSets(overworld, nether, end, items, () -> config);
        command = new DragonBallsCommand(sets, () -> config, () -> { });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void scatterRemovesTheOldHeadsBeforeMovingTheSet() {
        Block oldHead = world.getBlockAt(10, 40, 10);
        oldHead.setType(Material.PLAYER_HEAD);
        nether.markPlaced(1, oldHead.getLocation(), false);
        nether.setLastSummon(new Location(world, 0, 40, 0));

        command.scatter(server.getConsoleSender(), BallSet.NETHER, false);

        assertEquals(Material.AIR, oldHead.getType());
        assertEquals(1, scatterCenters.size());
    }
}
