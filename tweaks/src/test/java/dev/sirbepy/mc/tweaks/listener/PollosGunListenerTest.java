package dev.sirbepy.mc.tweaks.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PollosGunListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock shooter;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        shooter = server.addPlayer();
        shooter.teleport(new Location(world, 0, 64, 0));
        server.getPluginManager().registerEvents(new PollosGunListener(MockBukkit.createMockPlugin()), MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemStack crossbow(String... models) {
        ItemStack item = new ItemStack(Material.CROSSBOW);
        if (models.length > 0) {
            ItemMeta meta = item.getItemMeta();
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of(models));
            meta.setCustomModelDataComponent(cmd);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void shoot(ItemStack bow) {
        server.getPluginManager().callEvent(
                new EntityShootBowEvent(shooter, bow, new ItemStack(Material.ARROW), shooter, EquipmentSlot.HAND, 1.0f, true));
    }

    private static long shots(PlayerMock player) {
        return player.getHeardSounds().stream().filter(s -> s.getSound().equals(PollosGunListener.SHOT_SOUND)).count();
    }

    @Test
    void onlyThePollosCrossbowIsTheGun() {
        assertTrue(PollosGunListener.isPollosGun(crossbow("pollos_gun")));
        assertFalse(PollosGunListener.isPollosGun(crossbow()));
        assertFalse(PollosGunListener.isPollosGun(crossbow("something_else")));
        assertFalse(PollosGunListener.isPollosGun(null));

        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of("pollos_gun"));
        meta.setCustomModelDataComponent(cmd);
        bow.setItemMeta(meta);
        assertFalse(PollosGunListener.isPollosGun(bow));
    }

    @Test
    void pollosShotIsHeardWithin48BlocksAtVolume3() {
        PlayerMock near = server.addPlayer();
        near.teleport(new Location(world, 40, 64, 0));
        PlayerMock far = server.addPlayer();
        far.teleport(new Location(world, 60, 64, 0));

        shoot(crossbow("pollos_gun"));

        assertEquals(1, shots(shooter));
        assertEquals(3.0f, shooter.getHeardSounds().getFirst().getVolume());
        assertEquals(1, shots(near));
        assertEquals(0, shots(far));
    }

    @Test
    void plainCrossbowStaysSilent() {
        shoot(crossbow());

        assertEquals(0, shots(shooter));
    }

    @Test
    void multishotBoltsGiveOneBangPerTick() {
        ItemStack gun = crossbow("pollos_gun");
        shoot(gun);
        shoot(gun);
        shoot(gun);
        assertEquals(1, shots(shooter));

        server.getScheduler().performOneTick();
        shoot(gun);
        assertEquals(2, shots(shooter));
    }
}
