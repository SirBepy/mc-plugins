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
    void showSwapsTheScreenAndNameButNeverThePdc() {
        ItemStack radar = items.create();

        assertTrue(items.show(radar, null));
        assertTrue(items.isShowingIdle(radar));
        assertFalse(items.isShowingActive(radar));
        assertTrue(items.isRadar(radar));
        assertEquals("Default Radar", plain(radar.getItemMeta().displayName()));

        assertTrue(items.show(radar, new RadarReading(4, RadarBand.CLOSE, false)));
        assertTrue(items.isShowingActive(radar));
        assertTrue(items.isShowingBand(radar, RadarBand.CLOSE));
        assertEquals(List.of("dragon_radar_close"), radar.getItemMeta().getCustomModelDataComponent().getStrings());
        assertEquals("Default Radar: 4-star (nearest), Close", plain(radar.getItemMeta().displayName()));
    }

    @Test
    void aPickedBallDropsTheNearestTag() {
        ItemStack radar = items.create();

        items.show(radar, new RadarReading(2, RadarBand.FAR, true));

        assertEquals("Default Radar: 2-star, Far", plain(radar.getItemMeta().displayName()));
    }

    @Test
    void showReportsNoChangeForTheSameReadingSoTheHandIsNotRewritten() {
        ItemStack radar = items.create();
        RadarReading reading = new RadarReading(1, RadarBand.HERE, false);

        assertTrue(items.show(radar, reading));
        assertFalse(items.show(radar, reading), "an unchanged reading must not rewrite the item");
        assertTrue(items.show(radar, new RadarReading(1, RadarBand.CLOSE, false)));
    }

    @Test
    void aRadarFromBeforeTheBandsGetsTheNewLoreOnItsFirstRefresh() {
        ItemStack old = items.create();
        ItemMeta meta = old.getItemMeta();
        meta.lore(List.of(net.kyori.adventure.text.Component.text("Never says how far.")));
        old.setItemMeta(meta);

        assertTrue(items.show(old, new RadarReading(3, RadarBand.WARM, false)));

        List<String> lore = old.getItemMeta().lore().stream().map(RadarItemsTest::plain).toList();
        assertFalse(lore.contains("Never says how far."));
        assertTrue(lore.contains("Sneak + right-click: track the next ball."));
    }

    @Test
    void everyBandOfEverySetHasItsOwnModelString() {
        java.util.Set<String> models = new java.util.HashSet<>();
        for (BallSet set : BallSet.values()) {
            for (RadarBand band : RadarBand.values()) {
                models.add(RadarItems.activeModel(set, band));
            }
        }
        assertEquals(BallSet.values().length * RadarBand.values().length, models.size());
        assertTrue(models.contains("dragon_radar"), "the warm band keeps the original model so old radars still render");
        assertTrue(models.contains("dragon_radar_nether_far"));
        assertTrue(models.contains("dragon_radar_end_here"));
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
    void showKeepsWhichSetTheRadarPointsAt() {
        ItemStack nether = items.create(BallSet.NETHER);
        assertTrue(items.isShowingActive(nether));

        items.show(nether, null);
        assertTrue(items.isShowingIdle(nether));
        assertFalse(items.isShowingActive(nether));
        assertEquals(BallSet.NETHER, items.setOf(nether), "show must not change which set the radar points at");

        items.show(nether, new RadarReading(5, RadarBand.FAR, false));
        assertTrue(items.isShowingActive(nether));
        assertEquals(List.of("dragon_radar_nether_far"), nether.getItemMeta().getCustomModelDataComponent().getStrings());
        assertEquals("Nether Dragon Radar: 5-star (nearest), Far", plain(nether.getItemMeta().displayName()));
        assertEquals(BallSet.NETHER, items.setOf(nether));
    }

    @Test
    void endAndOverworldNeverShareAnActiveModelString() {
        ItemStack overworld = items.create(BallSet.OVERWORLD);
        ItemStack end = items.create(BallSet.END);

        assertNotEquals(overworld.getItemMeta().getCustomModelDataComponent().getStrings(),
                end.getItemMeta().getCustomModelDataComponent().getStrings());
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }
}
