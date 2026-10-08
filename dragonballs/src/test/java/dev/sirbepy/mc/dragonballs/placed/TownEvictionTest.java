package dev.sirbepy.mc.dragonballs.placed;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.registry.BallState;
import dev.sirbepy.mc.dragonballs.registry.BallView;
import dev.sirbepy.mc.dragonballs.scatter.ScatterTownyGuard;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A placed or dropped ball inside a town moves to the nearest free wilderness spot past the border,
 * on the ground and still a ball (placed, shown, same stone look), and nearby players hear where it
 * went; held balls, wilderness balls and a ball flying off after a wish stay put.
 */
class TownEvictionTest {

    // The town: x 100..139, z 0..39 (every height).
    private static final ScatterTownyGuard TOWNS = at ->
            !(at.getBlockX() >= 100 && at.getBlockX() < 140 && at.getBlockZ() >= 0 && at.getBlockZ() < 40);

    private ServerMock server;
    private WorldMock world;
    private BallItems items;
    private BallSets sets;
    private FloatingBalls balls;
    private TownEviction eviction;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        PluginMock plugin = MockBukkit.createMockPlugin();
        YamlConfiguration shipped = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
        DragonBallsConfig config = new DragonBallsConfig(shipped);
        items = new BallItems(plugin, () -> config);
        sets = new BallSets(
                new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items, BallSet.OVERWORLD),
                new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), items, BallSet.NETHER),
                new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), items, BallSet.END),
                items, () -> config);
        balls = new FloatingBalls(plugin, items, id -> false);
        sets.all().forEach(registry -> registry.setLook(balls));
        eviction = new TownEviction(sets, balls, TOWNS, () -> config, plugin.getLogger());
        // A stone floor at y 64 under the town and its surroundings, in loaded chunks.
        for (int cx = 5; cx < 10; cx++) {
            for (int cz = -2; cz < 4; cz++) {
                world.getChunkAt(cx, cz).load();
            }
        }
        for (int x = 80; x < 160; x++) {
            for (int z = -20; z < 60; z++) {
                world.getBlockAt(x, 64, z).setType(Material.STONE);
            }
        }
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private BallRegistry overworld() {
        return sets.registry(BallSet.OVERWORLD);
    }

    private BallView view(int star) {
        return overworld().get(star).orElseThrow();
    }

    private static boolean inTown(BallView view) {
        return !TOWNS.isWilderness(view.lastKnownLocation());
    }

    @Test
    void aPlacedBallInATownMovesJustPastTheBorder() {
        overworld().markPlaced(3, new Location(world, 104, 65, 20), true);

        eviction.run();

        BallView moved = view(3);
        assertEquals(BallState.PLACED, moved.state());
        assertFalse(inTown(moved), "outside the town now: " + moved);
        assertTrue(moved.x() < 100 && moved.x() >= 92, "the nearest border is 4 blocks west: " + moved.x());
        assertEquals(65, (int) moved.y(), "on the ground at its own height");
        assertTrue(moved.stone(), "a stone ball stays stone");
        assertTrue(balls.displayOf(BallSet.OVERWORLD, 3).isPresent(), "it floats at its new spot");
    }

    @Test
    void aDroppedBallInATownGoesTooAndItsItemWithIt() {
        Item item = world.dropItem(new Location(world, 120.5, 65, 10.5), items.createItem(BallSet.OVERWORLD, 5));
        overworld().markDropped(5, item, false);

        eviction.run();

        assertFalse(item.isValid(), "no copy is left lying in the town");
        assertEquals(BallState.PLACED, view(5).state());
        assertFalse(inTown(view(5)));
    }

    @Test
    void twoBallsLeavingTheSameTownGetTheirOwnSpots() {
        overworld().markPlaced(1, new Location(world, 104, 65, 20), false);
        overworld().markPlaced(2, new Location(world, 104, 66, 20), false);

        eviction.run();

        assertFalse(inTown(view(1)));
        assertFalse(inTown(view(2)));
        assertNotEquals(view(1).lastKnownLocation().toBlockLocation(), view(2).lastKnownLocation().toBlockLocation());
    }

    @Test
    void nearbyPlayersAreToldWhereItWent() {
        PlayerMock near = server.addPlayer("Near");
        near.teleport(new Location(world, 110, 65, 20));
        PlayerMock far = server.addPlayer("Far");
        far.teleport(new Location(world, 400, 65, 400));
        overworld().markPlaced(6, new Location(world, 104, 65, 20), false);

        eviction.run();

        BallView moved = view(6);
        assertEquals("The 6-Star Dragon Ball can't stay inside a town, so it rolled out to "
                + (int) moved.x() + ", 65, " + (int) moved.z() + ".", ChatColor.stripColor(near.nextMessage()));
        assertNull(far.nextMessage());
    }

    /** The whole wilderness strip west of the town, x 80..99, at height y. */
    private void westStrip(WorldMock in, int y, Material material) {
        for (int x = 80; x < 100; x++) {
            for (int z = -20; z < 60; z++) {
                in.getBlockAt(x, y, z).setType(material);
            }
        }
    }

    // Joe, 2026-10-09: the 1-star left a town 8 blocks under the ground; a ball must stay findable.
    @Test
    void aBallNeverLandsInACaveUnderTheWilderness() {
        // A hill west of the town, y 66..70, over a one-block cave at the town's floor height.
        for (int y = 66; y <= 70; y++) {
            westStrip(world, y, Material.STONE);
        }
        overworld().markPlaced(3, new Location(world, 104, 65, 20), false);

        eviction.run();

        BallView moved = view(3);
        assertTrue(moved.x() < 100, "still the nearest border, west: " + moved);
        assertEquals(71, (int) moved.y(), "on top of the hill, not in the cave under it");
        assertEquals(70, world.getHighestBlockYAt((int) Math.floor(moved.x()), (int) Math.floor(moved.z())),
                "nothing above it but sky");
    }

    @Test
    void aBallNeverLandsUnderTreesOrOnWater() {
        // West of the town: a leaf canopy at y 70; its floor is the same stone at 64.
        westStrip(world, 70, Material.OAK_LEAVES);
        // North of it: a lake on the floor.
        for (int x = 100; x < 140; x++) {
            for (int z = -20; z < 0; z++) {
                world.getBlockAt(x, 65, z).setType(Material.WATER);
            }
        }
        overworld().markPlaced(3, new Location(world, 104, 65, 20), false);

        eviction.run();

        BallView moved = view(3);
        assertFalse(inTown(moved), "outside the town: " + moved);
        assertTrue(moved.x() >= 100 && moved.z() >= 40, "south, the only dry open side: " + moved);
        assertEquals(65, (int) moved.y(), "on the open floor: " + moved);
    }

    @Test
    void aNetherBallLandsWithHeadroomUnderTheRoof() {
        WorldMock nether = server.addSimpleWorld("world_nether");
        nether.setEnvironment(org.bukkit.World.Environment.NETHER);
        for (int cx = 5; cx < 10; cx++) {
            for (int cz = -2; cz < 4; cz++) {
                nether.getChunkAt(cx, cz).load();
            }
        }
        for (int x = 80; x < 160; x++) {
            for (int z = -20; z < 60; z++) {
                nether.getBlockAt(x, 64, z).setType(Material.NETHERRACK);
                nether.getBlockAt(x, 126, z).setType(Material.BEDROCK);
            }
        }
        // West of the town a one-block-high gap at y 65: a pocket, with open cavern above y 66.
        westStrip(nether, 66, Material.NETHERRACK);
        sets.registry(BallSet.NETHER).markPlaced(2, new Location(nether, 104, 65, 20), false);

        eviction.run();

        BallView moved = sets.registry(BallSet.NETHER).get(2).orElseThrow();
        assertTrue(moved.x() < 100, "the nearest border, west: " + moved);
        assertEquals(67, (int) moved.y(), "on the netherrack above the pocket, not in it");
    }

    @Test
    void heldWildernessAndFlyingBallsStayPut() {
        PlayerMock holder = server.addPlayer("Holder");
        holder.teleport(new Location(world, 120, 65, 20));
        overworld().markHeld(1, holder, false);
        overworld().markPlaced(2, new Location(world, 50, 65, 50), false);
        overworld().markPlaced(4, new Location(world, 130, 65, 30), false);
        overworld().hidePlaced(4);

        eviction.run();

        assertEquals(BallState.HELD, view(1).state(), "players carry balls through towns");
        assertEquals(50, (int) view(2).x(), "a wilderness ball isn't touched");
        assertEquals(130, (int) view(4).x(), "a ball flying off after a wish lands where the scatter sends it");
    }
}
