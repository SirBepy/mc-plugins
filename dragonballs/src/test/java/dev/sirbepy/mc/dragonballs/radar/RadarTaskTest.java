package dev.sirbepy.mc.dragonballs.radar;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarTaskTest {

    private ServerMock server;
    private WorldMock world;
    private WorldMock netherWorld;
    private BallSets sets;
    private BallRegistry registry;
    private BallRegistry netherRegistry;
    private RadarItems items;
    private RadarTask task;
    private PlayerMock player;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        netherWorld = server.addSimpleWorld("world_nether");
        PluginMock plugin = MockBukkit.createMockPlugin();
        BallItems ballItems = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        registry = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), ballItems,
                BallSet.OVERWORLD);
        netherRegistry = new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), ballItems,
                BallSet.NETHER);
        BallRegistry endRegistry = new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(),
                ballItems, BallSet.END);
        sets = new BallSets(registry, netherRegistry, endRegistry, ballItems, () -> new DragonBallsConfig(new YamlConfiguration()));
        items = new RadarItems(plugin);
        task = new RadarTask(sets, items, () -> new DragonBallsConfig(new YamlConfiguration()));

        player = server.addPlayer();
        player.teleport(new Location(world, 0, 64, 0));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void holdingRadarPointsAtTheNearestAwakeBallInTheSameWorld() {
        registry.markPlaced(3, new Location(world, 100, 70, 0), false);
        player.getInventory().setItemInMainHand(items.create());

        task.run();

        assertEquals(new Location(world, 100, 70, 0).getBlockX(), player.getCompassTarget().getBlockX());
        assertTrue(items.isShowingActive(player.getInventory().getItemInMainHand()));
    }

    @Test
    void noAwakeBallsInThePlayersWorldShowsTheIdleScreen() {
        // Fresh registry: all 7 balls start with no stored location, so activeTargets() is empty.
        player.getInventory().setItemInMainHand(items.create());

        task.run();

        assertTrue(items.isShowingIdle(player.getInventory().getItemInMainHand()));
    }

    @Test
    void stoneBallsAreInvisibleToTheRadar() {
        registry.markPlaced(2, new Location(world, 50, 70, 0), true);
        player.getInventory().setItemInMainHand(items.create());

        task.run();

        assertTrue(items.isShowingIdle(player.getInventory().getItemInMainHand()));
    }

    @Test
    void aPlayerNeverGetsTheirTargetChangedByAPlainVanillaCompass() {
        registry.markPlaced(1, new Location(world, 20, 64, 0), false);
        player.getInventory().setItemInMainHand(new ItemStack(Material.COMPASS));
        Location before = player.getCompassTarget();

        task.run();

        assertEquals(before, player.getCompassTarget(), "a plain compass holder is never touched");
    }

    @Test
    void stoppingHoldingTheRadarResetsTheCompassToWorldSpawnOnce() {
        registry.markPlaced(4, new Location(world, 30, 64, 0), false);
        player.getInventory().setItemInMainHand(items.create());
        task.run();
        assertEquals(30, player.getCompassTarget().getBlockX());

        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        task.run();

        assertEquals(world.getSpawnLocation().getBlockX(), player.getCompassTarget().getBlockX());
        assertEquals(world.getSpawnLocation().getBlockZ(), player.getCompassTarget().getBlockZ());
    }

    @Test
    void aBallThePlayerIsCarryingThemselfIsExcludedFromTheirOwnRadar() {
        // The only active ball is the one this very player holds; the radar must find nothing.
        registry.markHeld(5, player, false);
        player.getInventory().setItemInOffHand(items.create());

        task.run();

        assertTrue(items.isShowingIdle(player.getInventory().getItemInOffHand()));
    }

    @Test
    void offHandRadarIsFoundWhenMainHandIsEmpty() {
        registry.markPlaced(6, new Location(world, 5, 64, 5), false);
        player.getInventory().setItemInOffHand(items.create());

        task.run();

        assertTrue(items.isShowingActive(player.getInventory().getItemInOffHand()));
        assertEquals(5, player.getCompassTarget().getBlockX());
    }

    @Test
    void anOverworldRadarIgnoresANetherBallEvenAtTheSameCoordinates() {
        netherRegistry.markPlaced(1, new Location(netherWorld, 5, 64, 5), false);
        player.getInventory().setItemInMainHand(items.create(BallSet.OVERWORLD));

        task.run();

        assertTrue(items.isShowingIdle(player.getInventory().getItemInMainHand()),
                "an Overworld radar must never point at a Nether ball");
    }

    @Test
    void aNetherRadarPointsAtTheNearestNetherBallWhileInTheNether() {
        netherRegistry.markPlaced(2, new Location(netherWorld, 40, 64, 0), false);
        player.teleport(new Location(netherWorld, 0, 64, 0));
        player.getInventory().setItemInMainHand(items.create(BallSet.NETHER));

        task.run();

        assertTrue(items.isShowingActive(player.getInventory().getItemInMainHand()));
        assertEquals(40, player.getCompassTarget().getBlockX());
    }

    @Test
    void aNetherRadarShowsIdleOutsideTheNetherEvenWithAnAwakeNetherBall() {
        netherRegistry.markPlaced(2, new Location(netherWorld, 40, 64, 0), false);
        // Player stays in the overworld, holding a Nether radar.
        player.getInventory().setItemInMainHand(items.create(BallSet.NETHER));

        task.run();

        assertTrue(items.isShowingIdle(player.getInventory().getItemInMainHand()),
                "a Nether radar must only ever point while its holder is actually in the Nether");
    }
}
