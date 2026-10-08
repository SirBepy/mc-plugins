package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.scoreboard.Team;
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
 * (either way) must leave none, and a stray from a previous run must never survive a sweep. The
 * Nether path must clamp under a ceiling and never place the dragon inside a block; the Overworld
 * and End paths must keep the plain, unscaled height.
 */
class DragonActorTest {

    private static final EffectsConfig DEFAULT_CONFIG = new EffectsConfig(18.0, 1.0, 0.5, 18.0, 2.0, 3.0, 8, 4, 6);

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

    private static EffectsConfig withDragonScale(double scale) {
        return new EffectsConfig(18.0, scale, 0.5, 18.0, 2.0, 3.0, 8, 4, 6);
    }

    @Test
    void spawnCreatesAHarmlessMarkedDragon() {
        // Scale itself isn't asserted here: MockBukkit's attribute-defaults table has no entry for
        // Attribute.SCALE on any entity type, so getAttribute(SCALE) is always null in this mock,
        // whatever the config says. The real server always has it (LivingEntity.createLivingAttributes
        // includes SCALE for every living entity, EnderDragon included) - config.dragonScale() being
        // applied there is a verify-in-game item, see the task report.
        EffectsConfig config = withDragonScale(2.5);

        actor.spawn(center, DragonType.SHENRON, BallSet.OVERWORLD, config);

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
        actor.spawn(center, DragonType.PORUNGA, BallSet.OVERWORLD, DEFAULT_CONFIG);
        assertTrue(actor.current().isGlowing());

        actor.spawn(center, DragonType.SHENRON, BallSet.OVERWORLD, DEFAULT_CONFIG);
        assertFalse(actor.current().isGlowing());
    }

    @Test
    void floatingNameFollowsTheSet() {
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

        actor.spawn(center, DragonType.SHENRON, BallSet.NETHER, DEFAULT_CONFIG);
        assertEquals("Nuova Shenron", plain.serialize(actor.current().customName()));

        actor.spawn(center, DragonType.SHENRON, BallSet.OVERWORLD, DEFAULT_CONFIG);
        assertEquals("Shenron", plain.serialize(actor.current().customName()));

        actor.spawn(center, DragonType.PORUNGA, BallSet.NETHER, DEFAULT_CONFIG);
        assertEquals("Porunga", plain.serialize(actor.current().customName()));
    }

    @Test
    void despawnWithEffectsRemovesTheDragon() {
        actor.spawn(center, DragonType.SHENRON, BallSet.OVERWORLD, DEFAULT_CONFIG);
        EnderDragon dragon = actor.current();

        actor.despawnWithEffects();

        assertFalse(dragon.isValid(), "the dragon entity should be gone");
        assertNull(actor.current());
    }

    @Test
    void despawnImmediateRemovesTheDragonWithNoSurvivors() {
        actor.spawn(center, DragonType.SHENRON, BallSet.OVERWORLD, DEFAULT_CONFIG);
        EnderDragon dragon = actor.current();

        actor.despawnImmediate();

        assertFalse(dragon.isValid());
        assertNull(actor.current());
    }

    @Test
    void removeStrayDeletesAMarkedLeftoverButKeepsTheTrackedOne() {
        actor.spawn(center, DragonType.SHENRON, BallSet.OVERWORLD, DEFAULT_CONFIG);
        EnderDragon tracked = actor.current();
        EnderDragon stray = world.spawn(new Location(world, -50, 64, -50), EnderDragon.class);
        stray.addScoreboardTag(EffectMarkers.DRAGON_TAG);

        actor.removeStray(world);

        assertTrue(tracked.isValid(), "the dragon this run is actively using must survive the sweep");
        assertFalse(stray.isValid(), "a marked dragon this actor isn't tracking must be removed");
    }

    @Test
    void endKeepsTheDefaultHeightJustLikeOverworld() {
        actor.spawn(center, DragonType.SHENRON, BallSet.END, DEFAULT_CONFIG);

        assertEquals(center.getY() + 18.0, actor.current().getLocation().getY(), 1.0e-6);
    }

    @Test
    void netherClampsHoverHeightUnderARealCavernCeiling() {
        // A solid roof 10 blocks above the summon centre, same shape as a Nether cavern: netherHoverHeight(10, 0.5, 18, 2, 3) = 4.
        world.getBlockAt(center.clone().add(0, 10, 0)).setType(Material.NETHERRACK);

        actor.spawn(center, DragonType.SHENRON, BallSet.NETHER, DEFAULT_CONFIG);

        assertEquals(center.getY() + 4.0, actor.current().getLocation().getY(), 1.0e-6,
            "the dragon should hover low enough to clear the ceiling with the configured margin");
    }

    @Test
    void netherNeverPlacesTheDragonInsideBlocksEvenWithTinyHeadroom() {
        // Ceiling only 5 blocks up: netherHoverHeight(5, 0.5, 18, 2, 3) = 1 (body top at 1+4=5, right at the ceiling, never past it).
        world.getBlockAt(center.clone().add(0, 5, 0)).setType(Material.NETHERRACK);

        actor.spawn(center, DragonType.SHENRON, BallSet.NETHER, DEFAULT_CONFIG);

        double hoverHeight = actor.current().getLocation().getY() - center.getY();
        assertEquals(1.0, hoverHeight, 1.0e-6);
        double bodyTop = center.getY() + hoverHeight + DragonPlacement.BODY_HEIGHT_AT_SCALE_1 * 0.5;
        assertTrue(bodyTop <= center.getY() + 5.0, "the scaled body must never reach past the ceiling block");
    }

    @Test
    void netherDragonGetsTheSmokeColumnButOverworldDoesNot() {
        actor.spawn(center, DragonType.SHENRON, BallSet.NETHER, DEFAULT_CONFIG);
        server.getScheduler().performTicks(10);
        assertTrue(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.CAMPFIRE_SIGNAL_SMOKE),
            "a Nether session should emit its smoke column");

        world.clearSpawnedParticles();
        actor.spawn(center, DragonType.SHENRON, BallSet.OVERWORLD, DEFAULT_CONFIG);
        server.getScheduler().performTicks(10);
        assertFalse(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.CAMPFIRE_SIGNAL_SMOKE),
            "the Overworld must never get the Nether's smoke column");
    }

    @Test
    void endDragonGetsThePurpleAuraButOverworldDoesNot() {
        actor.spawn(center, DragonType.SHENRON, BallSet.END, DEFAULT_CONFIG);
        server.getScheduler().performTicks(10);
        assertTrue(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.PORTAL),
            "an End session should emit its purple aura");

        world.clearSpawnedParticles();
        actor.spawn(center, DragonType.SHENRON, BallSet.OVERWORLD, DEFAULT_CONFIG);
        server.getScheduler().performTicks(10);
        assertFalse(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.PORTAL),
            "the Overworld must never get the End's purple aura");
    }

    @Test
    void theAuraTaskStopsOnceTheSessionEnds() {
        actor.spawn(center, DragonType.SHENRON, BallSet.NETHER, DEFAULT_CONFIG);
        server.getScheduler().performTicks(10);
        assertTrue(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.CAMPFIRE_SIGNAL_SMOKE));

        actor.despawnImmediate();
        world.clearSpawnedParticles();
        server.getScheduler().performTicks(30);

        assertTrue(world.getSpawnedParticles().isEmpty(), "no more column particles once the dragon is gone");
    }

    @Test
    void nuovaGlowsRedAndPorungaFromTheNetherDoesNot() {
        actor.spawn(center, DragonType.SHENRON, BallSet.NETHER, DEFAULT_CONFIG);
        EnderDragon nuova = actor.current();
        Team team = server.getScoreboardManager().getMainScoreboard().getTeam(DragonActor.NUOVA_TEAM);

        assertTrue(nuova.isGlowing(), "Nuova glows");
        assertTrue(team != null && team.hasEntry(nuova.getUniqueId().toString()), "red team carries the glow colour");
        assertEquals(NamedTextColor.RED, team.color());

        actor.spawn(center, DragonType.PORUNGA, BallSet.NETHER, DEFAULT_CONFIG);
        assertFalse(team.hasEntry(actor.current().getUniqueId().toString()), "Porunga keeps his default glow");
        assertFalse(team.hasEntry(nuova.getUniqueId().toString()), "the replaced Nuova left the team");
    }

    @Test
    void onlyNuovaLeavesAFireTrailAndItStopsWithHim() {
        actor.spawn(center, DragonType.SHENRON, BallSet.NETHER, DEFAULT_CONFIG);
        server.getScheduler().performTicks(4);
        assertTrue(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.SMALL_FLAME),
            "Nuova should trail fire as he circles");

        actor.spawn(center, DragonType.PORUNGA, BallSet.NETHER, DEFAULT_CONFIG);
        world.clearSpawnedParticles();
        server.getScheduler().performTicks(20);
        assertFalse(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.SMALL_FLAME),
            "a Nether Porunga gets the smoke column but no fire trail");
    }

    @Test
    void despawningNuovaTakesHimOffTheRedTeam() {
        actor.spawn(center, DragonType.SHENRON, BallSet.NETHER, DEFAULT_CONFIG);
        String entry = actor.current().getUniqueId().toString();

        actor.despawnWithEffects();

        Team team = server.getScoreboardManager().getMainScoreboard().getTeam(DragonActor.NUOVA_TEAM);
        assertFalse(team.hasEntry(entry));
    }
}
