package dev.sirbepy.mc.tweaks.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Item;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.TridentMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoidTridentListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock owner;
    private VoidTridentListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world_the_end");
        owner = server.addPlayer("SirBepy");
        owner.teleport(new Location(world, 0, 60, 0));
        Plugin plugin = MockBukkit.createMockPlugin("Tweaks");
        listener = new VoidTridentListener(server);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** TridentMock leaves getItemStack unimplemented; Paper returns a copy of the trident's item. */
    private static final class TestTrident extends TridentMock {
        private ItemStack item;

        TestTrident(ServerMock server) {
            super(server, UUID.randomUUID());
        }

        @Override
        public ItemStack getItemStack() {
            return item.clone();
        }
    }

    private static ItemStack porungasTrident(int loyalty) {
        ItemStack item = new ItemStack(Material.TRIDENT);
        item.editMeta(meta -> {
            meta.customName(Component.text("Porunga's Trident"));
            if (loyalty > 0) meta.addEnchant(Enchantment.LOYALTY, loyalty, true);
            meta.addEnchant(Enchantment.IMPALING, 5, true);
            ((Damageable) meta).setDamage(42);
        });
        return item;
    }

    private TestTrident trident(int loyalty, double y) {
        TestTrident trident = new TestTrident(server);
        trident.item = porungasTrident(loyalty);
        trident.setLoyaltyLevel(loyalty);
        trident.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        trident.setShooter(owner);
        trident.setLocation(new Location(world, 5, y, 5));
        trident.setVelocity(new Vector(0, -3, 0));
        return trident;
    }

    private TestTrident thrown(int loyalty, double y) {
        TestTrident trident = trident(loyalty, y);
        listener.onLaunch(new ProjectileLaunchEvent(trident));
        return trident;
    }

    private void voidRemoves(Trident trident) {
        server.getPluginManager().callEvent(new EntityRemoveEvent(trident, EntityRemoveEvent.Cause.OUT_OF_WORLD));
    }

    private int tridentsIn(PlayerMock player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.TRIDENT) count += stack.getAmount();
        }
        return count;
    }

    @Test
    void aLoyaltyTridentBelowTheWorldStartsFlyingHome() {
        TestTrident trident = thrown(3, world.getMinHeight() - 1);

        listener.tick();

        assertTrue(trident.hasDealtDamage(), "Loyalty only pulls a trident back once it has dealt damage");
        assertEquals(new Vector(), trident.getVelocity());
    }

    @Test
    void aLoyaltyTridentStillAboveTheWorldIsLeftAlone() {
        TestTrident trident = thrown(3, world.getMinHeight() + 1);

        listener.tick();

        assertFalse(trident.hasDealtDamage());
    }

    @Test
    void aTridentWithoutLoyaltyFallsIntoTheVoidLikeVanilla() {
        TestTrident trident = thrown(0, world.getMinHeight() - 1);

        listener.tick();
        voidRemoves(trident);

        assertFalse(trident.hasDealtDamage());
        assertEquals(0, tridentsIn(owner));
    }

    @Test
    void anOwnerInAnotherWorldIsNotRecalledTo() {
        owner.teleport(new Location(server.addSimpleWorld("world"), 0, 70, 0));
        TestTrident trident = thrown(3, world.getMinHeight() - 1);

        listener.tick();

        assertFalse(trident.hasDealtDamage(), "Loyalty can't reach an owner in another world");
    }

    @Test
    void aLoyaltyTridentTheVoidStillTakesGoesToItsOwnerWithAllItsData() {
        TestTrident trident = trident(3, world.getMinHeight() - 65);

        voidRemoves(trident);

        assertEquals(1, tridentsIn(owner));
        ItemStack back = owner.getInventory().getItem(owner.getInventory().first(Material.TRIDENT));
        assertTrue(back.isSimilar(porungasTrident(3)), "same name, enchantments and damage");
        assertEquals(42, ((Damageable) back.getItemMeta()).getDamage());
    }

    @Test
    void otherRemovalsGiveNothingBack() {
        TestTrident trident = trident(3, 70);

        server.getPluginManager().callEvent(new EntityRemoveEvent(trident, EntityRemoveEvent.Cause.PICKUP));
        server.getPluginManager().callEvent(new EntityRemoveEvent(trident, EntityRemoveEvent.Cause.DESPAWN));

        assertEquals(0, tridentsIn(owner));
    }

    @Test
    void aCreativeThrowIsNotReturnedBecauseTheInventoryKeptIt() {
        TestTrident trident = trident(3, world.getMinHeight() - 65);
        trident.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);

        voidRemoves(trident);

        assertEquals(0, tridentsIn(owner));
    }

    @Test
    void aFullInventoryDropsTheTridentAtTheOwnersFeet() {
        // MockBukkit's addItem also fills the off hand, so every slot is filled, not just storage.
        for (int slot = 0; slot < owner.getInventory().getSize(); slot++) {
            owner.getInventory().setItem(slot, new ItemStack(Material.DIRT, 64));
        }

        voidRemoves(trident(3, world.getMinHeight() - 65));

        assertEquals(1, world.getEntitiesByClass(Item.class).stream()
                .filter(item -> item.getItemStack().isSimilar(porungasTrident(3))).count());
    }

    @Test
    void anOfflineOwnerGetsTheTridentWhenTheyJoin() {
        TestTrident trident = trident(3, world.getMinHeight() - 65);
        owner.disconnect();

        voidRemoves(trident);
        PlayerMock back = new PlayerMock(server, "SirBepy", owner.getUniqueId());
        server.addPlayer(back);

        assertEquals(1, tridentsIn(back));
    }
}
