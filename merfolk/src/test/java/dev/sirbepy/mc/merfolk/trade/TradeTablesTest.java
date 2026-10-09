package dev.sirbepy.mc.merfolk.trade;

import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeTablesTest {

    private static final UUID VILLAGER = UUID.fromString("00000000-0000-0000-0000-00000000abcd");
    /** The wish's list of sea commons for the extra trade, plus the coral colours. */
    private static final Set<Material> SEA_COMMONS = Set.of(Material.COD, Material.SALMON, Material.TROPICAL_FISH,
            Material.KELP, Material.INK_SAC, Material.SEAGRASS, Material.SEA_PICKLE, Material.TUBE_CORAL,
            Material.BRAIN_CORAL, Material.BUBBLE_CORAL, Material.FIRE_CORAL, Material.HORN_CORAL);

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void everyVanillaJobGetsOneCommonSeaTradeForEmeralds() {
        for (Villager.Profession profession : Registry.VILLAGER_PROFESSION) {
            MerchantRecipe trade = SeaTrade.forProfession(profession, VILLAGER);
            String key = profession.getKey().getKey();
            if (key.equals("none") || key.equals("nitwit")) {
                assertNull(trade, key);
                continue;
            }
            assertNotNull(trade, key);
            assertTrue(SEA_COMMONS.contains(trade.getResult().getType()), key + " sells " + trade.getResult().getType());
            assertEquals(Material.EMERALD, trade.getIngredients().getFirst().getType(), key);
            assertFalse(RareLedger.isRare(trade.getResult().getType()), key);
        }
    }

    @Test
    void theTreasurerSellsTheThreeRaresAtTheWishPricesWithNoDiscounts() {
        List<MerchantRecipe> trades = Specialist.TREASURER.tradesUpTo(5, VILLAGER);
        assertEquals(3, trades.size());
        assertRare(trades.get(0), Material.TRIDENT, 4);
        assertRare(trades.get(1), Material.SPONGE, 2);
        assertRare(trades.get(2), Material.HEART_OF_THE_SEA, 8);
        assertTrue(trades.stream().noneMatch(t -> t.getResult().getType() == Material.CONDUIT));
    }

    private static void assertRare(MerchantRecipe trade, Material item, int diamonds) {
        assertEquals(item, trade.getResult().getType());
        assertEquals(1, trade.getIngredients().size());
        assertEquals(Material.DIAMOND, trade.getIngredients().getFirst().getType());
        assertEquals(diamonds, trade.getIngredients().getFirst().getAmount());
        assertTrue(trade.shouldIgnoreDiscounts());
        assertEquals(0f, trade.getPriceMultiplier());
    }

    @Test
    void fisherAndMasonUnlockTwoTradesPerLevelLikeVanillaAndSellNoRares() {
        for (Specialist specialist : List.of(Specialist.FISHER, Specialist.MASON)) {
            assertEquals(2, specialist.tradesUpTo(1, VILLAGER).size());
            assertEquals(9, specialist.tradesUpTo(5, VILLAGER).size());
            for (MerchantRecipe trade : specialist.tradesUpTo(5, VILLAGER)) {
                assertFalse(RareLedger.isRare(trade.getResult().getType()), specialist + " " + trade.getResult().getType());
            }
        }
        List<Material> masonGoods = Specialist.MASON.tradesUpTo(5, VILLAGER).stream().map(t -> t.getResult().getType()).toList();
        assertTrue(masonGoods.containsAll(List.of(Material.PRISMARINE_SHARD, Material.PRISMARINE_CRYSTALS, Material.PRISMARINE,
                Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE, Material.SEA_LANTERN)));
        List<Material> fisherGoods = Specialist.FISHER.tradesUpTo(5, VILLAGER).stream().map(t -> t.getResult().getType()).toList();
        assertTrue(fisherGoods.containsAll(List.of(Material.COD, Material.SALMON, Material.INK_SAC, Material.SEA_PICKLE,
                Material.NAUTILUS_SHELL, Material.TROPICAL_FISH)));
    }

    @Test
    void aVillagersCoralColourIsStable() {
        assertEquals(Trades.coral(VILLAGER, 1), Trades.coral(VILLAGER, 1));
    }

    @Test
    void specialistsBorrowFishermanMasonAndCartographer() {
        assertEquals(Specialist.FISHER, Specialist.forProfession(Villager.Profession.FISHERMAN));
        assertEquals(Specialist.MASON, Specialist.forProfession(Villager.Profession.MASON));
        assertEquals(Specialist.TREASURER, Specialist.forProfession(Villager.Profession.CARTOGRAPHER));
        assertNull(Specialist.forProfession(Villager.Profession.FARMER));
    }
}
