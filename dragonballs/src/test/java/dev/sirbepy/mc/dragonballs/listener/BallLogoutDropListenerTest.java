package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.registry.BallState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carried balls drop where the player logs out (and, for someone who logged out before that rule,
 * at their feet on the next join), each routed to its own set's registry; a ball item the registry
 * doesn't have held by this player is a dupe and is never dropped. A ball an offline player still
 * holds drops where they logged out.
 */
class BallLogoutDropListenerTest {

    private ServerMock server;
    private WorldMock world;
    private BallSets sets;
    private BallRegistry overworld;
    private BallItems items;
    private BallLogoutDropListener listener;
    private PlayerMock player;
    private final List<Item> flagged = new ArrayList<>();
    private Location logoutSpot;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        PluginMock plugin = MockBukkit.createMockPlugin();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("messages.balls-dropped-on-join", "Your Dragon Balls dropped at your feet");
        DragonBallsConfig config = new DragonBallsConfig(yaml);
        items = new BallItems(plugin, () -> config);
        overworld = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items, BallSet.OVERWORLD);
        BallRegistry nether = new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), items,
                BallSet.NETHER);
        BallRegistry end = new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), items, BallSet.END);
        sets = new BallSets(overworld, nether, end, items, () -> config);
        listener = new BallLogoutDropListener(sets, () -> config, new BallSlowness(plugin, overworld, () -> config),
                flagged::add, id -> logoutSpot);
        player = server.addPlayer();
        player.teleport(new Location(world, 50, 64, 50));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private List<Item> droppedBalls() {
        return world.getEntitiesByClass(Item.class).stream()
                .filter(item -> items.isBall(item.getItemStack()))
                .toList();
    }

    private boolean inventoryHasBall() {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (items.isBall(stack)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void quittingDropsEveryCarriedBallAtTheLogoutSpot() {
        overworld.markHeld(1, player, false);
        overworld.markHeld(2, player, false);
        player.getInventory().addItem(overworld.createItem(1), new ItemStack(Material.DIRT));
        player.getInventory().setItemInOffHand(overworld.createItem(2));

        listener.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        List<Item> balls = droppedBalls();
        assertEquals(2, balls.size());
        assertTrue(balls.stream().allMatch(b -> b.getLocation().distance(new Location(world, 50, 64, 50)) < 1));
        assertEquals(BallState.DROPPED, overworld.get(1).orElseThrow().state());
        assertEquals(BallState.DROPPED, overworld.get(2).orElseThrow().state());
        assertTrue(!inventoryHasBall() && player.getInventory().getItemInOffHand().getType() == Material.AIR,
                "no ball may stay in the offline player's inventory");
        assertTrue(player.getInventory().contains(Material.DIRT), "other items stay put");
        assertEquals(2, flagged.size(),
                "flagged even when the plugin's own ItemSpawnEvent handler isn't running (shutdown)");
    }

    @Test
    void aNetherBallDropsIntoTheNetherRegistry() {
        BallRegistry nether = sets.registry(BallSet.NETHER);
        nether.markHeld(3, player, true);
        player.getInventory().addItem(items.createStoneItem(BallSet.NETHER, 3));

        listener.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.KICKED));

        assertEquals(BallState.DROPPED, nether.get(3).orElseThrow().state());
        assertEquals(BallState.PLACED, overworld.get(3).orElseThrow().state(), "the overworld star 3 must be untouched");
    }

    @Test
    void aBallNotHeldByThisPlayerIsNeverDroppedSoNoDupeIsMinted() {
        player.getInventory().addItem(overworld.createItem(4));

        listener.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertTrue(droppedBalls().isEmpty());
        assertEquals(BallState.PLACED, overworld.get(4).orElseThrow().state());
    }

    @Test
    void joiningWithBallsFromBeforeTheRuleDropsThemAtTheirFeetAndSaysSo() {
        overworld.markHeld(5, player, false);
        player.getInventory().addItem(overworld.createItem(5));

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertEquals(1, droppedBalls().size());
        assertEquals(BallState.DROPPED, overworld.get(5).orElseThrow().state());
        assertTrue(!inventoryHasBall());
        Component message = player.nextComponentMessage();
        assertEquals("Your Dragon Balls dropped at your feet", PlainTextComponentSerializer.plainText().serialize(message));
    }

    @Test
    void joiningWithNoBallsSaysNothing() {
        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertTrue(droppedBalls().isEmpty());
        assertNull(player.nextComponentMessage());
    }

    @Test
    void aSecondQuitAfterTheDropDoesNotDropAgain() {
        overworld.markHeld(6, player, false);
        player.getInventory().addItem(overworld.createItem(6));

        listener.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        listener.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertEquals(1, droppedBalls().size());
    }

    @Test
    void aBallHeldByAnOfflinePlayerDropsWhereTheyLoggedOut() {
        overworld.markHeld(7, player, false);
        player.disconnect();
        logoutSpot = new Location(world, -300, 70, 900);

        listener.dropForOfflineHolders(overworld);

        List<Item> balls = droppedBalls();
        assertEquals(1, balls.size());
        assertEquals(-300, balls.get(0).getLocation().getBlockX());
        assertEquals(900, balls.get(0).getLocation().getBlockZ());
        assertEquals(BallState.DROPPED, overworld.get(7).orElseThrow().state());
        assertTrue(overworld.isDroppedAs(7, balls.get(0).getUniqueId()));
        assertEquals(1, flagged.size());
    }

    @Test
    void anOfflineHoldersLogoutSpotInAnotherWorldFallsBackToTheBallsLastKnownSpot() {
        overworld.markHeld(1, player, true);
        player.disconnect();
        logoutSpot = new Location(server.addSimpleWorld("world_nether"), 5, 64, 5);

        listener.dropForOfflineHolders(overworld);

        List<Item> balls = droppedBalls();
        assertEquals(1, balls.size(), "an Overworld ball never drops outside the Overworld");
        assertEquals(50, balls.get(0).getLocation().getBlockX());
        assertTrue(items.isStoneItem(balls.get(0).getItemStack()), "a stone ball stays stone");
    }

    @Test
    void aBallHeldByAnOnlinePlayerIsLeftAlone() {
        overworld.markHeld(2, player, false);
        logoutSpot = new Location(world, 0, 64, 0);

        listener.dropForOfflineHolders(overworld);

        assertTrue(droppedBalls().isEmpty());
        assertEquals(BallState.HELD, overworld.get(2).orElseThrow().state());
    }
}
