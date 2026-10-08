package dev.sirbepy.mc.dragonballs.item;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Skull;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Balls are identified ONLY by the {@code dragonballs:star} PDC marker (never name/texture); this
 * covers that identification plus the stone-variant PDC flag, the per-set {@code dragonballs:set}
 * marker (and its "no set = overworld" legacy default), and item text. The shared
 * {@code dev.sirbepy.mc.core.item.Heads#texturedProfile} helper that both ball heads and the
 * station decor heads use is covered by core's own test.
 */
class BallItemsTest {

    private ServerMock server;
    private WorldMock world;
    private BallItems items;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        PluginMock plugin = MockBukkit.createMockPlugin();
        YamlConfiguration config = new YamlConfiguration();
        config.set("textures.overworld.1", "deadbeef");
        config.set("textures.nether.1", "feedc0de");
        config.set("textures.end.1", "0ddba11");
        config.set("textures.stone", "feedface");
        items = new BallItems(plugin, () -> new DragonBallsConfig(config));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createItemIsRecognizedAndCarriesItsStar() {
        ItemStack ball = items.createItem(BallSet.OVERWORLD, 4);
        assertTrue(items.isBall(ball));
        assertEquals(4, items.starOf(ball).orElseThrow());
        assertFalse(items.isStoneItem(ball), "a freshly created active item is never stone");
        assertEquals(Material.PLAYER_HEAD, ball.getType());
    }

    @Test
    void createItemCarriesItsSetAndDefaultsToOverworld() {
        ItemStack overworld = items.createItem(BallSet.OVERWORLD, 1);
        ItemStack nether = items.createItem(BallSet.NETHER, 1);
        ItemStack end = items.createItem(BallSet.END, 1);

        assertEquals(BallSet.OVERWORLD, items.setOf(overworld));
        assertEquals(BallSet.NETHER, items.setOf(nether));
        assertEquals(BallSet.END, items.setOf(end));
    }

    @Test
    void anItemWithNoSetPdcEntryIsOverworld() {
        // Simulates a ball item from before the 3-set split: star is set, set is not.
        ItemStack legacy = new ItemStack(Material.PLAYER_HEAD);
        var meta = legacy.getItemMeta();
        meta.getPersistentDataContainer().set(items.starKey(), org.bukkit.persistence.PersistentDataType.INTEGER, 3);
        legacy.setItemMeta(meta);

        assertTrue(items.isBall(legacy));
        assertEquals(BallSet.OVERWORLD, items.setOf(legacy));
    }

    @Test
    void nonOverworldSetsGetDistinctProfileIdsFromOverworldAndEachOther() {
        // Star 1 is the only one configured with a texture for all 3 sets in setUp(); a profile is
        // only ever attached when the texture is non-blank, so the other call sites use it too.
        ItemStack overworld = items.createItem(BallSet.OVERWORLD, 1);
        ItemStack nether = items.createItem(BallSet.NETHER, 1);
        ItemStack end = items.createItem(BallSet.END, 1);

        UUID overworldId = profileId(overworld);
        UUID netherId = profileId(nether);
        UUID endId = profileId(end);

        assertNotEquals(overworldId, netherId);
        assertNotEquals(overworldId, endId);
        assertNotEquals(netherId, endId);
    }

    @Test
    void nonOverworldProfileIdsAreStableAcrossInstances() {
        ItemStack first = items.createItem(BallSet.NETHER, 1);
        ItemStack second = items.createItem(BallSet.NETHER, 1);
        assertEquals(profileId(first), profileId(second));
    }

    private static UUID profileId(ItemStack item) {
        return ((org.bukkit.inventory.meta.SkullMeta) item.getItemMeta()).getPlayerProfile().getId();
    }

    @Test
    void overworldItemNameStaysByteIdenticalToBeforeTheSplit() {
        ItemStack ball = items.createItem(BallSet.OVERWORLD, 4);
        assertEquals("4-Star Dragon Ball",
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(ball.getItemMeta().displayName()));
    }

    @Test
    void netherAndEndItemNamesInsertTheSetWord() {
        ItemStack nether = items.createItem(BallSet.NETHER, 4);
        ItemStack end = items.createItem(BallSet.END, 4);
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        assertEquals("4-Star Nether Dragon Ball", plain.serialize(nether.getItemMeta().displayName()));
        assertEquals("4-Star End Dragon Ball", plain.serialize(end.getItemMeta().displayName()));
    }

    @Test
    void createStoneItemCarriesTheStoneFlagAndSameStar() {
        ItemStack stoneBall = items.createStoneItem(BallSet.OVERWORLD, 5);
        assertTrue(items.isBall(stoneBall));
        assertEquals(5, items.starOf(stoneBall).orElseThrow());
        assertTrue(items.isStoneItem(stoneBall));
    }

    @Test
    void itemForPicksTheRequestedVariant() {
        assertFalse(items.isStoneItem(items.itemFor(BallSet.OVERWORLD, 2, false)));
        assertTrue(items.isStoneItem(items.itemFor(BallSet.OVERWORLD, 2, true)));
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

    @Test
    void aLegacyBallHeadBlockIsRecognisedByItsStarMarker() {
        Location loc = new Location(world, 10, 65, 10);
        loc.getBlock().setType(Material.PLAYER_HEAD);
        Skull skull = (Skull) loc.getBlock().getState();
        assertFalse(items.isBallBlock(skull), "a plain head block is not a ball");

        skull.getPersistentDataContainer().set(items.starKey(), org.bukkit.persistence.PersistentDataType.INTEGER, 6);

        assertTrue(items.isBallBlock(skull));
    }

    @Test
    void everyBallItemCarriesItsOwnThreeDLook() {
        ItemStack nether = items.createItem(BallSet.NETHER, 4);
        ItemStack stone = items.createStoneItem(BallSet.END, 2);

        assertEquals(java.util.List.of("dragonball_nether_4"), nether.getItemMeta().getCustomModelDataComponent().getStrings());
        assertEquals(java.util.List.of("dragonball_stone"), stone.getItemMeta().getCustomModelDataComponent().getStrings());
        assertTrue(items.hasCurrentLook(nether));
        assertTrue(items.hasCurrentLook(stone));
    }

    @Test
    void aBallMadeBeforeTheThreeDLookIsFlaggedForRemaking() {
        ItemStack old = items.createItem(BallSet.OVERWORLD, 3);
        var meta = old.getItemMeta();
        var look = meta.getCustomModelDataComponent();
        look.setStrings(java.util.List.of());
        meta.setCustomModelDataComponent(look);
        old.setItemMeta(meta);

        assertFalse(items.hasCurrentLook(old));
        assertTrue(items.hasCurrentLook(new ItemStack(Material.DIRT)), "only balls are ever remade");
    }
}
