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
        return new BallRegistry(file, server.getLogger(), items, dev.sirbepy.mc.dragonballs.ball.BallSet.OVERWORLD);
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
        Item item = world.dropItem(new Location(world, 10, 65, 10), registry.createItem(6));
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

        Item droppedItem = world.dropItem(new Location(world, 5, 65, 5), registry.createItem(3));
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
        player.getInventory().setItem(0, items.createStoneItem(dev.sirbepy.mc.dragonballs.ball.BallSet.OVERWORLD, 7));
        registry.setStoneUntil(StoneMode.MINECRAFT_DAYS, 1);

        registry.clearStone();

        var slot0 = player.getInventory().getItem(0);
        assertFalse(items.isStoneItem(slot0), "the stone item in the player's inventory must be swapped to active");
        assertEquals(7, items.starOf(slot0).orElseThrow());
    }

    @Test
    void clearStoneSwapsADroppedItemEntitysStackBackToActive() {
        BallRegistry registry = newRegistry();
        Item item = world.dropItem(new Location(world, 0, 65, 0), items.createStoneItem(dev.sirbepy.mc.dragonballs.ball.BallSet.OVERWORLD, 1));
        registry.markDropped(1, item, true);
        registry.setStoneUntil(StoneMode.MINECRAFT_DAYS, 1);

        registry.clearStone();

        assertFalse(items.isStoneItem(item.getItemStack()));
        assertEquals(1, items.starOf(item.getItemStack()).orElseThrow());
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

    @Test
    void registryReportsTheSetItWasConstructedWith() {
        BallRegistry overworld = newRegistry();
        assertEquals(dev.sirbepy.mc.dragonballs.ball.BallSet.OVERWORLD, overworld.set());

        BallRegistry nether = new BallRegistry(new File(file.getParentFile(), "balls-nether.yml"), server.getLogger(),
                items, dev.sirbepy.mc.dragonballs.ball.BallSet.NETHER);
        assertEquals(dev.sirbepy.mc.dragonballs.ball.BallSet.NETHER, nether.set());
    }

    @Test
    void aLegacyBallsYmlPredatingThe3SetSplitLoadsAsTheOverworldSetWithItsWishCountIntact() {
        // Hand-written in the exact pre-split shape: no "set" concept existed in the file format at all.
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("balls.1.state", "PLACED");
        legacy.set("balls.1.world", "world");
        legacy.set("balls.1.x", 10.0);
        legacy.set("balls.1.y", 64.0);
        legacy.set("balls.1.z", 10.0);
        legacy.set("global.wishCount", 5);
        try {
            legacy.save(file);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }

        BallRegistry registry = newRegistry();

        assertEquals(dev.sirbepy.mc.dragonballs.ball.BallSet.OVERWORLD, registry.set());
        assertEquals(5, registry.wishCount());
        assertEquals(BallState.PLACED, registry.get(1).orElseThrow().state());
        assertEquals(10, registry.get(1).orElseThrow().x(), 0.001);
    }

    @Test
    void eachSetPersistsToItsOwnFileWithoutCollidingWithAnother() {
        BallRegistry overworld = newRegistry();
        BallRegistry nether = new BallRegistry(new File(file.getParentFile(), "balls-nether.yml"), server.getLogger(),
                items, dev.sirbepy.mc.dragonballs.ball.BallSet.NETHER);

        overworld.markPlaced(1, new Location(world, 1, 64, 1), false);
        overworld.incrementWishCount();
        nether.markPlaced(1, new Location(world, 2, 64, 2), false);
        nether.incrementWishCount();
        nether.incrementWishCount();

        BallRegistry reloadedOverworld = newRegistry();
        BallRegistry reloadedNether = new BallRegistry(new File(file.getParentFile(), "balls-nether.yml"),
                server.getLogger(), items, dev.sirbepy.mc.dragonballs.ball.BallSet.NETHER);

        assertEquals(1, reloadedOverworld.wishCount());
        assertEquals(2, reloadedNether.wishCount());
        assertEquals(1, reloadedOverworld.get(1).orElseThrow().x(), 0.001);
        assertEquals(2, reloadedNether.get(1).orElseThrow().x(), 0.001);
    }

    /** Records what the registry asks the world to show, in order. */
    private static final class RecordingLook implements PlacedBallLook {
        final List<String> calls = new java.util.ArrayList<>();

        @Override
        public void show(dev.sirbepy.mc.dragonballs.ball.BallSet set, int star, Location block, boolean stone) {
            calls.add("show " + star + " " + block.getBlockX() + "," + block.getBlockY() + "," + block.getBlockZ()
                    + (stone ? " stone" : ""));
        }

        @Override
        public void hide(dev.sirbepy.mc.dragonballs.ball.BallSet set, int star) {
            calls.add("hide " + star);
        }

        @Override
        public void glow(dev.sirbepy.mc.dragonballs.ball.BallSet set, boolean on) {
            calls.add("glow " + on);
        }
    }

    @Test
    void placingShowsTheBallAndTakingItHidesIt() {
        BallRegistry registry = newRegistry();
        RecordingLook look = new RecordingLook();
        registry.setLook(look);
        PlayerMock player = server.addPlayer();

        registry.markPlaced(3, new Location(world, 10.7, 64, -4.2), false);
        registry.markHeld(3, player, false);
        registry.markHeld(3, player, false);

        assertEquals(List.of("show 3 10,64,-5", "hide 3"), look.calls, "holding an already-held ball hides nothing");
    }

    @Test
    void aBallFlyingOffStaysHiddenUntilItLands() {
        BallRegistry registry = newRegistry();
        RecordingLook look = new RecordingLook();
        registry.setLook(look);
        registry.markPlaced(1, new Location(world, 16, 64, 16), false);

        registry.hidePlaced(1);

        assertEquals("hide 1", look.calls.get(look.calls.size() - 1));
        assertTrue(registry.placedBallsInChunk("world", 1, 1).isEmpty(), "a hidden ball never respawns at its old spot");
        registry.markPlaced(1, new Location(world, 40, 70, 40), true);
        assertEquals("show 1 40,70,40 stone", look.calls.get(look.calls.size() - 1));
    }

    @Test
    void showPlacedShowsEveryPlacedBallAtStartup() {
        BallRegistry registry = newRegistry();
        for (int star = 1; star <= 7; star++) {
            registry.markPlaced(star, new Location(world, star, 64, 0), false);
        }
        RecordingLook look = new RecordingLook();
        BallRegistry reloaded = newRegistry();
        reloaded.setLook(look);

        reloaded.showPlaced();

        assertEquals(7, look.calls.size());
        assertEquals("show 4 4,64,0", look.calls.get(3));
    }

    @Test
    void endingStoneShowsPlacedBallsAwakeAgain() {
        BallRegistry registry = newRegistry();
        RecordingLook look = new RecordingLook();
        registry.setLook(look);
        registry.markPlaced(2, new Location(world, 5, 64, 5), true);
        registry.setStoneUntil(StoneMode.REAL_HOURS, 0);

        registry.clearStone();

        assertEquals("show 2 5,64,5", look.calls.get(look.calls.size() - 1));
    }

    @Test
    void glowIsPassedThroughToTheLook() {
        BallRegistry registry = newRegistry();
        RecordingLook look = new RecordingLook();
        registry.setLook(look);

        registry.glowPlaced(true);
        registry.glowPlaced(false);

        assertEquals(List.of("glow true", "glow false"), look.calls);
    }
}
