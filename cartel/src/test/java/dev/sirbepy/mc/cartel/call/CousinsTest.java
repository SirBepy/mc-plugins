package dev.sirbepy.mc.cartel.call;

import dev.sirbepy.mc.cartel.CartelTestBase;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
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
        assertEquals(Map.of(Enchantment.PIERCING, 4, Enchantment.QUICK_CHARGE, 3),
                crossbow.getEnchantments());
        assertTrue(crossbow.getItemMeta().isUnbreakable());
        assertEquals(List.of(Cousins.PISTOL_LOOK), crossbow.getItemMeta().getCustomModelDataComponent().getStrings());
    }

    @Test
    void theyWearFullIronArmorAndEveryPieceWearsTheSalamancaLook() {
        Map<EquipmentSlot, ItemStack> outfit = Cousins.outfit();

        assertEquals(Material.IRON_HELMET, outfit.get(EquipmentSlot.HEAD).getType());
        assertEquals(Material.IRON_CHESTPLATE, outfit.get(EquipmentSlot.CHEST).getType());
        assertEquals(Material.IRON_LEGGINGS, outfit.get(EquipmentSlot.LEGS).getType());
        assertEquals(Material.IRON_BOOTS, outfit.get(EquipmentSlot.FEET).getType());
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            var look = outfit.get(slot).getItemMeta().getEquippable();
            assertEquals(Cousins.LOOK, look.getModel(), slot.name());
            assertEquals(slot, look.getSlot(), slot.name());
            assertTrue(outfit.get(slot).getItemMeta().isUnbreakable(), slot.name());
        }
        assertEquals(Material.CROSSBOW, outfit.get(EquipmentSlot.HAND).getType());
    }

    @Test
    void aBoltCarriesPiercingIvAndACritAndCantBePickedUp() {
        Arrow bolt = world.spawn(owner.getLocation(), Arrow.class);

        RangedGoal.load(bolt);

        assertEquals(12.0, bolt.getDamage(), "enough to land within a victim's hurt immunity after a 6-8 hit");
        assertEquals(4, bolt.getPierceLevel());
        assertTrue(bolt.isCritical());
        assertEquals(AbstractArrow.PickupStatus.DISALLOWED, bolt.getPickupStatus());
    }

    @Test
    void theRocketHasStarsSoItExplodesAndOutfliesTheCrossbowsRange() {
        FireworkMeta rocket = (FireworkMeta) RangedGoal.rocket().getItemMeta();

        assertEquals(RangedGoal.ROCKET_STARS, rocket.getEffects().size());
        assertTrue(rocket.getEffects().size() > 0, "a rocket with no stars has no blast");
        assertEquals(2, rocket.getPower(), "20+ ticks of flight at 1.6 is 32+ blocks, past the 16-block range");
    }
}
