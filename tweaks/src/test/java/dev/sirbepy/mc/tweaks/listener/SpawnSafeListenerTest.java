package dev.sirbepy.mc.tweaks.listener;

import dev.sirbepy.mc.core.SpawnArea;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.ZombieMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spawn is a safe space from PvP and mobs (Joe, 2026-10-10). */
class SpawnSafeListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock attacker;
    private PlayerMock victim;
    private SpawnSafeListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        world.setSpawnLocation(100, 64, -200);
        attacker = server.addPlayer();
        victim = server.addPlayer();
        listener = new SpawnSafeListener();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Location inside() {
        return new Location(world, 100 + SpawnArea.RADIUS, 64, -200 + SpawnArea.RADIUS);
    }

    private Location outside(int dx) {
        return new Location(world, 100 + SpawnArea.RADIUS + 5 + dx, 64, -200);
    }

    private EntityDamageByEntityEvent hit(org.bukkit.entity.Entity damager) {
        return new EntityDamageByEntityEvent(damager, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4.0);
    }

    @Test
    void noPvpOnAPlayerInSpawn() {
        attacker.teleport(outside(0));
        victim.teleport(inside());
        EntityDamageByEntityEvent event = hit(attacker);
        listener.onDamage(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void noShootingOutOfSpawn() {
        attacker.teleport(inside());
        victim.teleport(outside(0));
        Arrow arrow = world.spawn(outside(1), Arrow.class);
        arrow.setShooter(attacker);
        EntityDamageByEntityEvent event = hit(arrow);
        listener.onDamage(event);
        assertTrue(event.isCancelled(), "the shooter stands in spawn");
    }

    @Test
    void pvpOutsideSpawnIsUntouched() {
        attacker.teleport(outside(0));
        victim.teleport(outside(1));
        EntityDamageByEntityEvent event = hit(attacker);
        listener.onDamage(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void mobsCannotHurtAPlayerInSpawnButStillCanOutside() {
        victim.teleport(inside());
        Zombie zombie = world.spawn(outside(0), Zombie.class);
        EntityDamageByEntityEvent inSpawn = hit(zombie);
        listener.onDamage(inSpawn);
        assertTrue(inSpawn.isCancelled());

        victim.teleport(outside(1));
        EntityDamageByEntityEvent outSpawn = hit(zombie);
        listener.onDamage(outSpawn);
        assertFalse(outSpawn.isCancelled());
    }

    @Test
    void hostileMobsDontSpawnInsideButAnimalsAndCommandsDo() {
        Zombie zombie = world.spawn(inside(), Zombie.class);
        CreatureSpawnEvent natural = new CreatureSpawnEvent(zombie, CreatureSpawnEvent.SpawnReason.NATURAL);
        listener.onSpawn(natural);
        assertTrue(natural.isCancelled());

        CreatureSpawnEvent command = new CreatureSpawnEvent(zombie, CreatureSpawnEvent.SpawnReason.COMMAND);
        listener.onSpawn(command);
        assertFalse(command.isCancelled());

        Cow cow = world.spawn(inside(), Cow.class);
        CreatureSpawnEvent animal = new CreatureSpawnEvent(cow, CreatureSpawnEvent.SpawnReason.NATURAL);
        listener.onSpawn(animal);
        assertFalse(animal.isCancelled());

        Zombie far = world.spawn(outside(0), Zombie.class);
        CreatureSpawnEvent outside = new CreatureSpawnEvent(far, CreatureSpawnEvent.SpawnReason.NATURAL);
        listener.onSpawn(outside);
        assertFalse(outside.isCancelled());
    }

    @Test
    void hostileMobsCannotTargetAPlayerInSpawn() {
        Zombie zombie = world.spawn(outside(0), Zombie.class);
        victim.teleport(inside());
        EntityTargetLivingEntityEvent event = new EntityTargetLivingEntityEvent(zombie, victim,
                EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        listener.onTarget(event);
        assertTrue(event.isCancelled());

        victim.teleport(outside(1));
        EntityTargetLivingEntityEvent allowed = new EntityTargetLivingEntityEvent(zombie, victim,
                EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        listener.onTarget(allowed);
        assertFalse(allowed.isCancelled());
    }

    /** MockBukkit leaves get/setRemoveWhenFarAway unimplemented, so the test's zombies answer it themselves. */
    private Zombie zombie(Location at, boolean despawnable) {
        Zombie zombie = new ZombieMock(server, UUID.randomUUID()) {
            @Override
            public boolean getRemoveWhenFarAway() {
                return despawnable;
            }
        };
        zombie.teleport(at);
        return zombie;
    }

    @Test
    void theSweepRemovesDespawnableHostilesInSpawnOnly() {
        Zombie walkedIn = zombie(inside(), true);
        Zombie named = zombie(inside(), false);
        Zombie outside = zombie(outside(0), true);
        Cow cow = world.spawn(inside(), Cow.class);

        SpawnSafeListener.sweep(List.of(walkedIn, named, outside, cow));

        assertTrue(walkedIn.isDead(), "a hostile that wandered in is removed");
        assertFalse(named.isDead(), "a persistent (name-tagged or plugin-kept) one stays");
        assertFalse(outside.isDead());
        assertTrue(cow.isValid());
    }
}
