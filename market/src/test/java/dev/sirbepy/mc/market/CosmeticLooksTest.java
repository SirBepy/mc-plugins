package dev.sirbepy.mc.market;

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cosmetic requests #2 (golden wings) and #3 (halo), dvolic21, 2026-10-10: the worn half. */
class CosmeticLooksTest {

    private static final NamespacedKey GOLDEN_WINGS = NamespacedKey.fromString("claudesmp:golden_wings");

    private ServerMock server;
    private PluginMock plugin;
    private CosmeticLooks looks;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        looks = new CosmeticLooks(plugin, Map.of(2, Optional.of(GOLDEN_WINGS), 3, Optional.empty()), plugin.getLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ItemStack stamped(Material material, int id) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "cosmetic_id"), PersistentDataType.INTEGER, id));
        return item;
    }

    private static EquippableComponent equippable(ItemStack item) {
        assertTrue(item.getItemMeta().hasEquippable());
        return item.getItemMeta().getEquippable();
    }

    @Test
    void theJarsLooksFileMapsWingsToTheirAssetAndTheHaloToNone() throws Exception {
        try (InputStream stream = Objects.requireNonNull(getClass().getResourceAsStream("/cosmetic-looks.yml"))) {
            Map<Integer, Optional<NamespacedKey>> loaded = CosmeticLooks.load(stream, Logger.getAnonymousLogger());
            assertEquals(Map.of(2, Optional.of(GOLDEN_WINGS), 3, Optional.empty()), loaded);
        }
    }

    @Test
    void aBadLineIsSkippedNotFatal() {
        String yaml = "2: claudesmp:golden_wings\nx: none\n4: 'Not A Key!'\n5: none\n";
        var loaded = CosmeticLooks.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), Logger.getAnonymousLogger());
        assertEquals(Map.of(2, Optional.of(GOLDEN_WINGS), 5, Optional.empty()), loaded);
    }

    @Test
    void wingsKeepTheElytrasOwnEquippableWithTheGoldenAsset() {
        ItemStack elytra = stamped(Material.ELYTRA, 2);
        assertTrue(looks.apply(elytra));
        EquippableComponent e = equippable(elytra);
        assertEquals(GOLDEN_WINGS, e.getModel());
        assertEquals(EquipmentSlot.CHEST, e.getSlot());
        assertEquals(Sound.ITEM_ARMOR_EQUIP_ELYTRA, e.getEquipSound());
        assertFalse(e.isDamageOnHurt());
        assertFalse(looks.apply(elytra), "already applied, nothing to change");
    }

    @Test
    void theHaloClearsTheHelmetsAssetSoItsModelIsDrawnOnTheHead() {
        ItemStack helmet = stamped(Material.NETHERITE_HELMET, 3);
        assertTrue(looks.apply(helmet));
        EquippableComponent e = equippable(helmet);
        assertNull(e.getModel());
        assertEquals(EquipmentSlot.HEAD, e.getSlot());
        assertEquals(Sound.ITEM_ARMOR_EQUIP_NETHERITE, e.getEquipSound());
        assertFalse(looks.apply(helmet));
    }

    @Test
    void otherItemsAreLeftAlone() {
        ItemStack plain = ItemStack.of(Material.ELYTRA);
        assertFalse(looks.apply(plain));
        assertFalse(plain.getItemMeta().hasEquippable());
        ItemStack lookless = stamped(Material.NETHERITE_PICKAXE, 1);  // #1 is a pack-only look
        assertFalse(looks.apply(lookless));
        assertFalse(lookless.getItemMeta().hasEquippable());
        assertFalse(looks.apply(null));
    }

    @Test
    void joiningAppliesTheLookToWornAndCarriedItems() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setChestplate(stamped(Material.ELYTRA, 2));
        player.getInventory().setItem(20, stamped(Material.NETHERITE_HELMET, 3));
        looks.onJoin(new PlayerJoinEvent(player, net.kyori.adventure.text.Component.empty()));
        assertEquals(GOLDEN_WINGS, equippable(player.getInventory().getChestplate()).getModel());
        assertNull(equippable(player.getInventory().getItem(20)).getModel());
    }

    @Test
    void puttingItOnLaterAppliesTheLookATickLater() {
        PlayerMock player = server.addPlayer();
        ItemStack helmet = stamped(Material.NETHERITE_HELMET, 3);
        player.getInventory().setHelmet(helmet);
        looks.onEquip(new EntityEquipmentChangedEvent(player, Map.of(EquipmentSlot.HEAD, change(null, helmet))));
        assertFalse(player.getInventory().getHelmet().getItemMeta().hasEquippable());
        server.getScheduler().performOneTick();
        assertNull(equippable(player.getInventory().getHelmet()).getModel());
    }

    private static EntityEquipmentChangedEvent.EquipmentChange change(ItemStack oldItem, ItemStack newItem) {
        return new EntityEquipmentChangedEvent.EquipmentChange() {
            @Override
            public ItemStack oldItem() {
                return oldItem;
            }

            @Override
            public ItemStack newItem() {
                return newItem;
            }
        };
    }
}
