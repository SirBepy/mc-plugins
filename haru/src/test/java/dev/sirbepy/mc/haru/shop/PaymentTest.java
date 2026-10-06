package dev.sirbepy.mc.haru.shop;

import dev.sirbepy.mc.haru.HaruTestBase;
import dev.sirbepy.mc.haru.item.Goods;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentTest extends HaruTestBase {

    @Test
    void emeraldBlocksCountAsNine() {
        give(owner, Material.EMERALD, 5);
        give(owner, Material.EMERALD_BLOCK, 2);
        assertEquals(23, Payment.emeraldsHeld(owner.getInventory()));
    }

    @Test
    void looseEmeraldsGoFirstThenABlockIsBrokenForChange() {
        give(owner, Material.EMERALD, 3);
        give(owner, Material.EMERALD_BLOCK, 2);

        assertTrue(Payment.take(owner, 5));

        assertEquals(1, count(owner, Material.EMERALD_BLOCK));
        assertEquals(7, count(owner, Material.EMERALD));
    }

    @Test
    void notEnoughTakesNothing() {
        give(owner, Material.EMERALD, 4);
        assertFalse(Payment.take(owner, 5));
        assertEquals(4, count(owner, Material.EMERALD));
    }

    @Test
    void renamedEmeraldsAreLeftAlone() {
        owner.getInventory().addItem(Goods.named(new ItemStack(Material.EMERALD, 10), "Lucky", Goods.SAKURA));
        assertEquals(0, Payment.emeraldsHeld(owner.getInventory()));
        assertFalse(Payment.take(owner, 1));
    }

    @Test
    void handInsTakeExactlyTheAmount() {
        give(owner, Material.CHERRY_LOG, 40);
        assertTrue(Payment.takeItems(owner.getInventory(), Material.CHERRY_LOG, 32));
        assertEquals(8, count(owner, Material.CHERRY_LOG));
        assertFalse(Payment.takeItems(owner.getInventory(), Material.CHERRY_LOG, 32));
        assertEquals(8, count(owner, Material.CHERRY_LOG));
    }
}
