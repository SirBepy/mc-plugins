package dev.sirbepy.mc.endfight.reward;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The item components a Dragonscale piece needs for the server pack's look (commit 9d59217) -
 * see {@link DragonscaleArmor}. */
class DragonscaleArmorTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theHelmetIsPlainNetheriteWithNoEnchantments() {
        ItemStack item = DragonscaleArmor.build(ArmorPiece.HELMET);

        assertEquals(Material.NETHERITE_HELMET, item.getType());
        assertTrue(item.getEnchantments().isEmpty());
    }

    @Test
    void everyPieceCarriesTheDragonscaleCustomModelDataString() {
        for (ArmorPiece piece : ArmorPiece.values()) {
            ItemStack item = DragonscaleArmor.build(piece);
            ItemMeta meta = item.getItemMeta();
            assertTrue(meta.hasCustomModelDataComponent());
            assertEquals(java.util.List.of("dragonscale"), meta.getCustomModelDataComponent().getStrings());
        }
    }

    // Paper's default equippable for a fresh meta is HEAD (javap, 26.3), which once put every piece
    // on the head.
    @Test
    void everyPieceEquipsToItsOwnSlotWithTheNetheriteSound() {
        for (ArmorPiece piece : ArmorPiece.values()) {
            var equippable = DragonscaleArmor.build(piece).getItemMeta().getEquippable();

            assertEquals(piece.slot(), equippable.getSlot(), piece.name());
            assertEquals(org.bukkit.Sound.ITEM_ARMOR_EQUIP_NETHERITE, equippable.getEquipSound(), piece.name());
        }
        assertEquals(org.bukkit.inventory.EquipmentSlot.CHEST, ArmorPiece.CHESTPLATE.slot());
        assertEquals(org.bukkit.inventory.EquipmentSlot.FEET, ArmorPiece.BOOTS.slot());
    }

    @Test
    void theBodyPiecesCarryTheClaudesmpDragonscaleEquippableModel() {
        for (ArmorPiece piece : new ArmorPiece[] {ArmorPiece.CHESTPLATE, ArmorPiece.LEGGINGS, ArmorPiece.BOOTS}) {
            ItemMeta meta = DragonscaleArmor.build(piece).getItemMeta();

            assertTrue(meta.hasEquippable());
            assertEquals(DragonscaleArmor.MODEL_KEY, meta.getEquippable().getModel(), piece.name());
        }
    }

    // 10ca's 3D dragon-skull helm: with no asset id the client draws the item model on the head.
    @Test
    void theHelmetGoesOnTheHeadWithNoArmorLayer() {
        ItemMeta meta = DragonscaleArmor.build(ArmorPiece.HELMET).getItemMeta();

        assertEquals(null, meta.getEquippable().getModel());
        assertEquals(org.bukkit.inventory.EquipmentSlot.HEAD, meta.getEquippable().getSlot());
    }

    @Test
    void eachPieceIsNamedDragonscaleAndMatchesItsSlotWord() {
        ItemStack boots = DragonscaleArmor.build(ArmorPiece.BOOTS);
        String plain = PlainTextComponentSerializer.plainText().serialize(boots.getItemMeta().displayName());

        assertEquals("Dragonscale Boots", plain);
    }
}
