package dev.sirbepy.mc.tweaks.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Item;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.DrownedMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.TridentMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoyaltyTridentListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock owner;
    private LoyaltyTridentListener listener;
    private Plugin plugin;
    private File pendingFile;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world_the_end");
        owner = server.addPlayer("SirBepy");
        owner.teleport(new Location(world, 0, 60, 0));
        plugin = MockBukkit.createMockPlugin("Tweaks");
        pendingFile = dataFolder.resolve("loyalty-tridents.yml").toFile();
        listener = newListener();
    }

    /** A fresh listener on the same file, as after a restart. */
    private LoyaltyTridentListener newListener() {
        LoyaltyTridentListener fresh = new LoyaltyTridentListener(server, new PendingTridents(pendingFile, server.getLogger()));
        server.getPluginManager().registerEvents(fresh, plugin);
        return fresh;
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** TridentMock leaves getItemStack and isTicking unimplemented; Paper returns a copy of the trident's item. */
    private static final class TestTrident extends TridentMock {
        private ItemStack item;
        private boolean ticking = true;

        TestTrident(ServerMock server) {
            super(server, UUID.randomUUID());
        }

        @Override
        public ItemStack getItemStack() {
            return item.clone();
        }

        @Override
        public boolean isTicking() {
            return ticking;
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
        listener.onAdd(new EntityAddToWorldEvent(trident, world));
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

    private TestTrident lodged(boolean ticking) {
        TestTrident trident = thrown(3, 70);
        trident.setVelocity(new Vector());
        trident.ticking = ticking;
        return trident;
    }

    private void tick(int times) {
        for (int i = 0; i < times; i++) listener.tick();
    }

    @Test
    void aStrayOutOfTickingRangeGoesHomeOnceTheGraceIsOver() {
        TestTrident trident = lodged(false);

        tick(LoyaltyTridentListener.GRACE_TICKS - 1);
        assertTrue(trident.isValid(), "vanilla gets the first few seconds");
        assertEquals(0, tridentsIn(owner));

        tick(1);
        assertFalse(trident.isValid(), "the entity is gone in the same tick the item is handed over");
        assertEquals(1, tridentsIn(owner));
        ItemStack back = owner.getInventory().getItem(owner.getInventory().first(Material.TRIDENT));
        assertEquals(42, ((Damageable) back.getItemMeta()).getDamage());
    }

    @Test
    void aTickingTridentWithItsOwnerInTheSameWorldIsLeftToVanilla() {
        TestTrident trident = lodged(true);

        tick(LoyaltyTridentListener.GRACE_TICKS * 3);

        assertTrue(trident.isValid());
        assertEquals(0, tridentsIn(owner));
    }

    @Test
    void anOwnerInAnotherWorldGetsTheStrayAfterTheGrace() {
        TestTrident trident = lodged(true);
        owner.teleport(new Location(server.addSimpleWorld("world"), 0, 70, 0));

        tick(LoyaltyTridentListener.GRACE_TICKS);

        assertFalse(trident.isValid());
        assertEquals(1, tridentsIn(owner));
    }

    @Test
    void aTridentAboutToUnloadGoesHomeAtOnce() {
        TestTrident trident = lodged(true);

        server.getPluginManager().callEvent(new EntitiesUnloadEvent(world.getChunkAt(0, 0), List.of(trident)));

        assertFalse(trident.isValid(), "removed before the chunk saves, or it would load again later as a second trident");
        assertEquals(1, tridentsIn(owner));
    }

    @Test
    void anOfflineOwnerGetsTheStrayOnJoinEvenAfterARestart() {
        TestTrident trident = lodged(false);
        owner.disconnect();

        tick(LoyaltyTridentListener.GRACE_TICKS);
        assertFalse(trident.isValid());
        HandlerList.unregisterAll(listener);
        listener = newListener();
        PlayerMock back = new PlayerMock(server, "SirBepy", owner.getUniqueId());
        server.addPlayer(back);

        assertEquals(1, tridentsIn(back));
    }

    @Test
    void aDeadOwnersTridentsWaitForTheRespawn() {
        TestTrident trident = lodged(true);

        owner.setHealth(0);
        assertFalse(trident.isValid(), "taken before vanilla drops it where it lies");
        assertEquals(0, tridentsIn(owner));

        owner.respawn();
        server.getPluginManager().callEvent(new PlayerPostRespawnEvent(owner, owner.getLocation(), false, false, false,
                PlayerRespawnEvent.RespawnReason.DEATH));
        assertEquals(1, tridentsIn(owner));
    }

    @Test
    void aDrownedsTridentIsNeverHandedOver() {
        TestTrident trident = lodged(false);
        trident.setShooter(new DrownedMock(server, UUID.randomUUID()));

        tick(LoyaltyTridentListener.GRACE_TICKS * 2);

        assertTrue(trident.isValid());
        assertFalse(pendingFile.exists(), "nothing owed to a mob");
    }

    @Test
    void aCreativeStrayIsLeftWhereItIs() {
        TestTrident trident = lodged(false);
        trident.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);

        tick(LoyaltyTridentListener.GRACE_TICKS * 2);

        assertTrue(trident.isValid());
        assertEquals(0, tridentsIn(owner));
    }

    @Test
    void aTridentSomeoneElsePickedUpIsTheirs() {
        TestTrident trident = lodged(false);
        trident.remove();

        tick(LoyaltyTridentListener.GRACE_TICKS * 2);

        assertEquals(0, tridentsIn(owner));
    }

    @Test
    void shutdownHandsOverEveryTridentStillOut() {
        TestTrident flying = lodged(true);
        TestTrident falling = thrown(3, 70);

        listener.returnAll();

        assertFalse(flying.isValid());
        assertFalse(falling.isValid());
        assertEquals(2, tridentsIn(owner));
    }
}
