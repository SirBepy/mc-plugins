package dev.sirbepy.mc.cartel.call;

import dev.sirbepy.mc.cartel.CartelTestBase;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CousinsTest extends CartelTestBase {

    @Test
    void theCrossbowIsAnUnbreakablePistolWithJoesEnchantments() {
        ItemStack crossbow = Cousins.crossbow();

        assertEquals(Material.CROSSBOW, crossbow.getType());
        assertEquals(Map.of(Enchantment.PIERCING, 4, Enchantment.QUICK_CHARGE, 3, Enchantment.POWER, 5),
                crossbow.getEnchantments());
        assertTrue(crossbow.getItemMeta().isUnbreakable());
        assertEquals(List.of(Cousins.PISTOL_LOOK), crossbow.getItemMeta().getCustomModelDataComponent().getStrings());
    }

    @Test
    void onlyAChainmailChestplateAndLeggingsAreArmorButEveryPieceWearsTheSalamancaLook() {
        Map<EquipmentSlot, ItemStack> outfit = Cousins.outfit();

        assertEquals(Material.CHAINMAIL_CHESTPLATE, outfit.get(EquipmentSlot.CHEST).getType());
        assertEquals(Material.CHAINMAIL_LEGGINGS, outfit.get(EquipmentSlot.LEGS).getType());
        assertEquals(Material.PAPER, outfit.get(EquipmentSlot.HEAD).getType(), "no helmet");
        assertEquals(Material.PAPER, outfit.get(EquipmentSlot.FEET).getType(), "no boots");
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            var look = outfit.get(slot).getItemMeta().getEquippable();
            assertEquals(Cousins.LOOK, look.getModel(), slot.name());
            assertEquals(slot, look.getSlot(), slot.name());
            assertTrue(outfit.get(slot).getItemMeta().isUnbreakable(), slot.name());
        }
        assertEquals(Material.CROSSBOW, outfit.get(EquipmentSlot.HAND).getType());
    }

    @Test
    void aBoltCarriesPowerVPiercingIvAndACritAndCantBePickedUp() {
        Arrow bolt = world.spawn(owner.getLocation(), Arrow.class);

        RangedGoal.load(bolt);

        assertEquals(5.0, bolt.getDamage(), "a 2-damage arrow +150%");
        assertEquals(4, bolt.getPierceLevel());
        assertTrue(bolt.isCritical());
        assertEquals(AbstractArrow.PickupStatus.DISALLOWED, bolt.getPickupStatus());
    }
}
