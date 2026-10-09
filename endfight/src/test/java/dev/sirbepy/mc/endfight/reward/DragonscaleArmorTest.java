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

    @Test
    void everyPieceCarriesTheClaudesmpDragonscaleEquippableModel() {
        ItemStack item = DragonscaleArmor.build(ArmorPiece.CHESTPLATE);
        ItemMeta meta = item.getItemMeta();

        assertTrue(meta.hasEquippable());
        assertEquals(DragonscaleArmor.MODEL_KEY, meta.getEquippable().getModel());
    }

    @Test
    void eachPieceIsNamedDragonscaleAndMatchesItsSlotWord() {
        ItemStack boots = DragonscaleArmor.build(ArmorPiece.BOOTS);
        String plain = PlainTextComponentSerializer.plainText().serialize(boots.getItemMeta().displayName());

        assertEquals("Dragonscale Boots", plain);
    }
}
