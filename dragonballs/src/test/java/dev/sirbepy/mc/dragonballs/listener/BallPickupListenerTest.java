package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.registry.BallState;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dropping a ball makes it the tracked DROPPED entity only when the registry has it HELD by the
 * player who dropped it; any other copy is a stale duplicate and must never move the real ball.
 */
class BallPickupListenerTest {

    private ServerMock server;
    private WorldMock world;
    private BallRegistry overworld;
    private BallPickupListener listener;
    private PlayerMock player;
    private final List<String> announced = new ArrayList<>();

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        PluginMock plugin = MockBukkit.createMockPlugin();
        DragonBallsConfig config = new DragonBallsConfig(new YamlConfiguration());
        BallItems items = new BallItems(plugin, () -> config);
        overworld = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items, BallSet.OVERWORLD);
        BallSets sets = new BallSets(overworld,
                new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), items, BallSet.NETHER),
                new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), items, BallSet.END),
                items, () -> config);
        HeldBallCheck check = new HeldBallCheck(sets, () -> config, item -> { }, plugin.getLogger());
        listener = new BallPickupListener(sets, check,
                (finder, set, star) -> announced.add(finder.getName() + " " + set + " " + star), plugin.getLogger());
        check.trackSelfPickupsWith(listener);
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(new Location(world, 50, 64, 50));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Location farSpot() {
        return new Location(world, 900, 70, -900);
    }

    /** The entity a Q-press leaves on the ground, with the event Paper fires for it. */
    private Item drop(PlayerMock dropper, int star) {
        Item entity = world.dropItem(dropper.getLocation(), overworld.createItem(star));
        listener.onDrop(new PlayerDropItemEvent(dropper, entity));
        return entity;
    }

    @Test
    void droppingYourOwnBallTracksItAsDropped() {
        overworld.markHeld(2, player, false);

        Item entity = drop(player, 2);

        assertFalse(entity.isDead());
        assertTrue(overworld.isDroppedAs(2, entity.getUniqueId()));
    }

    /** dvolic21, live 2026-10-09: a copy left in his inventory was dropped and picked back up, and
     * the real ball, placed far away, became his with no "found" line. */
    @Test
    void aStaleCopyDroppedNeverTakesOverThePlacedBall() {
        overworld.markPlaced(6, farSpot(), false);

        Item copy = drop(player, 6);
        EntityPickupItemEvent pickup = new EntityPickupItemEvent(player, copy, 0);
        listener.onPickup(pickup);

        assertTrue(copy.isDead(), "the copy is deleted when it hits the ground");
        assertTrue(overworld.isPlacedAt(6, farSpot()), "the real ball stays where it was");
        assertFalse(overworld.isHeldBy(6, player.getUniqueId()));
        assertTrue(announced.isEmpty());
    }

    @Test
    void aCopyDroppedWhileSomeoneElseHoldsTheBallLeavesThemHoldingIt() {
        PlayerMock holder = server.addPlayer();
        overworld.markHeld(3, holder, false);

        Item copy = drop(player, 3);

        assertTrue(copy.isDead());
        assertEquals(BallState.HELD, overworld.get(3).orElseThrow().state());
        assertTrue(overworld.isHeldBy(3, holder.getUniqueId()));
    }
}
