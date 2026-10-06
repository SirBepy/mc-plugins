package dev.sirbepy.mc.dragonballs.radar;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @Test
    void createWithNoSetArgumentIsOverworldAndNoSetPdcDefaultsToOverworld() {
        ItemStack radar = items.create();
        assertEquals(BallSet.OVERWORLD, items.setOf(radar));

        ItemStack legacy = new ItemStack(Material.COMPASS);
        ItemMeta meta = legacy.getItemMeta();
        meta.getPersistentDataContainer().set(items.radarKey(), org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        legacy.setItemMeta(meta);
        assertEquals(BallSet.OVERWORLD, items.setOf(legacy));
    }

    @Test
    void createForNetherAndEndCarriesTheirOwnSetAndName() {
        ItemStack nether = items.create(BallSet.NETHER);
        ItemStack end = items.create(BallSet.END);

        assertEquals(BallSet.NETHER, items.setOf(nether));
        assertEquals(BallSet.END, items.setOf(end));
        assertEquals("Nether Dragon Radar",
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(nether.getItemMeta().displayName()));
        assertEquals("End Dragon Radar",
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(end.getItemMeta().displayName()));
    }

    @Test
    void setActiveKeepsWhichSetTheRadarPointsAt() {
        ItemStack nether = items.create(BallSet.NETHER);
        assertTrue(items.isShowingActive(nether));

        items.setActive(nether, false);
        assertTrue(items.isShowingIdle(nether));
        assertFalse(items.isShowingActive(nether));
        assertEquals(BallSet.NETHER, items.setOf(nether), "setActive must not change which set the radar points at");

        items.setActive(nether, true);
        assertTrue(items.isShowingActive(nether));
        assertEquals(BallSet.NETHER, items.setOf(nether));
    }

    @Test
    void endAndOverworldNeverShareAnActiveModelString() {
        ItemStack overworld = items.create(BallSet.OVERWORLD);
        ItemStack end = items.create(BallSet.END);

        assertNotEquals(overworld.getItemMeta().getCustomModelDataComponent().getStrings(),
                end.getItemMeta().getCustomModelDataComponent().getStrings());
    }
}
