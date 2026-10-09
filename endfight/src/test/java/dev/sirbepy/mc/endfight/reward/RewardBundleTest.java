package dev.sirbepy.mc.endfight.reward;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardBundleTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aMissingSectionFallsBackToThePlaceholderDragonHoard() {
        RewardBundle bundle = RewardBundle.from(null);

        ItemStack stack = bundle.toItemStack();

        assertEquals(Material.BUNDLE, stack.getType());
        BundleMeta meta = (BundleMeta) stack.getItemMeta();
        assertEquals("Dragon Hoard", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(meta.displayName()));
    }

    @Test
    void configuredItemsBecomeBundleContents() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rewards.bundle.name", "<gold>Test Hoard");
        config.set("rewards.bundle.items", List.of(
                Map.of("material", "DIAMOND", "amount", 4),
                Map.of("material", "IRON_SWORD", "name", "<red>Fang", "enchantments", Map.of("sharpness", 3))));

        RewardBundle bundle = RewardBundle.from(config.getConfigurationSection("rewards.bundle"));
        ItemStack stack = bundle.toItemStack();
        BundleMeta meta = (BundleMeta) stack.getItemMeta();

        assertEquals(2, meta.getItems().size());
        assertTrue(meta.getItems().stream().anyMatch(i -> i.getType() == Material.DIAMOND && i.getAmount() == 4));
        assertTrue(meta.getItems().stream().anyMatch(i -> i.getType() == Material.IRON_SWORD
                && i.getEnchantments().getOrDefault(org.bukkit.enchantments.Enchantment.SHARPNESS, 0) == 3));
    }

    @Test
    void anEntryWithNoMaterialIsSkippedNotCrashed() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rewards.bundle.items", List.of(Map.of("amount", 4)));

        RewardBundle bundle = RewardBundle.from(config.getConfigurationSection("rewards.bundle"));
        BundleMeta meta = (BundleMeta) bundle.toItemStack().getItemMeta();

        assertTrue(meta.getItems().isEmpty());
    }
}
