package dev.sirbepy.mc.cartel.call;

import dev.sirbepy.mc.cartel.CartelTestBase;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CousinsTest extends CartelTestBase {

    @Test
    void marcosPistolIsAPlainUnbreakableBowWithThePackLook() {
        ItemStack pistol = Cousins.pistol();

        assertEquals(Material.BOW, pistol.getType());
        assertTrue(pistol.getEnchantments().isEmpty(), "no Power, Punch or Flame");
        assertTrue(pistol.getItemMeta().isUnbreakable());
        assertEquals(List.of(Cousins.PISTOL_LOOK), pistol.getItemMeta().getCustomModelDataComponent().getStrings());
    }
}
