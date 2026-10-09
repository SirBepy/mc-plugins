package dev.sirbepy.mc.endfight.reward;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The inventory -&gt; ender chest -&gt; ground chain, isolated from XP/config so a failure here
 * points straight at the delivery order instead of the reward math around it. */
class RewardDeliveryTest {

    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void fill(org.bukkit.inventory.Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, new ItemStack(Material.STONE, 64));
        }
    }

    @Test
    void aBundleThatFitsGoesToTheInventory() {
        String where = RewardDelivery.deliver(player, new ItemStack(Material.BUNDLE));
        assertEquals("inventory", where);
        assertEquals(Material.BUNDLE, player.getInventory().getStorageContents()[0].getType());
    }

    @Test
    void aFullInventorySendsItToTheEnderChest() {
        fill(player.getInventory());

        String where = RewardDelivery.deliver(player, new ItemStack(Material.BUNDLE));

        assertEquals("ender chest", where);
        assertEquals(Material.BUNDLE, player.getEnderChest().getStorageContents()[0].getType());
    }

    @Test
    void aFullInventoryAndEnderChestDropsItOnTheGround() {
        fill(player.getInventory());
        fill(player.getEnderChest());

        String where = RewardDelivery.deliver(player, new ItemStack(Material.BUNDLE));

        assertEquals("the ground", where);
        World world = player.getWorld();
        boolean dropped = world.getEntitiesByClass(org.bukkit.entity.Item.class).stream()
                .anyMatch(item -> item.getItemStack().getType() == Material.BUNDLE);
        assertTrue(dropped, "the bundle must land in the world when neither storage has room");
    }
}
