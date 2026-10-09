package dev.sirbepy.mc.dragonballs.command;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.config.StoneMode;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.placed.FloatingBalls;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallScatterer;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Interaction;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void scatterTakesTheOldBallsAwayBeforeMovingTheSet() {
        FloatingBalls balls = new FloatingBalls(MockBukkit.createMockPlugin(), items, id -> false);
        nether.setLook(balls);
        Block oldSpot = world.getBlockAt(10, 40, 10);
        world.getChunkAt(oldSpot).load();
        nether.markPlaced(1, oldSpot.getLocation(), false);
        nether.setLastSummon(new Location(world, 0, 40, 0));

        command.scatter(server.getConsoleSender(), BallSet.NETHER, false);

        assertTrue(balls.displayOf(BallSet.NETHER, 1).isEmpty(), "the old ball doesn't linger until the new spot lands");
        assertTrue(world.getEntitiesByClass(Interaction.class).isEmpty());
        assertEquals(1, scatterCenters.size());
    }

    // Reproduces the 2026-10-08 live mismatch: 57ca's plain re-scatter during a stone window left
    // the Nether set refusing summons as stone while its fresh balls weren't stone-looking.
    @Test
    void scatterWithoutStoneWakesAnAlreadyDormantSet() {
        nether.setLastSummon(new Location(world, 0, 40, 0));
        nether.setStoneUntil(StoneMode.MINECRAFT_DAYS, 999);
        assertTrue(nether.isDormant());

        command.scatter(server.getConsoleSender(), BallSet.NETHER, false);

        assertFalse(nether.isDormant(), "a plain scatter must wake the set, matching the non-stone balls it just placed");
    }

    // The reverse mismatch: an admin stone-scatter with no cooldown running placed stone-looking
    // balls that a summon attempt would never actually refuse.
    @Test
    void scatterWithStoneStartsTheCooldownWhenNoneIsRunning() {
        nether.setLastSummon(new Location(world, 0, 40, 0));
        assertFalse(nether.isDormant());

        command.scatter(server.getConsoleSender(), BallSet.NETHER, true);

        assertTrue(nether.isDormant(), "a stone scatter must leave the set dormant, matching the stone balls it just placed");
    }

    @Test
    void scatterWithStoneKeepsAnAlreadyRunningCooldownsEndTime() {
        nether.setLastSummon(new Location(world, 0, 40, 0));
        nether.setStoneUntil(StoneMode.MINECRAFT_DAYS, 42);

        command.scatter(server.getConsoleSender(), BallSet.NETHER, true);

        assertTrue(nether.isDormant());
        assertFalse(nether.isStoneExpired(3, 0),
                "a re-scatter mid-cooldown must not reset a wish's own end time to the config default");
    }
}
