package dev.sirbepy.mc.carry.listener;

import dev.sirbepy.mc.carry.CarryConfig;
import dev.sirbepy.mc.carry.CarryManager;
import dev.sirbepy.mc.carry.CarryTestSupport;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
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

class CarryListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private CarryTestSupport.FakeGuard guard;
    private CarryManager carry;
    private CarryListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        // plugin.yml isn't loaded here, so its default-true permission is registered by hand.
        server.getPluginManager().addPermission(new Permission("carry.use", PermissionDefault.TRUE));
        player = server.addPlayer("Carrier");
        player.teleport(new Location(world, 0, 64, 0));
        guard = new CarryTestSupport.FakeGuard();
        CarryConfig config = CarryTestSupport.shippedConfig();
        carry = new CarryManager(() -> config, List.of(guard));
        listener = new CarryListener(carry, () -> config);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Cow cow() {
        return world.spawn(new Location(world, 2, 64, 0), Cow.class);
    }

    private PlayerInteractEntityEvent rightClick(PlayerMock who, org.bukkit.entity.Entity target, EquipmentSlot hand) {
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(who, target, hand);
        listener.onInteractEntity(event);
        return event;
    }

    private Cow carriedCow() {
        Cow cow = cow();
        player.setSneaking(true);
        rightClick(player, cow, EquipmentSlot.HAND);
        player.setSneaking(false);
        assertTrue(carry.isCarrying(player));
        return cow;
    }

    @Test
    void sneakRightClickWithAnEmptyHandPicksUpAndCancelsTheInteraction() {
        Cow cow = cow();
        player.setSneaking(true);

        PlayerInteractEntityEvent event = rightClick(player, cow, EquipmentSlot.HAND);

        assertTrue(event.isCancelled());
        assertTrue(carry.isCarried(cow));
    }

    @Test
    void rightClickWithoutSneakingIsLeftAlone() {
        Cow cow = cow();

        PlayerInteractEntityEvent event = rightClick(player, cow, EquipmentSlot.HAND);

        assertFalse(event.isCancelled());
        assertFalse(carry.isCarried(cow));
    }

    @Test
    void holdingAnItemIsLeftAloneSoVanillaUsesStillWork() {
        Cow cow = cow();
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));

        PlayerInteractEntityEvent event = rightClick(player, cow, EquipmentSlot.HAND);

        assertFalse(event.isCancelled());
        assertFalse(carry.isCarried(cow));
    }

    @Test
    void nonCarryableMobsAreLeftAlone() {
        Zombie zombie = world.spawn(new Location(world, 2, 64, 0), Zombie.class);
        player.setSneaking(true);

        assertFalse(rightClick(player, zombie, EquipmentSlot.HAND).isCancelled());
    }

    @Test
    void aRefusedPickUpStillCancelsTheInteraction() {
        guard.pickUpDenial = "denied-claim";
        Cow cow = cow();
        player.setSneaking(true);

        assertTrue(rightClick(player, cow, EquipmentSlot.HAND).isCancelled());
        assertFalse(carry.isCarried(cow));
    }

    @Test
    void nobodyCanInteractWithACarriedMob() {
        Cow cow = carriedCow();
        PlayerMock other = server.addPlayer("Other");

        assertTrue(rightClick(other, cow, EquipmentSlot.HAND).isCancelled());
    }

    @Test
    void theCarrierCannotInteractWithOtherEntities() {
        carriedCow();
        Cow second = world.spawn(new Location(world, -2, 64, 0), Cow.class);

        assertTrue(rightClick(player, second, EquipmentSlot.HAND).isCancelled());
        assertFalse(carry.isCarried(second));
    }

    @Test
    void rightClickingABlockPutsTheMobOnThatFace() {
        Cow cow = carriedCow();
        Block floor = world.getBlockAt(3, 63, 3);
        floor.setType(Material.STONE);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, floor, BlockFace.UP, EquipmentSlot.HAND);
        listener.onInteract(event);

        assertEquals(Event.Result.DENY, event.useInteractedBlock());
        assertFalse(carry.isCarrying(player));
        assertEquals(3.5, cow.getLocation().getX());
        assertEquals(64, cow.getLocation().getY());
        assertEquals(3.5, cow.getLocation().getZ());
    }

    @Test
    void theOffHandDuplicateOfAClickDoesNotPutTheMobDown() {
        carriedCow();
        Block floor = world.getBlockAt(3, 63, 3);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, floor, BlockFace.UP, EquipmentSlot.OFF_HAND);
        listener.onInteract(event);

        assertEquals(Event.Result.DENY, event.useInteractedBlock());
        assertTrue(carry.isCarrying(player));
    }

    @Test
    void sneakLeftClickThrows() {
        Cow cow = carriedCow();
        player.setSneaking(true);

        listener.onInteract(new PlayerInteractEvent(player, Action.LEFT_CLICK_AIR, null, null, BlockFace.SELF, EquipmentSlot.HAND));

        assertFalse(carry.isCarrying(player));
        assertTrue(cow.getVelocity().length() > 0.5);
    }

    @Test
    void leftClickWithoutSneakingKeepsCarrying() {
        carriedCow();

        listener.onInteract(new PlayerInteractEvent(player, Action.LEFT_CLICK_AIR, null, null, BlockFace.SELF, EquipmentSlot.HAND));

        assertTrue(carry.isCarrying(player));
    }

    @Test
    void teleportingPutsTheMobDownAtTheDeparturePoint() {
        Cow cow = carriedCow();
        Location from = player.getLocation();

        listener.onTeleport(new PlayerTeleportEvent(player, from, new Location(world, 100, 64, 100)));

        assertFalse(carry.isCarrying(player));
        assertEquals(from.getBlockX(), cow.getLocation().getBlockX());
        assertEquals(from.getBlockZ(), cow.getLocation().getBlockZ());
    }

    @Test
    void loggingOutPutsTheMobDown() {
        Cow cow = carriedCow();

        listener.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertFalse(carry.isCarrying(player));
        assertTrue(player.getPassengers().isEmpty());
        assertTrue(cow.isValid());
    }

    @Test
    void aCarriedMobTakesNoDamageExceptFromTheVoid() {
        Cow cow = carriedCow();
        DamageSource generic = DamageSource.builder(DamageType.GENERIC).build();
        DamageSource voidSource = DamageSource.builder(DamageType.OUT_OF_WORLD).build();

        EntityDamageEvent suffocation = new EntityDamageEvent(cow, EntityDamageEvent.DamageCause.SUFFOCATION, generic, 1);
        EntityDamageEvent voidDamage = new EntityDamageEvent(cow, EntityDamageEvent.DamageCause.VOID, voidSource, 4);
        listener.onCarriedDamaged(suffocation);
        listener.onCarriedDamaged(voidDamage);

        assertTrue(suffocation.isCancelled());
        assertFalse(voidDamage.isCancelled());
    }

    @Test
    void hotbarSlotsAreLockedWhileCarrying() {
        carriedCow();

        PlayerItemHeldEvent event = new PlayerItemHeldEvent(player, 0, 1);
        listener.onSlotChange(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void aForeignDismountReleasesTheCarrier() {
        Cow cow = carriedCow();

        listener.onDismount(new EntityDismountEvent(cow, player));

        assertFalse(carry.isCarrying(player));
    }
}
