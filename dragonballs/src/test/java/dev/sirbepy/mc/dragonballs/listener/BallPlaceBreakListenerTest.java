package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.placed.FloatingBalls;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.registry.BallState;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
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

/**
 * Placing a ball must float it in the block the head would have filled (no head block, the item
 * gone from the hand) and keep other blocks out of that spot; hitting it must give the same ball
 * back, or drop it when the inventory is full, unless the spot's owner forbids taking it.
 */
class BallPlaceBreakListenerTest {

    private ServerMock server;
    private WorldMock world;
    private BallItems items;
    private BallSets sets;
    private FloatingBalls balls;
    private PlayerMock player;
    private boolean mayPickUp = true;
    private BallPlaceBreakListener listener;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        PluginMock plugin = MockBukkit.createMockPlugin();
        items = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        sets = new BallSets(
                new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items, BallSet.OVERWORLD),
                new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), items, BallSet.NETHER),
                new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), items, BallSet.END),
                items, () -> new DragonBallsConfig(new YamlConfiguration()));
        balls = new FloatingBalls(plugin, items, id -> false);
        sets.all().forEach(registry -> registry.setLook(balls));
        listener = new BallPlaceBreakListener(sets, balls, (who, where) -> mayPickUp);
        player = server.addPlayer();
        world.getChunkAt(0, 0).load();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private BlockPlaceEvent place(Block block, ItemStack inHand) {
        player.getInventory().setItemInMainHand(inHand);
        return new BlockPlaceEvent(block, block.getState(), block.getRelative(0, -1, 0), inHand, player, true,
                EquipmentSlot.HAND);
    }

    private Interaction hitbox(BallSet set, int star) {
        return world.getEntitiesByClass(Interaction.class).stream()
                .filter(i -> balls.ballOf(i).map(ref -> ref.set() == set && ref.star() == star).orElse(false))
                .findFirst().orElseThrow();
    }

    @Test
    void placingABallFloatsItInsteadOfPlacingAHead() {
        Block block = world.getBlockAt(3, 65, 4);
        BlockPlaceEvent event = place(block, items.createItem(BallSet.NETHER, 5));

        listener.onPlace(event);

        assertTrue(event.isCancelled(), "no head block");
        assertTrue(player.getInventory().getItemInMainHand().isEmpty(), "the ball left the hand");
        var view = sets.registry(BallSet.NETHER).get(5).orElseThrow();
        assertEquals(BallState.PLACED, view.state());
        assertEquals(3, (int) view.x());
        assertEquals(65, (int) view.y());
        assertTrue(balls.displayOf(BallSet.NETHER, 5).isPresent());
    }

    @Test
    void aStoneBallPlacesAsStone() {
        Block block = world.getBlockAt(3, 65, 4);

        listener.onPlace(place(block, items.createStoneItem(BallSet.OVERWORLD, 2)));

        assertTrue(sets.registry(BallSet.OVERWORLD).isStone(2));
        assertTrue(items.isStoneItem(balls.displayOf(BallSet.OVERWORLD, 2).orElseThrow().getItemStack()));
    }

    @Test
    void nothingCanBePlacedIntoABallsSpot() {
        Block block = world.getBlockAt(3, 65, 4);
        listener.onPlace(place(block, items.createItem(BallSet.OVERWORLD, 1)));

        BlockPlaceEvent stone = place(block, new ItemStack(Material.STONE));
        listener.onPlace(stone);
        BlockPlaceEvent secondBall = place(block, items.createItem(BallSet.OVERWORLD, 2));
        listener.onPlace(secondBall);

        assertTrue(stone.isCancelled());
        assertTrue(secondBall.isCancelled());
        assertEquals(2, items.starOf(player.getInventory().getItemInMainHand()).orElseThrow(), "the second ball stays in hand");
    }

    @Test
    void anOrdinaryBlockElsewhereIsUntouched() {
        BlockPlaceEvent event = place(world.getBlockAt(8, 65, 8), new ItemStack(Material.STONE));

        listener.onPlace(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void hittingABallPicksTheSameBallBackUp() {
        Block block = world.getBlockAt(3, 65, 4);
        listener.onPlace(place(block, items.createItem(BallSet.END, 6)));
        player.getInventory().setItemInMainHand(null);

        PrePlayerAttackEntityEvent event = new PrePlayerAttackEntityEvent(player, hitbox(BallSet.END, 6), true);
        listener.onHit(event);

        assertTrue(event.isCancelled());
        assertTrue(sets.registry(BallSet.END).isHeldBy(6, player.getUniqueId()));
        assertTrue(player.getInventory().containsAtLeast(items.createItem(BallSet.END, 6), 1));
        assertTrue(world.getEntitiesByClass(Interaction.class).isEmpty(), "the floating ball is gone");
        assertFalse(balls.isOccupied(block.getLocation()));
    }

    @Test
    void aFullInventoryDropsThePickedUpBall() {
        listener.onPlace(place(world.getBlockAt(3, 65, 4), items.createItem(BallSet.OVERWORLD, 3)));
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.DIRT, 64));
        }

        listener.onHit(new PrePlayerAttackEntityEvent(player, hitbox(BallSet.OVERWORLD, 3), true));

        var view = sets.registry(BallSet.OVERWORLD).get(3).orElseThrow();
        assertEquals(BallState.DROPPED, view.state());
        assertTrue(world.getEntitiesByClass(Item.class).stream()
                .anyMatch(item -> items.starOf(item.getItemStack()).orElse(-1) == 3));
    }

    @Test
    void aGuardedSpotKeepsItsBall() {
        listener.onPlace(place(world.getBlockAt(3, 65, 4), items.createItem(BallSet.OVERWORLD, 4)));
        player.getInventory().setItemInMainHand(null);
        mayPickUp = false;

        PrePlayerAttackEntityEvent event = new PrePlayerAttackEntityEvent(player, hitbox(BallSet.OVERWORLD, 4), true);
        listener.onHit(event);

        assertTrue(event.isCancelled());
        assertEquals(BallState.PLACED, sets.registry(BallSet.OVERWORLD).get(4).orElseThrow().state());
        assertFalse(player.getInventory().containsAtLeast(items.createItem(BallSet.OVERWORLD, 4), 1));
    }

    @Test
    void hittingSomethingElseIsIgnored() {
        Interaction other = world.spawn(new Location(world, 1, 65, 1), Interaction.class);
        PrePlayerAttackEntityEvent event = new PrePlayerAttackEntityEvent(player, other, true);

        listener.onHit(event);

        assertFalse(event.isCancelled());
    }
}
