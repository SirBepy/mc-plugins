package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
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
 * they're pulled out of {@code event.getDrops()} and dropped separately instead.
 */
class BallDeathListenerTest {

    private ServerMock server;
    private WorldMock world;
    private BallRegistry registry;
    private BallItems items;
    private BallDeathListener listener;
    private PlayerMock player;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        PluginMock plugin = MockBukkit.createMockPlugin();
        items = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        registry = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items);
        listener = new BallDeathListener(registry);
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
        registry.markHeld(1, player, false);
        List<ItemStack> drops = new ArrayList<>(List.of(registry.createItem(1), new ItemStack(Material.DIRT)));

        PlayerDeathEvent event = death(drops);
        listener.onDeath(event);

        assertEquals(1, event.getDrops().size(), "only the non-ball drop should remain");
        assertEquals(Material.DIRT, event.getDrops().get(0).getType());
    }

    @Test
    void theRemovedBallBecomesItsOwnDroppedItemAtTheDeathLocation() {
        registry.markHeld(2, player, true);
        List<ItemStack> drops = new ArrayList<>(List.of(registry.items().createStoneItem(2)));

        listener.onDeath(death(drops));

        var balls = world.getEntitiesByClass(org.bukkit.entity.Item.class).stream()
                .filter(item -> items.isBall(item.getItemStack()))
                .toList();
        assertEquals(1, balls.size(), "the ball must reappear as its own item entity");
        assertEquals(BallState.DROPPED, registry.get(2).orElseThrow().state());
        assertTrue(registry.isDroppedAs(2, balls.get(0).getUniqueId()));
    }

    @Test
    void aDeathWithNoBallsLeavesDropsAlone() {
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.DIRT), new ItemStack(Material.STONE)));
        PlayerDeathEvent event = death(drops);

        listener.onDeath(event);

        assertEquals(2, event.getDrops().size());
        assertFalse(registry.getBalls().stream().anyMatch(b -> b.state() == BallState.DROPPED));
    }
}
