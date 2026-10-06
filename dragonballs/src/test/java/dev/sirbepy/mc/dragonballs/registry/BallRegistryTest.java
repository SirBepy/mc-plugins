package dev.sirbepy.mc.dragonballs.registry;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.config.StoneMode;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import org.bukkit.Location;
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
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registry state transitions, the balls.yml persistence round trip, and the dupe-protection
 * predicates every listener relies on. Each ball has exactly one state at a time; these tests
 * confirm mark* always leaves it that way, and that nothing survives a reload except what was saved.
 */
class BallRegistryTest {

    private ServerMock server;
    private WorldMock world;
    private BallItems items;
    private File file;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        PluginMock plugin = MockBukkit.createMockPlugin();
        items = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        file = new File(tempDir.toFile(), "balls.yml");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private BallRegistry newRegistry() {
        return new BallRegistry(file, server.getLogger(), items);
    }

    @Test
    void freshRegistryStartsWithAllSevenBallsPlacedAtOrigin() {
        BallRegistry registry = newRegistry();
        assertEquals(7, registry.getBalls().size());
        assertEquals(BallState.PLACED, registry.get(1).orElseThrow().state());
    }

    @Test
    void markPlacedThenReloadRoundTrips() {
        BallRegistry registry = newRegistry();
        Location location = new Location(world, 100, 65, -200);
        registry.markPlaced(3, location, true);

        BallRegistry reloaded = newRegistry();
        BallView view = reloaded.get(3).orElseThrow();
        assertEquals(BallState.PLACED, view.state());
        assertTrue(view.stone());
        assertEquals("world", view.worldName());
        assertEquals(100, view.x(), 0.001);
        assertEquals(65, view.y(), 0.001);
        assertEquals(-200, view.z(), 0.001);
    }

    @Test
    void markHeldThenReloadRoundTrips() {
        BallRegistry registry = newRegistry();
        PlayerMock player = server.addPlayer("Goku");
        player.teleport(new Location(world, 1, 2, 3));
        registry.markHeld(5, player, false);

        BallRegistry reloaded = newRegistry();
        BallView view = reloaded.get(5).orElseThrow();
        assertEquals(BallState.HELD, view.state());
        assertEquals(player.getUniqueId(), view.holderId());
        assertEquals("Goku", view.holderName());
        assertFalse(view.stone());
    }

    @Test
    void markDroppedThenReloadRoundTrips() {
        BallRegistry registry = newRegistry();
        Item item = world.dropItem(new Location(world, 10, 65, 10), items.createItem(6));
        registry.markDropped(6, item, false);

        BallRegistry reloaded = newRegistry();
        BallView view = reloaded.get(6).orElseThrow();
        assertEquals(BallState.DROPPED, view.state());
        assertEquals(item.getUniqueId(), view.entityId());
    }

    @Test
    void eachMarkClearsTheOtherStates() {
        BallRegistry registry = newRegistry();
        PlayerMock player = server.addPlayer();
        registry.markHeld(1, player, false);
        assertTrue(registry.isHeldBy(1, player.getUniqueId()));

        registry.markPlaced(1, new Location(world, 0, 64, 0), false);
        assertFalse(registry.isHeldBy(1, player.getUniqueId()), "placing must clear the old HELD claim");
        assertTrue(registry.isPlacedAt(1, new Location(world, 0, 64, 0)));
    }

    @Test
    void dupeDetectionPredicatesOnlyAcceptTheAuthoritativeClaim() {
        BallRegistry registry = newRegistry();
        PlayerMock holder = server.addPlayer();
        PlayerMock impostor = server.addPlayer();
        registry.markHeld(2, holder, false);

        assertTrue(registry.isHeldBy(2, holder.getUniqueId()));
        assertFalse(registry.isHeldBy(2, impostor.getUniqueId()), "a different player's copy must not be the authoritative one");

        Item droppedItem = world.dropItem(new Location(world, 5, 65, 5), items.createItem(3));
        assertFalse(registry.isDroppedAs(2, droppedItem.getUniqueId()), "star 2 is HELD, not DROPPED as this entity");
    }

    @Test
    void activeTargetsExcludesStoneBallsAndOfflineHolders() {
        BallRegistry registry = newRegistry();
        registry.markPlaced(1, new Location(world, 100, 64, 0), false);
        registry.markPlaced(2, new Location(world, 200, 64, 0), true); // stone: excluded

        PlayerMock online = server.addPlayer();
        online.teleport(new Location(world, 300, 64, 0));
        registry.markHeld(3, online, false);

        PlayerMock soonOffline = server.addPlayer();
        registry.markHeld(4, soonOffline, false);
        server.getScheduler().performTicks(1);
        soonOffline.disconnect();

        List<Location> targets = registry.activeTargets();
        assertEquals(2, targets.size(), "star 1 (placed) and star 3 (online holder) only");
        assertTrue(targets.stream().anyMatch(l -> l.getBlockX() == 100));
        assertTrue(targets.stream().anyMatch(l -> l.getBlockX() == 300));
    }

    @Test
    void wishCountStartsAtZeroAndPersistsAcrossReload() {
        BallRegistry registry = newRegistry();
        assertEquals(0, registry.wishCount());
        registry.incrementWishCount();
        registry.incrementWishCount();

        BallRegistry reloaded = newRegistry();
        assertEquals(2, reloaded.wishCount());
    }

    @Test
    void stoneExpiryInMinecraftDaysModeComparesTheDayNumber() {
        BallRegistry registry = newRegistry();
        registry.setStoneUntil(StoneMode.MINECRAFT_DAYS, 10);
        assertTrue(registry.isDormant());
        assertFalse(registry.isStoneExpired(9, 0));
        assertTrue(registry.isStoneExpired(10, 0));
        assertTrue(registry.isStoneExpired(11, 0));
    }

    @Test
    void stoneExpiryInRealHoursModeComparesWallClockMillis() {
        BallRegistry registry = newRegistry();
        registry.setStoneUntil(StoneMode.REAL_HOURS, 5_000);
        assertFalse(registry.isStoneExpired(0, 4_999));
        assertTrue(registry.isStoneExpired(0, 5_000));
    }

    @Test
    void stoneCooldownPersistsAcrossReload() {
        newRegistry().setStoneUntil(StoneMode.REAL_HOURS, 5_000);

        BallRegistry reloaded = newRegistry();
        assertTrue(reloaded.isDormant());
        assertTrue(reloaded.isStone(3));
        assertFalse(reloaded.isStoneExpired(0, 4_999));
        assertTrue(reloaded.isStoneExpired(0, 5_000));
    }

    @Test
    void noStoneUntilSetMeansAlreadyExpired() {
        BallRegistry registry = newRegistry();
        assertFalse(registry.isDormant());
        assertTrue(registry.isStoneExpired(0, 0));
    }

    @Test
    void setStoneUntilMarksEveryBallStoneAndClearStoneClearsThem() {
        BallRegistry registry = newRegistry();
        registry.setStoneUntil(StoneMode.MINECRAFT_DAYS, 3);
        for (BallView view : registry.getBalls()) {
            assertTrue(view.stone());
        }

        registry.clearStone();
        assertFalse(registry.isDormant());
        for (BallView view : registry.getBalls()) {
            assertFalse(view.stone());
        }
    }

    @Test
    void clearStoneSweepsAnOnlinePlayersStoneItemBackToActive() {
        BallRegistry registry = newRegistry();
        PlayerMock player = server.addPlayer();
        registry.markHeld(7, player, true);
        player.getInventory().setItem(0, items.createStoneItem(7));
        registry.setStoneUntil(StoneMode.MINECRAFT_DAYS, 1);

        registry.clearStone();

        var slot0 = player.getInventory().getItem(0);
        assertFalse(items.isStoneItem(slot0), "the stone item in the player's inventory must be swapped to active");
        assertEquals(7, items.starOf(slot0).orElseThrow());
    }

    @Test
    void clearStoneSwapsADroppedItemEntitysStackBackToActive() {
        BallRegistry registry = newRegistry();
        Item item = world.dropItem(new Location(world, 0, 65, 0), items.createStoneItem(1));
        registry.markDropped(1, item, true);
        registry.setStoneUntil(StoneMode.MINECRAFT_DAYS, 1);

        registry.clearStone();

        assertFalse(items.isStoneItem(item.getItemStack()));
        assertEquals(1, items.starOf(item.getItemStack()).orElseThrow());
    }

    @Test
    void touchHolderSeenOnlyAffectsAnActuallyHeldBall() {
        BallRegistry registry = newRegistry();
        PlayerMock player = server.addPlayer();
        registry.markHeld(1, player, false);
        registry.touchHolderSeen(1, 12345L);
        assertEquals(12345L, registry.get(1).orElseThrow().lastSeenMillis());

        // Star 2 is still PLACED; touching it must be a no-op, not throw or corrupt its state.
        registry.touchHolderSeen(2, 99999L);
        assertEquals(BallState.PLACED, registry.get(2).orElseThrow().state());
    }

    @Test
    void lastSummonRoundTripsThroughReload() {
        BallRegistry registry = newRegistry();
        registry.setLastSummon(new Location(world, 427, 70, -868));

        BallRegistry reloaded = newRegistry();
        Location lastSummon = reloaded.lastSummon();
        assertEquals(427, lastSummon.getBlockX());
        assertEquals(-868, lastSummon.getBlockZ());
    }

    @Test
    void scatterAllAndScatterOneDelegateToTheInjectedScatterer() {
        BallRegistry registry = newRegistry();
        var calledAll = new boolean[]{false};
        var calledOne = new int[]{-1};
        registry.setScatterer(new BallScatterer() {
            @Override
            public java.util.Map<Integer, Location> scatterAll(Location center, boolean stone, BallRegistry r) {
                calledAll[0] = true;
                return java.util.Map.of();
            }

            @Override
            public void scatterOne(int star, Location center, boolean stone, BallRegistry r) {
                calledOne[0] = star;
            }
        });

        registry.scatterAll(new Location(world, 0, 64, 0), false);
        registry.scatterOne(4, new Location(world, 0, 64, 0), false);

        assertTrue(calledAll[0]);
        assertEquals(4, calledOne[0]);
    }

    @Test
    void scatterAllWithoutAScattererThrowsRatherThanSilentlyNoOp() {
        BallRegistry registry = newRegistry();
        assertTrue(assertThrowsIllegalState(() -> registry.scatterAll(new Location(world, 0, 64, 0), false)));
    }

    private static boolean assertThrowsIllegalState(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
