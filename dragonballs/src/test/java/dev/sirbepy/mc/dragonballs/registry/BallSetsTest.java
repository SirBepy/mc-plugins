package dev.sirbepy.mc.dragonballs.registry;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The container that routes a bare ball item/block to its owning set's registry, and gates the End
 * set behind {@code Bukkit.getAllowEnd()} + its world being loaded.
 */
class BallSetsTest {

    private ServerMock server;
    private WorldMock world;
    private BallItems items;
    private BallSets sets;
    private BallRegistry overworld;
    private BallRegistry nether;
    private BallRegistry end;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        PluginMock plugin = MockBukkit.createMockPlugin();
        items = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        overworld = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items, BallSet.OVERWORLD);
        nether = new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), items, BallSet.NETHER);
        end = new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), items, BallSet.END);
        sets = new BallSets(overworld, nether, end, items, () -> new DragonBallsConfig(new YamlConfiguration()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registryLooksUpEachOfThe3SetsByEnumValue() {
        assertEquals(overworld, sets.registry(BallSet.OVERWORLD));
        assertEquals(nether, sets.registry(BallSet.NETHER));
        assertEquals(end, sets.registry(BallSet.END));
    }

    @Test
    void allReturnsAll3InDeclarationOrder() {
        assertEquals(java.util.List.of(overworld, nether, end), sets.all());
    }

    @Test
    void registryForAnItemFindsTheRegistryOwningItsSet() {
        ItemStack netherBall = items.createItem(BallSet.NETHER, 4);
        assertEquals(nether, sets.registryFor(netherBall).orElseThrow());

        ItemStack overworldBall = items.createItem(BallSet.OVERWORLD, 4);
        assertEquals(overworld, sets.registryFor(overworldBall).orElseThrow());
    }

    @Test
    void registryForANonBallItemIsEmpty() {
        assertTrue(sets.registryFor(new ItemStack(Material.DIRT)).isEmpty());
    }

    @Test
    void overworldAndNetherAreAlwaysEnabled() {
        assertTrue(sets.isEnabled(BallSet.OVERWORLD));
        assertTrue(sets.isEnabled(BallSet.NETHER));
    }

    @Test
    void endIsDisabledWhenAllowEndIsFalseEvenIfItsWorldIsLoaded() {
        server.addSimpleWorld("world_the_end");
        server.setAllowEnd(false);

        assertFalse(sets.isEnabled(BallSet.END));
    }

    @Test
    void endIsDisabledWhenAllowEndIsTrueButItsWorldIsntLoaded() {
        server.setAllowEnd(true);

        assertFalse(sets.isEnabled(BallSet.END));
    }

    @Test
    void endIsEnabledOnlyWhenAllowEndIsTrueAndItsWorldIsLoaded() {
        server.addSimpleWorld("world_the_end");
        server.setAllowEnd(true);

        assertTrue(sets.isEnabled(BallSet.END));
    }
}
