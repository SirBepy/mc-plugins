package dev.sirbepy.mc.dragonballs.task;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.config.StoneMode;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The minecraft-days stone cooldown runs on the overworld's day count for every set. Reproduces
 * the 2026-10-09 live bug: on 26.3 the Nether has no day clock, so its full time stayed 0 and the
 * Nether set stayed stone ~20 h after a wish instead of 3 in-game days.
 */
class BallMaintenanceTaskTest {

    private static final long DAY = 24000L;

    private ServerMock server;
    private WorldMock overworld;
    private WorldMock netherWorld;
    private BallRegistry nether;
    private BallMaintenanceTask task;
    private DragonBallsConfig config;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        overworld = server.addSimpleWorld("world");
        netherWorld = server.addSimpleWorld("world_nether");
        PluginMock plugin = MockBukkit.createMockPlugin();
        config = new DragonBallsConfig(new YamlConfiguration());
        BallItems items = new BallItems(plugin, () -> config);
        BallRegistry overworldSet = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(),
                items, BallSet.OVERWORLD);
        nether = new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), items,
                BallSet.NETHER);
        BallRegistry end = new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), items,
                BallSet.END);
        BallSets sets = new BallSets(overworldSet, nether, end, items, () -> config);
        task = new BallMaintenanceTask(sets, () -> config, registry -> { });
        overworld.setFullTime(10 * DAY);
        netherWorld.setFullTime(0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void netherStoneCooldownCountsOverworldDays() {
        nether.applyStoneUntil(config, 0);

        overworld.setFullTime(12 * DAY);
        task.run();
        assertTrue(nether.isDormant(), "2 of the 3 overworld days have passed");

        overworld.setFullTime(13 * DAY);
        task.run();
        assertFalse(nether.isDormant(), "3 overworld days after the wish the Nether set wakes, though the Nether's own clock never moved");
    }

    // A cooldown saved before the fix was counted from the Nether's stuck day 0, so its end day is
    // tiny next to the overworld's; it must end on the first sweep rather than strand the set.
    @Test
    void cooldownSavedFromTheStuckNetherClockEndsOnTheNextSweep() {
        nether.setStoneUntil(StoneMode.MINECRAFT_DAYS, 3);

        task.run();

        assertFalse(nether.isDormant());
    }
}
