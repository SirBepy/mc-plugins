package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.registry.BallState;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityPickupItemEvent;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ball the registry has HELD by an online player who no longer carries it (moved out with no
 * Bukkit event: the graves datapack refilling a slot on grave opening, /clear) is found and tracked
 * again, or dropped at the holder's feet when it's gone; and picking up such a ball is never treated
 * as a dupe.
 */
class HeldBallCheckTest {

    private ServerMock server;
    private WorldMock world;
    private BallItems items;
    private BallRegistry overworld;
    private BallSets sets;
    private HeldBallCheck check;
    private BallPickupListener pickup;
    private PlayerMock player;
    private final List<Item> flagged = new ArrayList<>();

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        PluginMock plugin = MockBukkit.createMockPlugin();
        YamlConfiguration yaml = new YamlConfiguration();
        // Blank: MockBukkit's server can't take a broadcast (the test would be skipped).
        yaml.set("messages.found", "");
        yaml.set("messages.ball-returned", "Your <star>-Star Dragon Ball dropped at your feet.");
        DragonBallsConfig config = new DragonBallsConfig(yaml);
        items = new BallItems(plugin, () -> config);
        overworld = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items, BallSet.OVERWORLD);
        BallRegistry nether = new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), items,
                BallSet.NETHER);
        BallRegistry end = new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), items, BallSet.END);
        sets = new BallSets(overworld, nether, end, items, () -> config);
        check = new HeldBallCheck(sets, () -> config, flagged::add, plugin.getLogger());
        pickup = new BallPickupListener(sets, () -> config, check);
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(new Location(world, 50, 64, 50));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** What graves does on grave opening: the slot's ball is gone, and the same ball lies at the
     * grave as an entity the registry never saw. */
    private Item displaceIntoTheWorld(int star) {
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
        return world.dropItem(new Location(world, 52, 64, 52), overworld.createItem(star));
    }

    private List<Item> ballEntities() {
        return world.getEntitiesByClass(Item.class).stream().filter(item -> items.isBall(item.getItemStack())).toList();
    }

    private EntityPickupItemEvent pickUp(PlayerMock picker, Item entity) {
        EntityPickupItemEvent event = new EntityPickupItemEvent(picker, entity, 0);
        pickup.onPickup(event);
        return event;
    }

    @Test
    void pickingUpABallTheGravePushedOutReturnsItInsteadOfDeletingIt() {
        overworld.markHeld(2, player, false);
        Item displaced = displaceIntoTheWorld(2);

        EntityPickupItemEvent event = pickUp(player, displaced);

        assertFalse(event.isCancelled(), "the pickup goes through");
        assertFalse(displaced.isDead(), "the ball entity is not deleted as a dupe");
        assertTrue(overworld.isHeldBy(2, player.getUniqueId()));
    }

    @Test
    void anotherPlayerCanPickUpABallThatLeftItsHolder() {
        overworld.markHeld(2, player, false);
        Item displaced = displaceIntoTheWorld(2);
        PlayerMock other = server.addPlayer();

        EntityPickupItemEvent event = pickUp(other, displaced);

        assertFalse(event.isCancelled());
        assertTrue(overworld.isHeldBy(2, other.getUniqueId()));
    }

    @Test
    void aSecondCopyIsStillDeletedWhileTheHolderCarriesTheRealOne() {
        overworld.markHeld(2, player, false);
        player.getInventory().addItem(overworld.createItem(2));
        Item dupe = world.dropItem(new Location(world, 52, 64, 52), overworld.createItem(2));

        EntityPickupItemEvent event = pickUp(server.addPlayer(), dupe);

        assertTrue(event.isCancelled());
        assertTrue(dupe.isDead());
        assertTrue(overworld.isHeldBy(2, player.getUniqueId()));
    }

    @Test
    void aCarriedBallIsLeftAlone() {
        overworld.markHeld(3, player, false);
        player.getInventory().setItemInOffHand(overworld.createItem(3));

        check.run();
        check.run();

        assertTrue(overworld.isHeldBy(3, player.getUniqueId()));
        assertTrue(ballEntities().isEmpty(), "no second copy dropped");
    }

    @Test
    void aBallOnTheCursorCountsAsCarried() {
        overworld.markHeld(3, player, false);
        player.setItemOnCursor(overworld.createItem(3));

        check.run();
        check.run();

        assertTrue(overworld.isHeldBy(3, player.getUniqueId()));
        assertTrue(ballEntities().isEmpty(), "no second copy dropped");
    }

    @Test
    void aBallLyingLooseAfterLeavingItsHolderBecomesTheTrackedDrop() {
        overworld.markHeld(2, player, false);
        Item displaced = displaceIntoTheWorld(2);

        check.run();
        check.run();

        assertTrue(overworld.isDroppedAs(2, displaced.getUniqueId()));
        assertEquals(List.of(displaced), flagged, "it gets the no-despawn/invulnerable flags of any ball drop");
        assertEquals(List.of(displaced), ballEntities(), "no second copy dropped");
    }

    @Test
    void aFarAwayCopyIsNotTakenForTheMissingBall() {
        overworld.markHeld(2, player, false);
        Item farCopy = world.dropItem(new Location(world, 50 + HeldBallCheck.STRAY_RADIUS + 10, 64, 50), overworld.createItem(2));

        check.run();
        check.run();

        assertFalse(overworld.isDroppedAs(2, farCopy.getUniqueId()), "an old copy far away is a dupe, not the ball");
        assertEquals(2, ballEntities().size(), "the ball drops at the holder's feet instead");
    }

    @Test
    void aBallWipedFromTheInventoryDropsAtTheHoldersFeetOnTheSecondMiss() {
        overworld.markHeld(4, player, true);

        check.run();
        assertTrue(ballEntities().isEmpty(), "one miss is not enough");
        check.run();

        List<Item> dropped = ballEntities();
        assertEquals(1, dropped.size());
        Item ball = dropped.get(0);
        assertTrue(ball.getLocation().distance(player.getLocation()) < 1, "at the holder's feet");
        assertEquals(4, items.starOf(ball.getItemStack()).getAsInt());
        assertTrue(items.isStoneItem(ball.getItemStack()), "keeps its stone look");
        assertTrue(overworld.isDroppedAs(4, ball.getUniqueId()));
        assertEquals(List.of(ball), flagged);
        assertEquals("Your 4-Star Dragon Ball dropped at your feet.", player.nextMessage());
    }

    @Test
    void theDroppedBallIsNotDroppedAgain() {
        overworld.markHeld(4, player, false);

        for (int run = 0; run < 5; run++) {
            check.run();
        }

        assertEquals(1, ballEntities().size());
    }

    @Test
    void aCreativeHolderGetsLongerBeforeTheDrop() {
        overworld.markHeld(4, player, false);
        player.setGameMode(GameMode.CREATIVE);

        for (int run = 1; run < HeldBallCheck.CREATIVE_MISSES_BEFORE_DROP; run++) {
            check.run();
        }
        assertTrue(ballEntities().isEmpty(), "a creative drag is not a loss");
        assertEquals(BallState.HELD, overworld.get(4).orElseThrow().state());
        check.run();

        assertEquals(1, ballEntities().size());
    }

    @Test
    void aMissThenACarriedCheckStartsTheCountOver() {
        overworld.markHeld(4, player, false);

        check.run();
        player.getInventory().addItem(overworld.createItem(4));
        check.run();
        player.getInventory().clear();
        check.run();

        assertTrue(ballEntities().isEmpty(), "no second copy dropped");
    }

    @Test
    void anOfflineHolderIsLeftToTheLogoutDrop() {
        overworld.markHeld(5, player, false);
        player.disconnect();

        check.run();
        check.run();

        assertTrue(ballEntities().isEmpty(), "no second copy dropped");
        assertEquals(BallState.HELD, overworld.get(5).orElseThrow().state());
    }
}
