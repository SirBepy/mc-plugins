package dev.sirbepy.mc.endfight.cult;

import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Vindicator;
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
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CultFriendlyFireTest {

    private ServerMock server;
    private WorldMock world;
    private CultFriendlyFire friendlyFire;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world_the_end");
        friendlyFire = new CultFriendlyFire();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Pillager cultPillager() {
        Pillager pillager = world.spawn(new Location(world, 0, 70, 0), Pillager.class);
        CultIdentity.apply(pillager);
        return pillager;
    }

    @Test
    void aDirectHitFromOneCultMobOnAnotherIsCancelled() {
        Pillager victim = cultPillager();
        Vindicator attacker = world.spawn(new Location(world, 1, 70, 0), Vindicator.class);
        CultIdentity.apply(attacker);

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 5);

        friendlyFire.onDamage(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void aBoltFiredByACultMobOnAnotherCultMobIsCancelled() {
        Pillager victim = cultPillager();
        Pillager shooter = world.spawn(new Location(world, 5, 70, 0), Pillager.class);
        CultIdentity.apply(shooter);
        Arrow bolt = world.spawn(new Location(world, 1, 70, 0), Arrow.class);
        bolt.setShooter(shooter);

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(bolt, victim,
                EntityDamageEvent.DamageCause.PROJECTILE, 5);

        friendlyFire.onDamage(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void anEvokersVexNeverHurtsAnotherCultMob() {
        Pillager victim = cultPillager();
        Evoker evoker = world.spawn(new Location(world, 5, 70, 0), Evoker.class);
        CultIdentity.apply(evoker);
        Vex vex = world.spawn(new Location(world, 1, 70, 0), Vex.class);
        vex.setOwner(evoker);

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(vex, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 3);

        friendlyFire.onDamage(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void anEvokersFangsNeverHurtAnotherCultMob() {
        Pillager victim = cultPillager();
        Evoker evoker = world.spawn(new Location(world, 5, 70, 0), Evoker.class);
        CultIdentity.apply(evoker);
        EvokerFangs fangs = world.spawn(new Location(world, 1, 70, 0), EvokerFangs.class);
        fangs.setOwner(evoker);

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(fangs, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 6);

        friendlyFire.onDamage(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void aPlayerHittingACultMobIsNeverCancelled() {
        Pillager victim = cultPillager();
        PlayerMock player = server.addPlayer("Hero");

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 5);

        friendlyFire.onDamage(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void theDragonHittingACultMobIsNeverCancelled() {
        Pillager victim = cultPillager();
        EnderDragon dragon = world.spawn(new Location(world, 0, 90, 0), EnderDragon.class);

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(dragon, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10);

        friendlyFire.onDamage(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void cultOnCultTargetingIsCancelled() {
        Pillager one = cultPillager();
        Pillager two = world.spawn(new Location(world, 2, 70, 0), Pillager.class);
        CultIdentity.apply(two);

        EntityTargetLivingEntityEvent event = new EntityTargetLivingEntityEvent(one, two, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);

        friendlyFire.onTarget(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void aCultMobTargetingAPlayerIsNeverCancelled() {
        Pillager one = cultPillager();
        PlayerMock player = server.addPlayer("Hero");

        EntityTargetLivingEntityEvent event = new EntityTargetLivingEntityEvent(one, player, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);

        friendlyFire.onTarget(event);

        assertFalse(event.isCancelled());
    }
}
