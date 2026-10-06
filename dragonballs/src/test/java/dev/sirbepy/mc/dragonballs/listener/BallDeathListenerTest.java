package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.registry.BallState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A dying player's balls never land in the normal death-drop pile (so a graveyard/no-drop plugin,
 * or the vanilla "keep inventory" gamerule's own drop suppression, can't accidentally eat one):
 * they're pulled out of {@code event.getDrops()} and dropped separately instead, routed to each
 * ball's own set's registry.
 */
class BallDeathListenerTest {

    private ServerMock server;
    private WorldMock world;
    private BallSets sets;
    private BallRegistry overworld;
    private BallItems items;
    private BallDeathListener listener;
    private PlayerMock player;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        PluginMock plugin = MockBukkit.createMockPlugin();
        items = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        overworld = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items, BallSet.OVERWORLD);
        BallRegistry nether = new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), items,
                BallSet.NETHER);
        BallRegistry end = new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), items, BallSet.END);
        sets = new BallSets(overworld, nether, end, items, () -> new DragonBallsConfig(new YamlConfiguration()));
        listener = new BallDeathListener(sets);
        player = server.addPlayer();
        player.teleport(new Location(world, 50, 64, 50));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerDeathEvent death(List<ItemStack> drops) {
        return new PlayerDeathEvent(player, DamageSource.builder(DamageType.GENERIC).build(), drops, 0,
                net.kyori.adventure.text.Component.empty(), false);
    }

    @Test
    void ballsAreRemovedFromTheNormalDropsList() {
        overworld.markHeld(1, player, false);
        List<ItemStack> drops = new ArrayList<>(List.of(overworld.createItem(1), new ItemStack(Material.DIRT)));

        PlayerDeathEvent event = death(drops);
        listener.onDeath(event);

        assertEquals(1, event.getDrops().size(), "only the non-ball drop should remain");
        assertEquals(Material.DIRT, event.getDrops().get(0).getType());
    }

    @Test
    void theRemovedBallBecomesItsOwnDroppedItemAtTheDeathLocation() {
        overworld.markHeld(2, player, true);
        List<ItemStack> drops = new ArrayList<>(List.of(items.createStoneItem(BallSet.OVERWORLD, 2)));

        listener.onDeath(death(drops));

        var balls = world.getEntitiesByClass(org.bukkit.entity.Item.class).stream()
                .filter(item -> items.isBall(item.getItemStack()))
                .toList();
        assertEquals(1, balls.size(), "the ball must reappear as its own item entity");
        assertEquals(BallState.DROPPED, overworld.get(2).orElseThrow().state());
        assertTrue(overworld.isDroppedAs(2, balls.get(0).getUniqueId()));
    }

    @Test
    void aNetherBallDiesIntoTheNetherRegistryNotTheOverworldOne() {
        BallRegistry nether = sets.registry(BallSet.NETHER);
        nether.markHeld(3, player, false);
        List<ItemStack> drops = new ArrayList<>(List.of(items.createItem(BallSet.NETHER, 3)));

        listener.onDeath(death(drops));

        assertEquals(BallState.DROPPED, nether.get(3).orElseThrow().state());
        assertEquals(BallState.PLACED, overworld.get(3).orElseThrow().state(), "the overworld star 3 must be untouched");
    }

    @Test
    void aDeathWithNoBallsLeavesDropsAlone() {
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.DIRT), new ItemStack(Material.STONE)));
        PlayerDeathEvent event = death(drops);

        listener.onDeath(event);

        assertEquals(2, event.getDrops().size());
        assertFalse(overworld.getBalls().stream().anyMatch(b -> b.state() == BallState.DROPPED));
    }
}
