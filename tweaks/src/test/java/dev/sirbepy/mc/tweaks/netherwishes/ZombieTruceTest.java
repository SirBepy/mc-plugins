package dev.sirbepy.mc.tweaks.netherwishes;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.EntityMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombieTruceTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock coffie;
    private PlayerMock stranger;
    private ZombieTruce truce;
    private long now = 1_000_000_000L;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        coffie = server.addPlayer("CoffiebagSupreme");
        stranger = server.addPlayer("Someone");
        truce = new ZombieTruce(server, coffie::getUniqueId, () -> now);
        server.getPluginManager().registerEvents(truce, MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Mob mob(EntityType type, SpawnReason reason) {
        Mob mob = (Mob) world.spawnEntity(coffie.getLocation().add(2, 0, 0), type);
        ((EntityMock) mob).setSpawnReason(reason);
        return mob;
    }

    /** True when the mob may target {@code target}. */
    private boolean mayTarget(Mob mob, LivingEntity target) {
        EntityTargetLivingEntityEvent event = new EntityTargetLivingEntityEvent(mob, target, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        server.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    private void hit(Entity damager, Entity causing, Mob mob) {
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(damager, mob, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(damager).withCausingEntity(causing).build(), 1));
    }

    @Test
    void zombiesHusksDrownedAndZombieVillagersLeaveHimAlone() {
        for (EntityType type : ZombieTruce.TYPES) {
            Mob mob = mob(type, SpawnReason.NATURAL);
            assertFalse(mayTarget(mob, coffie), type.name());
            assertTrue(mayTarget(mob, stranger), type.name());
        }
    }

    @Test
    void otherMobsStillHuntHim() {
        assertTrue(mayTarget(mob(EntityType.SKELETON, SpawnReason.NATURAL), coffie));
        assertTrue(mayTarget(mob(EntityType.ZOMBIFIED_PIGLIN, SpawnReason.NATURAL), coffie));
    }

    @Test
    void pluginSpawnedZombiesLikeTheCartelCousinsIgnoreTheTruce() {
        assertTrue(mayTarget(mob(EntityType.ZOMBIE, SpawnReason.CUSTOM), coffie));
    }

    @Test
    void onlyTheZombieHeHitFightsBack() {
        Mob hit = mob(EntityType.ZOMBIE, SpawnReason.NATURAL);
        Mob bystander = mob(EntityType.ZOMBIE, SpawnReason.NATURAL);
        Mob reinforcement = mob(EntityType.ZOMBIE, SpawnReason.REINFORCEMENTS);
        hit(coffie, coffie, hit);
        assertTrue(mayTarget(hit, coffie));
        assertFalse(mayTarget(bystander, coffie));
        assertFalse(mayTarget(reinforcement, coffie));
    }

    @Test
    void anArrowHeShotStartsAGrudge() {
        Mob drowned = mob(EntityType.DROWNED, SpawnReason.NATURAL);
        Arrow arrow = (Arrow) world.spawnEntity(coffie.getLocation(), EntityType.ARROW);
        arrow.setShooter(coffie);
        hit(arrow, coffie, drowned);
        assertTrue(mayTarget(drowned, coffie));
    }

    @Test
    void someoneElsesHitStartsNoGrudgeAgainstHim() {
        Mob zombie = mob(EntityType.ZOMBIE, SpawnReason.NATURAL);
        hit(stranger, stranger, zombie);
        assertFalse(mayTarget(zombie, coffie));
    }

    @Test
    void theGrudgeEndsFiveMinutesAfterHisLatestHit() {
        Mob zombie = mob(EntityType.ZOMBIE, SpawnReason.NATURAL);
        hit(coffie, coffie, zombie);
        now += ZombieTruce.GRUDGE.toMillis() - 1;
        assertTrue(mayTarget(zombie, coffie));
        hit(coffie, coffie, zombie);
        now += ZombieTruce.GRUDGE.toMillis() - 1;
        assertTrue(mayTarget(zombie, coffie));
        now += 1;
        assertFalse(mayTarget(zombie, coffie));
    }

    @Test
    void theTickCallsOffAChaseWithNoGrudgeAndKeepsOneWithIt() {
        Mob chasing = mob(EntityType.HUSK, SpawnReason.NATURAL);
        Mob grudging = mob(EntityType.HUSK, SpawnReason.NATURAL);
        Mob onStranger = mob(EntityType.HUSK, SpawnReason.NATURAL);
        hit(coffie, coffie, grudging);
        chasing.setTarget(coffie);
        grudging.setTarget(coffie);
        onStranger.setTarget(stranger);
        truce.tick();
        assertNull(chasing.getTarget());
        assertEquals(coffie, grudging.getTarget());
        assertEquals(stranger, onStranger.getTarget());
        now += ZombieTruce.GRUDGE.toMillis();
        truce.tick();
        assertNull(grudging.getTarget());
    }
}
