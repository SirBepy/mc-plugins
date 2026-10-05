package dev.sirbepy.mc.tag.tracker;

import dev.sirbepy.mc.tag.config.TrackerConfig;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.PlayerInventoryViewMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tracker may move around IT's own inventory but never leave it. The listener only reads
 * TagService for right-clicks and inventory opens, so these tests pass null for it.
 */
class TrackerListenerTest {

    private ServerMock server;
    private TrackerService tracker;
    private TrackerListener listener;
    private PlayerMock player;
    private ItemStack compass;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        tracker = new TrackerService(plugin);
        listener = new TrackerListener(null, tracker);
        player = server.addPlayer();
        compass = tracker.build(TrackerConfig.from(null));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private InventoryClickEvent ownInventoryClick(ItemStack cursor, ItemStack current, InventoryType.SlotType slotType,
                                                  int rawSlot, InventoryAction action) {
        PlayerInventoryViewMock view = new PlayerInventoryViewMock(player, player.getInventory());
        view.setCursor(cursor);
        if (current != null) {
            view.setItem(rawSlot, current);
        }
        return new InventoryClickEvent(view, slotType, rawSlot, ClickType.LEFT, action);
    }

    @Test
    void movingTheTrackerWithinTheirOwnInventoryIsAllowed() {
        InventoryClickEvent event = ownInventoryClick(compass, null, InventoryType.SlotType.CONTAINER, 20,
                InventoryAction.PLACE_ALL);

        listener.onClick(event);

        assertFalse(event.isCancelled(), "rearranging the compass inside IT's own inventory must work");
    }

    @Test
    void placingTheTrackerIntoTheCraftingGridIsBlocked() {
        InventoryClickEvent event = ownInventoryClick(compass, null, InventoryType.SlotType.CRAFTING, 1,
                InventoryAction.PLACE_ALL);

        listener.onClick(event);

        assertTrue(event.isCancelled(), "the 2x2 grid is a way out of the inventory (close the window and it drops)");
    }

    @Test
    void stuffingTheTrackerIntoABundleIsBlocked() {
        InventoryClickEvent event = ownInventoryClick(compass, new ItemStack(Material.BUNDLE),
                InventoryType.SlotType.CONTAINER, 20, InventoryAction.SWAP_WITH_CURSOR);

        listener.onClick(event);

        assertTrue(event.isCancelled(), "a bundle could carry the compass out and be dropped");
    }

    @Test
    void clickingOutsideTheWindowToDropTheTrackerIsBlocked() {
        InventoryClickEvent event = ownInventoryClick(compass, null, InventoryType.SlotType.OUTSIDE, -999,
                InventoryAction.DROP_ALL_CURSOR);

        listener.onClick(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void anyTrackerClickWhileAChestIsOpenIsBlocked() {
        Inventory chest = server.createInventory(null, 27);
        InventoryView view = player.openInventory(chest);
        view.setCursor(compass);
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0,
                ClickType.LEFT, InventoryAction.PLACE_ALL);

        listener.onClick(event);

        assertTrue(event.isCancelled(), "the compass must never land in a chest");
    }

    @Test
    void ordinaryItemsAreLeftAlone() {
        Inventory chest = server.createInventory(null, 27);
        InventoryView view = player.openInventory(chest);
        view.setCursor(new ItemStack(Material.DIAMOND));
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0,
                ClickType.LEFT, InventoryAction.PLACE_ALL);

        listener.onClick(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void droppingTheTrackerIsCancelled() {
        Item drop = player.getWorld().dropItem(player.getLocation(), compass);
        PlayerDropItemEvent event = new PlayerDropItemEvent(player, drop);

        listener.onDrop(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void giveSkipsAPlayerWhoAlreadyHasOneAndRevokeTakesItBack() {
        server.addSimpleWorld("world");
        TrackerConfig config = TrackerConfig.from(null);

        tracker.give(player, config);
        tracker.give(player, config);
        long carried = Arrays.stream(player.getInventory().getContents()).filter(tracker::isTracker).count();
        assertEquals(1, carried, "IT must carry exactly one tracker");

        tracker.revoke(player);
        assertTrue(Arrays.stream(player.getInventory().getContents()).noneMatch(tracker::isTracker),
                "revoke must take every tracker away");
    }

    @Test
    void giveToAFullInventoryBumpsTheLastStorageItemToTheEnderChest() {
        // Every slot, not just storage (0-35): MockBukkit's addItem spills into armor/offhand and its
        // extra equipment slots, Paper's only fills storage.
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            player.getInventory().setItem(i, new ItemStack(Material.DIRT));
        }
        player.getInventory().setItem(35, new ItemStack(Material.DIAMOND));

        tracker.give(player, TrackerConfig.from(null));

        assertTrue(tracker.isTracker(player.getInventory().getItem(35)));
        assertTrue(player.getEnderChest().contains(Material.DIAMOND), "the bumped item must not be lost");
    }
}
