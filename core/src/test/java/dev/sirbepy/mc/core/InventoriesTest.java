package dev.sirbepy.mc.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoriesTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer();
        player.teleport(new Location(world, 10, 64, -20));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private List<Item> dropped() {
        return List.copyOf(world.getEntitiesByClass(Item.class));
    }

    @Test
    void anItemThatFitsGoesIntoTheInventory() {
        Inventories.giveOrDrop(player, new ItemStack(Material.DIAMOND, 3));

        assertTrue(player.getInventory().contains(Material.DIAMOND, 3));
        assertTrue(dropped().isEmpty());
    }

    @Test
    void aFullInventoryDropsTheItemAtThePlayersFeet() {
        // MockBukkit's addItem also fills the off hand, so every slot is filled, not just storage.
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.DIRT, 64));
        }

        Inventories.giveOrDrop(player, new ItemStack(Material.DIAMOND, 3));

        assertEquals(1, dropped().size());
        Item item = dropped().get(0);
        assertEquals(new ItemStack(Material.DIAMOND, 3), item.getItemStack());
        assertEquals(player.getLocation().toBlockLocation(), item.getLocation().toBlockLocation());
    }

    @Test
    void onlyTheOverflowIsDropped() {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.DIRT, 64));
        }
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 60));

        Inventories.giveOrDrop(player, new ItemStack(Material.DIAMOND, 10));

        assertEquals(64, player.getInventory().getItem(0).getAmount());
        assertEquals(6, dropped().get(0).getItemStack().getAmount());
    }
}
