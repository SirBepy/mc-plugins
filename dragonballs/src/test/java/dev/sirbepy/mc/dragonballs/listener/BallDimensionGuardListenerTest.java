package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A ball is pinned to its own home dimension: crossing worlds leaves a foreign-set ball behind
 * (dropped, registry-marked) and keeps one headed home; a dropped ball's own portal/teleport is
 * cancelled so the portal itself can't carry it across either. */
class BallDimensionGuardListenerTest {

    private ServerMock server;
    private WorldMock world;
    private WorldMock netherWorld;
    private BallItems items;
    private BallSets sets;
    private BallRegistry registry;
    private BallRegistry netherRegistry;
    private BallSlowness slowness;
    private BallDimensionGuardListener listener;
    private PlayerMock player;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        netherWorld = server.addSimpleWorld("world_nether");
        PluginMock plugin = MockBukkit.createMockPlugin();
        items = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        registry = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items,
                BallSet.OVERWORLD);
        netherRegistry = new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), items,
                BallSet.NETHER);
        BallRegistry endRegistry = new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), items,
                BallSet.END);
        sets = new BallSets(registry, netherRegistry, endRegistry, items, () -> new DragonBallsConfig(new YamlConfiguration()));
        slowness = new BallSlowness(plugin, registry, () -> new DragonBallsConfig(new YamlConfiguration()));
        listener = new BallDimensionGuardListener(sets, () -> new DragonBallsConfig(new YamlConfiguration()), slowness);
        player = server.addPlayer();
        player.teleport(new Location(netherWorld, 0, 64, 0));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void teleportingFromTheNetherToTheOverworldDropsANetherBallAtTheFromLocationAndKeepsAnOverworldOne() {
        ItemStack netherBall = items.createItem(BallSet.NETHER, 4);
        ItemStack overworldBall = items.createItem(BallSet.OVERWORLD, 2);
        player.getInventory().setItem(0, netherBall);
        player.getInventory().setItem(1, overworldBall);
        Location from = new Location(netherWorld, 10, 64, 20);
        Location to = new Location(world, 0, 70, 0);

        PlayerTeleportEvent event = new PlayerTeleportEvent(player, from, to);
        listener.onTeleport(event);

        assertFalse(items.isBall(player.getInventory().getItem(0)), "the Nether ball must be removed from the inventory");
        assertTrue(items.isBall(player.getInventory().getItem(1)), "the Overworld ball is already headed home and must stay");

        var dropped = netherWorld.getEntitiesByClass(Item.class).stream()
                .filter(item -> items.isBall(item.getItemStack()))
                .toList();
        assertEquals(1, dropped.size());
        assertEquals(10, dropped.get(0).getLocation().getBlockX(), "the ball drops at the departure point, not the destination");
        assertTrue(netherRegistry.isDroppedAs(4, dropped.get(0).getUniqueId()), "the registry must mark it DROPPED there");
    }

    @Test
    void teleportingWithinTheSameWorldLeavesEveryBallAlone() {
        ItemStack netherBall = items.createItem(BallSet.NETHER, 1);
        player.getInventory().setItem(0, netherBall);

        PlayerTeleportEvent event = new PlayerTeleportEvent(player, new Location(netherWorld, 0, 64, 0),
                new Location(netherWorld, 50, 64, 50));
        listener.onTeleport(event);

        assertTrue(items.isBall(player.getInventory().getItem(0)), "a same-world teleport must never touch a carried ball");
    }

    @Test
    void aBallItemEntitysPortalEventIsCancelled() {
        ItemStack netherBall = items.createItem(BallSet.NETHER, 3);
        Item entity = netherWorld.dropItem(new Location(netherWorld, 5, 64, 5), netherBall);

        EntityPortalEvent event = new EntityPortalEvent(entity, entity.getLocation(), new Location(world, 0, 70, 0));
        listener.onEntityPortal(event);

        assertTrue(event.isCancelled(), "a dropped ball must never ride a portal across on its own");
    }

    @Test
    void aPlainItemEntitysPortalEventIsUntouched() {
        Item plainItem = netherWorld.dropItem(new Location(netherWorld, 5, 64, 5),
                new ItemStack(org.bukkit.Material.DIRT));

        EntityPortalEvent event = new EntityPortalEvent(plainItem, plainItem.getLocation(), new Location(world, 0, 70, 0));
        listener.onEntityPortal(event);

        assertFalse(event.isCancelled());
    }
}
