package dev.sirbepy.mc.carry;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarryManagerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private CarryTestSupport.FakeGuard guard;
    private CarryManager carry;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Carrier");
        player.teleport(new Location(world, 0, 64, 0));
        guard = new CarryTestSupport.FakeGuard();
        CarryConfig config = CarryTestSupport.shippedConfig();
        carry = new CarryManager(() -> config, List.of(guard));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Cow cow() {
        return world.spawn(new Location(world, 2, 64, 0), Cow.class);
    }

    @Test
    void shippedListCarriesPassiveMobsButNotHostilesOrPlayers() {
        CarryConfig config = CarryTestSupport.shippedConfig();
        assertTrue(config.isCarryable(EntityType.COW));
        assertTrue(config.isCarryable(EntityType.VILLAGER));
        assertTrue(config.isCarryable(EntityType.IRON_GOLEM));
        assertFalse(config.isCarryable(EntityType.ZOMBIE));
        assertFalse(config.isCarryable(EntityType.CREEPER));
        assertFalse(config.isCarryable(EntityType.PLAYER));
    }

    @Test
    void pickingUpMountsTheMobAndSlowsTheCarrier() {
        Cow cow = cow();
        double speed = player.getAttribute(Attribute.MOVEMENT_SPEED).getValue();

        assertNull(carry.tryPickUp(player, cow));

        assertTrue(player.getPassengers().contains(cow));
        assertTrue(carry.isCarrying(player));
        assertTrue(carry.isCarried(cow));
        assertNotNull(player.getAttribute(Attribute.MOVEMENT_SPEED).getModifier(CarryManager.SPEED_KEY));
        assertTrue(player.getAttribute(Attribute.MOVEMENT_SPEED).getValue() < speed);
    }

    @Test
    void hostileMobsCannotBePickedUp() {
        Zombie zombie = world.spawn(new Location(world, 2, 64, 0), Zombie.class);

        assertFalse(carry.isCarryable(zombie));
        assertEquals("denied-busy", carry.tryPickUp(player, zombie));
        assertFalse(carry.isCarrying(player));
    }

    @Test
    void otherPlayersAreNeverCarryable() {
        PlayerMock other = server.addPlayer("Other");

        assertFalse(carry.isCarryable(other));
    }

    @Test
    void aProtectionGuardBlocksPickUp() {
        guard.pickUpDenial = "denied-claim";
        Cow cow = cow();

        assertEquals("denied-claim", carry.tryPickUp(player, cow));
        assertFalse(carry.isCarrying(player));
        assertTrue(player.getPassengers().isEmpty());
    }

    @Test
    void someoneElsesTamedPetIsRefusedButTheOwnerMayCarryIt() {
        PlayerMock owner = server.addPlayer("Owner");
        Wolf wolf = world.spawn(new Location(world, 2, 64, 0), Wolf.class);
        wolf.setOwner(owner);

        assertEquals("denied-pet", carry.tryPickUp(player, wolf));
        assertNull(carry.tryPickUp(owner, wolf));
    }

    @Test
    void aMobCarriedBySomeoneElseIsRefused() {
        PlayerMock other = server.addPlayer("Other");
        Villager villager = world.spawn(new Location(world, 2, 64, 0), Villager.class);
        assertNull(carry.tryPickUp(other, villager));

        assertEquals("denied-busy", carry.tryPickUp(player, villager));
    }

    @Test
    void puttingDownMovesTheMobToTheTargetAndRestoresSpeed() {
        Cow cow = cow();
        double speed = player.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
        carry.tryPickUp(player, cow);
        Location target = new Location(world, 5.5, 65, 5.5);

        assertNull(carry.tryPutDown(player, target));

        assertFalse(carry.isCarrying(player));
        assertTrue(player.getPassengers().isEmpty());
        assertEquals(5.5, cow.getLocation().getX());
        assertEquals(65, cow.getLocation().getY());
        assertEquals(5.5, cow.getLocation().getZ());
        assertEquals(speed, player.getAttribute(Attribute.MOVEMENT_SPEED).getValue(), 1e-9);
    }

    @Test
    void aProtectionGuardBlocksPuttingDownSoTheMobStaysCarried() {
        Cow cow = cow();
        carry.tryPickUp(player, cow);
        guard.placeDenial = "denied-place";

        assertEquals("denied-place", carry.tryPutDown(player, new Location(world, 5, 65, 5)));
        assertTrue(carry.isCarrying(player));
        assertTrue(player.getPassengers().contains(cow));
    }

    @Test
    void throwingLaunchesTheMobAlongTheView() {
        player.teleport(new Location(world, 0, 64, 0, -90, 0)); // yaw -90 faces +x
        Cow cow = cow();
        carry.tryPickUp(player, cow);

        assertNull(carry.tryThrow(player));

        assertFalse(carry.isCarrying(player));
        assertTrue(cow.getVelocity().getX() > 0.5, "thrown forward, got " + cow.getVelocity());
        assertTrue(cow.getVelocity().getY() > 0, "thrown slightly upward, got " + cow.getVelocity());
        assertEquals(player.getLocation(), guard.lastPlaceCheck);
    }

    @Test
    void throwingIsRefusedWhereTheCarrierCannotBuild() {
        Cow cow = cow();
        carry.tryPickUp(player, cow);
        guard.placeDenial = "denied-place";

        assertEquals("denied-place", carry.tryThrow(player));
        assertTrue(carry.isCarrying(player));
    }

    @Test
    void forgettingAMobThatLeftOnItsOwnRestoresSpeed() {
        Cow cow = cow();
        double speed = player.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
        carry.tryPickUp(player, cow);

        carry.forget(cow);

        assertFalse(carry.isCarrying(player));
        assertEquals(speed, player.getAttribute(Attribute.MOVEMENT_SPEED).getValue(), 1e-9);
    }

    @Test
    void releaseAllPutsEveryMobDownAtItsCarrier() {
        Cow cow = cow();
        carry.tryPickUp(player, cow);

        carry.releaseAll(server.getOnlinePlayers());

        assertFalse(carry.isCarrying(player));
        assertTrue(player.getPassengers().isEmpty());
        assertEquals(player.getLocation().getBlockX(), cow.getLocation().getBlockX());
    }
}
