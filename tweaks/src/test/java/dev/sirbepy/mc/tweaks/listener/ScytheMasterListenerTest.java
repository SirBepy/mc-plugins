package dev.sirbepy.mc.tweaks.listener;

import dev.aurelium.auraskills.api.damage.DamageModifier;
import dev.aurelium.auraskills.api.damage.DamageModifier.Operation;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScytheMasterListenerTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static double total(List<DamageModifier> modifiers) {
        return modifiers.stream().mapToDouble(DamageModifier::value).sum();
    }

    @Test
    void swordMasterIsReplacedByScytheMaster() {
        DamageModifier crit = new DamageModifier(0.5, Operation.MULTIPLY);
        List<DamageModifier> modifiers = new ArrayList<>(List.of(crit, new DamageModifier(0.13, Operation.ADD_COMBINED)));

        ScytheMasterListener.swapBonus(modifiers, 13, 9);

        assertEquals(2, modifiers.size());
        assertTrue(modifiers.contains(crit));
        assertEquals(0.5 + 0.09, total(modifiers), 1e-9);
    }

    @Test
    void onlyOneMatchingModifierIsRemoved() {
        List<DamageModifier> modifiers = new ArrayList<>(List.of(
                new DamageModifier(0.05, Operation.ADD_COMBINED), new DamageModifier(0.05, Operation.ADD_COMBINED)));

        ScytheMasterListener.swapBonus(modifiers, 5, 7);

        assertEquals(0.05 + 0.07, total(modifiers), 1e-9);
    }

    @Test
    void noSwordMasterLevelStillGetsScytheMaster() {
        List<DamageModifier> modifiers = new ArrayList<>();

        ScytheMasterListener.swapBonus(modifiers, 0, 3);

        assertEquals(0.03, total(modifiers), 1e-9);
    }

    @Test
    void swordMasterBlockedByAuraSkillsChecksMeansNoScytheMasterEither() {
        List<DamageModifier> modifiers = new ArrayList<>(List.of(new DamageModifier(0.2, Operation.MULTIPLY)));

        ScytheMasterListener.swapBonus(modifiers, 13, 9);

        assertEquals(1, modifiers.size());
        assertEquals(0.2, total(modifiers), 1e-9);
    }

    @Test
    void noScytheMasterLevelJustDropsSwordMaster() {
        List<DamageModifier> modifiers = new ArrayList<>(List.of(new DamageModifier(0.13, Operation.ADD_COMBINED)));

        ScytheMasterListener.swapBonus(modifiers, 13, 0);

        assertTrue(modifiers.isEmpty());
    }

    @Test
    void rmfScytheIsRecognised() {
        ItemStack scythe = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta meta = scythe.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString("rmf:scythe"));
        scythe.setItemMeta(meta);

        assertTrue(ScytheMasterListener.isScythe(scythe));
    }

    @Test
    void plainWoodenSwordAndOtherModelsAreNotScythes() {
        ItemStack plain = new ItemStack(Material.WOODEN_SWORD);
        ItemStack other = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta meta = other.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString("rmf:knife"));
        other.setItemMeta(meta);

        assertFalse(ScytheMasterListener.isScythe(plain));
        assertFalse(ScytheMasterListener.isScythe(other));
        assertFalse(ScytheMasterListener.isScythe(null));
    }
}
