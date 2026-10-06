package dev.sirbepy.mc.dragonballs.item;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Balls are identified ONLY by the {@code dragonballs:star} PDC marker (never name/texture); this
 * covers that identification plus the stone-variant PDC flag and item text. Texture/profile
 * application itself isn't asserted on: MockBukkit has no real Mojang session, so a profile is
 * best-effort there (see BallItems.applyProfileTexture's null-safe guard) and the PDC marker is
 * what every other listener actually keys off of.
 */
class BallItemsTest {

    private ServerMock server;
    private BallItems items;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        YamlConfiguration config = new YamlConfiguration();
        config.set("textures.1", "deadbeef");
        config.set("textures.stone", "feedface");
        items = new BallItems(plugin, () -> new DragonBallsConfig(config));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createItemIsRecognizedAndCarriesItsStar() {
        ItemStack ball = items.createItem(4);
        assertTrue(items.isBall(ball));
        assertEquals(4, items.starOf(ball).orElseThrow());
        assertFalse(items.isStoneItem(ball), "a freshly created active item is never stone");
        assertEquals(Material.PLAYER_HEAD, ball.getType());
    }

    @Test
    void createStoneItemCarriesTheStoneFlagAndSameStar() {
        ItemStack stoneBall = items.createStoneItem(5);
        assertTrue(items.isBall(stoneBall));
        assertEquals(5, items.starOf(stoneBall).orElseThrow());
        assertTrue(items.isStoneItem(stoneBall));
    }

    @Test
    void itemForPicksTheRequestedVariant() {
        assertFalse(items.isStoneItem(items.itemFor(2, false)));
        assertTrue(items.isStoneItem(items.itemFor(2, true)));
    }

    @Test
    void ordinaryItemsAreNeverMistakenForBalls() {
        assertFalse(items.isBall(new ItemStack(Material.PLAYER_HEAD)));
        assertFalse(items.isBall(null));
        assertTrue(items.starOf(new ItemStack(Material.DIRT)).isEmpty());
    }

    @Test
    void identificationIsByPdcMarkerOnly() {
        // A plain head, even with the same display name text, is still not a ball: no PDC marker.
        ItemStack lookalike = new ItemStack(Material.PLAYER_HEAD);
        var meta = lookalike.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("4-Star Dragon Ball"));
        lookalike.setItemMeta(meta);
        assertFalse(items.isBall(lookalike));
    }
}
