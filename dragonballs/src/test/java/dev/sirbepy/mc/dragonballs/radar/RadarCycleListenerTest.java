package dev.sirbepy.mc.dragonballs.radar;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RadarCycleListenerTest {

    private WorldMock world;
    private BallRegistry registry;
    private RadarItems items;
    private RadarTracking tracking;
    private RadarCycleListener listener;
    private PlayerMock player;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        server.addSimpleWorld("world_nether");
        PluginMock plugin = MockBukkit.createMockPlugin();
        BallItems ballItems = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        registry = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), ballItems, BallSet.OVERWORLD);
        BallRegistry nether = new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), ballItems,
                BallSet.NETHER);
        BallRegistry end = new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), ballItems,
                BallSet.END);
        BallSets sets = new BallSets(registry, nether, end, ballItems, () -> new DragonBallsConfig(new YamlConfiguration()));
        items = new RadarItems(plugin);
        tracking = new RadarTracking();
        RadarTask task = new RadarTask(sets, items, tracking, () -> new DragonBallsConfig(new YamlConfiguration()));
        listener = new RadarCycleListener(items, tracking, task);

        player = server.addPlayer();
        player.teleport(new Location(world, 0, 64, 0));
        registry.markPlaced(2, new Location(world, 100, 64, 0), false);
        registry.markPlaced(5, new Location(world, 600, 64, 0), false);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void sneakRightClickInTheAirPinsTheNearestThenStepsOutward() {
        player.getInventory().setItemInMainHand(items.create());
        player.setSneaking(true);

        click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, null);
        assertEquals(2, tracking.picked(player.getUniqueId(), BallSet.OVERWORLD));
        assertEquals("Default Radar: 2-star, Close", name(player.getInventory().getItemInMainHand()),
                "the radar shows the new pick straight away, not on the next 1 s pass");

        click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, null);
        assertEquals(5, tracking.picked(player.getUniqueId(), BallSet.OVERWORLD));
        assertEquals(600, player.getCompassTarget().getBlockX());

        click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, null);
        assertNull(tracking.picked(player.getUniqueId(), BallSet.OVERWORLD));
    }

    @Test
    void aPlainRightClickWithoutSneakingChangesNothing() {
        player.getInventory().setItemInMainHand(items.create());

        click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, null);

        assertNull(tracking.picked(player.getUniqueId(), BallSet.OVERWORLD));
    }

    @Test
    void aPlainCompassNeverCycles() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.COMPASS));
        player.setSneaking(true);

        click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, null);

        assertNull(tracking.picked(player.getUniqueId(), BallSet.OVERWORLD));
    }

    @Test
    void anOffHandRadarStepsOnceWhenTheMainHandIsEmpty() {
        player.getInventory().setItemInOffHand(items.create());
        player.setSneaking(true);

        click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, null);
        click(Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND, null);

        assertEquals(2, tracking.picked(player.getUniqueId(), BallSet.OVERWORLD));
    }

    @Test
    void radarsInBothHandsStepOncePerClick() {
        player.getInventory().setItemInMainHand(items.create());
        player.getInventory().setItemInOffHand(items.create());
        player.setSneaking(true);

        click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, null);
        click(Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND, null);

        assertEquals(2, tracking.picked(player.getUniqueId(), BallSet.OVERWORLD));
    }

    @Test
    void sneakClickingASignNeverCyclesSoTheRadarShopStillSells() {
        player.getInventory().setItemInMainHand(items.create());
        player.setSneaking(true);
        Block sign = world.getBlockAt(3, 64, 3);
        sign.setType(Material.OAK_SIGN);

        click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, sign);

        assertNull(tracking.picked(player.getUniqueId(), BallSet.OVERWORLD));
    }

    @Test
    void sneakClickingALodestoneCyclesWithoutBindingTheRadar() {
        player.getInventory().setItemInMainHand(items.create());
        player.setSneaking(true);
        Block lodestone = world.getBlockAt(3, 64, 3);
        lodestone.setType(Material.LODESTONE);

        PlayerInteractEvent event = click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, lodestone);

        assertEquals(Event.Result.DENY, event.useItemInHand());
        assertEquals(2, tracking.picked(player.getUniqueId(), BallSet.OVERWORLD));
    }

    private PlayerInteractEvent click(Action action, EquipmentSlot hand, Block block) {
        ItemStack item = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        PlayerInteractEvent event = new PlayerInteractEvent(player, action, item, block,
                block == null ? BlockFace.SELF : BlockFace.UP, hand);
        listener.onInteract(event);
        return event;
    }

    private static String name(ItemStack item) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
    }
}
