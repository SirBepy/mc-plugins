package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.PlayerInventoryViewMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the "balls can never go into any container" rule: a direct click into a chest slot, a
 * shift-click that would auto-route there, a hopper's own item-move, and inserting one into a
 * bundle. Moving a ball between the player's OWN slots (a plain click there) must stay allowed.
 */
class BallContainerGuardListenerTest {

    private ServerMock server;
    private BallRegistry registry;
    private BallContainerGuardListener listener;
    private PlayerMock player;
    private ItemStack ball;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        BallItems items = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        registry = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items);
        listener = new BallContainerGuardListener(registry);
        player = server.addPlayer();
        ball = registry.createItem(3);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void directClickIntoAChestSlotIsCancelled() {
        Inventory chest = Bukkit.createInventory(null, InventoryType.CHEST);
        var view = player.openInventory(chest);
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0,
                ClickType.LEFT, InventoryAction.PLACE_ALL);
        event.getView().setCursor(ball);

        listener.onClick(event);

        assertTrue(event.isCancelled(), "placing a ball into a chest slot must be blocked");
    }

    @Test
    void shiftClickFromPlayerInventoryIsCancelled() {
        Inventory chest = Bukkit.createInventory(null, InventoryType.CHEST);
        var view = player.openInventory(chest);
        player.getInventory().setItem(9, ball);
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 9 + chest.getSize(),
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);

        listener.onClick(event);

        assertTrue(event.isCancelled(), "shift-clicking a ball toward the open container must be blocked");
    }

    @Test
    void plainClickWithinThePlayersOwnInventoryIsAllowed() {
        PlayerInventoryViewMock view = new PlayerInventoryViewMock(player, player.getInventory());
        view.setItem(9, ball);
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 9,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);

        listener.onClick(event);

        assertFalse(event.isCancelled(), "moving a ball within the player's own inventory must stay allowed");
    }

    @Test
    void hopperMoveOfABallIsCancelled() {
        Inventory source = Bukkit.createInventory(null, InventoryType.HOPPER);
        Inventory destination = Bukkit.createInventory(null, InventoryType.CHEST);
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(source, ball, destination, true);

        listener.onHopperMove(event);

        assertTrue(event.isCancelled(), "a hopper must never be able to move a ball");
    }

    @Test
    void aHopperCannotSuckADroppedBallOffTheGround() {
        Inventory hopper = server.createInventory(null, InventoryType.HOPPER);
        org.bukkit.entity.Item dropped = player.getWorld().dropItem(player.getLocation(), ball);
        InventoryPickupItemEvent event = new InventoryPickupItemEvent(hopper, dropped);

        listener.onHopperPickup(event);

        assertTrue(event.isCancelled(), "a hopper must never pick a ball up off the ground");
    }

    @Test
    void aHopperStillPicksUpOrdinaryItems() {
        Inventory hopper = server.createInventory(null, InventoryType.HOPPER);
        org.bukkit.entity.Item dropped = player.getWorld().dropItem(player.getLocation(), new ItemStack(Material.DIRT));
        InventoryPickupItemEvent event = new InventoryPickupItemEvent(hopper, dropped);

        listener.onHopperPickup(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void insertingABallIntoAnOpenBundleIsCancelled() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        PlayerInventoryViewMock view = new PlayerInventoryViewMock(player, player.getInventory());
        view.setItem(9, bundle);
        view.setCursor(ball);
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 9,
                ClickType.RIGHT, InventoryAction.PLACE_ONE);

        listener.onClick(event);

        assertTrue(event.isCancelled(), "a ball must never be insertable into a bundle");
    }
}
