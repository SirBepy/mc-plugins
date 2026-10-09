package dev.sirbepy.mc.merfolk;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.EntityMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock logiks;
    private PlayerMock stranger;
    private final List<Villager> adopted = new ArrayList<>();
    private boolean otherTown;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        logiks = server.addPlayer("ItsLogiks");
        stranger = server.addPlayer("Someone");
        TownCheck towns = new TownCheck() {
            @Override
            public boolean inOtherTown(Player player, Location location) {
                return otherTown;
            }

            @Override
            public boolean mayBuild(Player player, Location location) {
                return true;
            }

            @Override
            public boolean mayDestroy(Player player, Location location) {
                return true;
            }
        };
        server.getPluginManager().registerEvents(new Conversion(logiks::getUniqueId, towns, adopted::add),
                MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Villager villager(boolean inWater) {
        Villager villager = world.spawn(new Location(world, 0, 64, 0), Villager.class);
        ((EntityMock) villager).setInWater(inWater);
        return villager;
    }

    private boolean click(PlayerMock player, Villager villager) {
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, villager, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    @Test
    void theWisherTurnsAVillagerInWaterIntoAMerfolkForOneShell() {
        logiks.getInventory().setItemInMainHand(new ItemStack(Material.NAUTILUS_SHELL, 3));
        Villager villager = villager(true);

        assertTrue(click(logiks, villager), "the trade screen must not open on the ritual click");

        assertTrue(Merfolk.is(villager));
        assertEquals(Villager.Type.SWAMP, villager.getVillagerType());
        assertTrue(Merfolk.isHead(villager.getEquipment().getHelmet()));
        assertEquals(0f, villager.getEquipment().getHelmetDropChance());
        assertEquals(2, logiks.getInventory().getItemInMainHand().getAmount());
        assertEquals(List.of(villager), adopted);
    }

    @Test
    void anyoneElseGetsNothingAndCanStillTrade() {
        stranger.getInventory().setItemInMainHand(new ItemStack(Material.NAUTILUS_SHELL));
        Villager villager = villager(true);

        assertFalse(click(stranger, villager));

        assertFalse(Merfolk.is(villager));
        assertEquals(1, stranger.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void theVillagerMustStandInWater() {
        logiks.getInventory().setItemInMainHand(new ItemStack(Material.NAUTILUS_SHELL));
        Villager villager = villager(false);

        click(logiks, villager);

        assertFalse(Merfolk.is(villager));
        assertEquals(1, logiks.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void babiesCannotAnswerTheSea() {
        logiks.getInventory().setItemInMainHand(new ItemStack(Material.NAUTILUS_SHELL));
        Villager villager = villager(true);
        villager.setBaby();

        click(logiks, villager);

        assertFalse(Merfolk.is(villager));
    }

    @Test
    void notInsideAnotherPlayersTown() {
        logiks.getInventory().setItemInMainHand(new ItemStack(Material.NAUTILUS_SHELL));
        Villager villager = villager(true);
        otherTown = true;

        click(logiks, villager);

        assertFalse(Merfolk.is(villager));
        assertEquals(1, logiks.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void withoutAShellNothingHappens() {
        logiks.getInventory().setItemInMainHand(new ItemStack(Material.EMERALD));
        Villager villager = villager(true);

        assertFalse(click(logiks, villager));

        assertFalse(Merfolk.is(villager));
    }
}
