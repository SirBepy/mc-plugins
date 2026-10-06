package dev.sirbepy.mc.dragonballs.effects;

import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spawning must leave exactly one harmless, marked, scaled dragon above the centre; ending it
 * (either way) must leave none, and a stray from a previous run must never survive a sweep.
 */
class DragonActorTest {

    private ServerMock server;
    private WorldMock world;
    private PluginMock plugin;
    private DragonActor actor;
    private Location center;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        actor = new DragonActor(plugin);
        center = new Location(world, 100, 64, 100);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void spawnCreatesAHarmlessMarkedDragon() {
        // Scale itself isn't asserted here: MockBukkit's attribute-defaults table has no entry for
        // Attribute.SCALE on any entity type, so getAttribute(SCALE) is always null in this mock,
        // whatever the config says. The real server always has it (LivingEntity.createLivingAttributes
        // includes SCALE for every living entity, EnderDragon included) - config.dragonScale() being
        // applied there is a verify-in-game item, see the task report.
        EffectsConfig config = new EffectsConfig(18.0, 2.5);

        actor.spawn(center, DragonType.SHENRON, config);

        EnderDragon dragon = actor.current();
        assertTrue(dragon != null && dragon.isValid(), "a dragon should now exist");
        assertTrue(dragon.getScoreboardTags().contains(EffectMarkers.DRAGON_TAG));
        assertFalse(dragon.hasAI(), "the dragon must be NoAI");
        assertTrue(dragon.isInvulnerable());
        assertFalse(dragon.isPersistent());
        assertFalse(dragon.isCollidable(), "players must not be able to push it");
        assertFalse(dragon.hasGravity());
        assertEquals(center.getY() + 18.0, dragon.getLocation().getY(), 1.0e-6);
    }

    @Test
    void porungaGlowsAndShenronDoesNot() {
        EffectsConfig config = new EffectsConfig(18.0, 1.0);

        actor.spawn(center, DragonType.PORUNGA, config);
        assertTrue(actor.current().isGlowing());

        actor.spawn(center, DragonType.SHENRON, config);
        assertFalse(actor.current().isGlowing());
    }

    @Test
    void despawnWithEffectsRemovesTheDragon() {
        actor.spawn(center, DragonType.SHENRON, new EffectsConfig(18.0, 1.0));
        EnderDragon dragon = actor.current();

        actor.despawnWithEffects();

        assertFalse(dragon.isValid(), "the dragon entity should be gone");
        assertNull(actor.current());
    }

    @Test
    void despawnImmediateRemovesTheDragonWithNoSurvivors() {
        actor.spawn(center, DragonType.SHENRON, new EffectsConfig(18.0, 1.0));
        EnderDragon dragon = actor.current();

        actor.despawnImmediate();

        assertFalse(dragon.isValid());
        assertNull(actor.current());
    }

    @Test
    void removeStrayDeletesAMarkedLeftoverButKeepsTheTrackedOne() {
        actor.spawn(center, DragonType.SHENRON, new EffectsConfig(18.0, 1.0));
        EnderDragon tracked = actor.current();
        EnderDragon stray = world.spawn(new Location(world, -50, 64, -50), EnderDragon.class);
        stray.addScoreboardTag(EffectMarkers.DRAGON_TAG);

        actor.removeStray(world);

        assertTrue(tracked.isValid(), "the dragon this run is actively using must survive the sweep");
        assertFalse(stray.isValid(), "a marked dragon this actor isn't tracking must be removed");
    }
}
