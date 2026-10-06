package dev.sirbepy.mc.dragonballs.effects;

import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The marked summon dragon must never hurt anyone, break anything, be targeted, or take damage;
 * an ordinary, unmarked hostile mob must be left alone so the guard doesn't over-reach.
 */
class DragonGuardListenerTest {

    private ServerMock server;
    private WorldMock world;
    private DragonGuardListener listener;
    private PlayerMock player;
    private EnderDragon markedDragon;
    private Zombie plainMob;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        listener = new DragonGuardListener();
        player = server.addPlayer();
        markedDragon = world.spawn(new Location(world, 0, 80, 0), EnderDragon.class);
        markedDragon.addScoreboardTag(EffectMarkers.DRAGON_TAG);
        plainMob = world.spawn(new Location(world, 5, 80, 5), Zombie.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void damageDealtByTheMarkedDragonIsCancelled() {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(markedDragon, player,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(markedDragon).build(), 5.0);

        listener.onDamageByEntity(event);

        assertTrue(event.isCancelled(), "the summon dragon must never be able to hurt a player");
    }

    @Test
    void damageDealtByAnUnmarkedMobIsLeftAlone() {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(plainMob, player,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(plainMob).build(), 5.0);

        listener.onDamageByEntity(event);

        assertFalse(event.isCancelled(), "an ordinary mob's attack must not be touched by this guard");
    }

    @Test
    void damageTakenByTheMarkedDragonIsCancelled() {
        EntityDamageEvent event = new EntityDamageEvent(markedDragon, EntityDamageEvent.DamageCause.FALL,
                DamageSource.builder(DamageType.FALL).build(), 5.0);

        listener.onDamage(event);

        assertTrue(event.isCancelled(), "nothing should be able to hurt the summon dragon either");
    }

    @Test
    void explosionCausedByTheMarkedDragonIsCancelled() {
        EntityExplodeEvent event = new EntityExplodeEvent(markedDragon, markedDragon.getLocation(),
                List.of(), 1.0f, ExplosionResult.KEEP);

        listener.onExplode(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void blockChangeCausedByTheMarkedDragonIsCancelled() {
        Block block = world.getBlockAt(0, 80, 0);
        EntityChangeBlockEvent event = new EntityChangeBlockEvent(markedDragon, block, block.getBlockData());

        listener.onChangeBlock(event);

        assertTrue(event.isCancelled(), "the dragon flying through blocks must never break them");
    }

    @Test
    void anotherMobTargetingTheMarkedDragonIsCancelled() {
        EntityTargetEvent event = new EntityTargetEvent(plainMob, markedDragon, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);

        listener.onTarget(event);

        assertTrue(event.isCancelled(), "hostile mobs must not be able to target the summon dragon");
    }

    @Test
    void theMarkedDragonTargetingSomethingIsCancelled() {
        EntityTargetEvent event = new EntityTargetEvent(markedDragon, player, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);

        listener.onTarget(event);

        assertTrue(event.isCancelled(), "the summon dragon must never acquire a target either");
    }
}
