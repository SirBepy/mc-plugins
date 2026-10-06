package dev.sirbepy.mc.dragonballs.radar;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarItemsTest {

    private RadarItems items;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        items = new RadarItems(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createdRadarIsAMarkedCompassShowingTheActiveModel() {
        ItemStack radar = items.create();

        assertEquals(Material.COMPASS, radar.getType());
        assertTrue(items.isRadar(radar));
        assertTrue(items.isShowingActive(radar));
        assertFalse(items.isShowingIdle(radar));
    }

    @Test
    void identificationIsByPdcOnlyNeverByModelString() {
        ItemStack lookalike = new ItemStack(Material.COMPASS);
        ItemMeta meta = lookalike.getItemMeta();
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of("dragon_radar"));
        meta.setCustomModelDataComponent(cmd);
        lookalike.setItemMeta(meta);

        assertFalse(items.isRadar(lookalike), "a compass with the right model but no PDC marker is not a radar");
    }

    @Test
    void plainCompassAndOtherMaterialsAreNeverRadars() {
        assertFalse(items.isRadar(new ItemStack(Material.COMPASS)));
        assertFalse(items.isRadar(new ItemStack(Material.STONE)));
        assertFalse(items.isRadar(null));
    }

    @Test
    void setActiveSwapsOnlyTheModelStringLeavingPdcAndNameIntact() {
        ItemStack radar = items.create();
        var originalName = radar.getItemMeta().displayName();

        items.setActive(radar, false);
        assertTrue(items.isShowingIdle(radar));
        assertFalse(items.isShowingActive(radar));
        assertTrue(items.isRadar(radar));
        assertEquals(originalName, radar.getItemMeta().displayName());

        items.setActive(radar, true);
        assertTrue(items.isShowingActive(radar));
    }
}
