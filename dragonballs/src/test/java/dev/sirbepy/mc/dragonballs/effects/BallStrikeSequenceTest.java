package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-ball strike sequence: 7 steps 6 ticks apart, flames (never lightning) for the Nether,
 * and the dragon-spawn handoff only firing once every step has run - or never, if cancelled early.
 * Overworld/End's actual lightning call isn't exercised here: MockBukkit's
 * {@code World#strikeLightningEffect} throws {@code UnimplementedOperationException} (a
 * {@code TestAbortedException} subtype, confirmed via javap on mockbukkit-v26.2-4.116.1.jar this
 * session), which would abort the whole test rather than fail just that assertion. {@link
 * #usesLightning(BallSet)}'s selection is covered directly below instead.
 */
class BallStrikeSequenceTest {

    private ServerMock server;
    private WorldMock world;
    private PluginMock plugin;
    private BallStrikeSequence sequence;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        sequence = new BallStrikeSequence(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void usesLightningForOverworldAndEndButFlamesForTheNether() {
        assertTrue(BallStrikeSequence.usesLightning(BallSet.OVERWORLD));
        assertTrue(BallStrikeSequence.usesLightning(BallSet.END));
        assertFalse(BallStrikeSequence.usesLightning(BallSet.NETHER));
    }

    @Test
    void stepsAreSixTicksApart() {
        assertEquals(0L, BallStrikeSequence.tickForStep(0));
        assertEquals(6L, BallStrikeSequence.tickForStep(1));
        assertEquals(36L, BallStrikeSequence.tickForStep(6));
    }

    @Test
    void netherStrikesBurnFlameParticlesInsteadOfLightning() {
        Map<Integer, Location> origins = sevenBallOrigins();
        boolean[] completed = {false};

        sequence.play(world, origins, BallSet.NETHER, () -> completed[0] = true);
        server.getScheduler().performTicks(1); // the first step's minimum delay is 1 tick

        assertTrue(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.FLAME),
            "the first Nether strike should burn flame particles");
        assertFalse(completed[0], "onComplete must not fire before every step has run");
    }

    @Test
    void dragonSpawnHandoffOnlyFiresAfterEveryStepHasRun() {
        Map<Integer, Location> origins = sevenBallOrigins();
        boolean[] completed = {false};

        sequence.play(world, origins, BallSet.NETHER, () -> completed[0] = true);
        long lastStepTick = BallStrikeSequence.tickForStep(6);
        server.getScheduler().performTicks(lastStepTick); // the 7th strike just ran; the handoff is still STEP_TICKS away
        assertFalse(completed[0], "onComplete must wait for the final step's own period to elapse too");

        server.getScheduler().performTicks(BallStrikeSequence.STEP_TICKS);
        assertTrue(completed[0], "onComplete should fire once the sequence is done");
    }

    @Test
    void cancelStopsEveryPendingStrikeAndTheDragonHandoff() {
        Map<Integer, Location> origins = sevenBallOrigins();
        boolean[] completed = {false};

        sequence.play(world, origins, BallSet.NETHER, () -> completed[0] = true);
        sequence.cancel();
        world.clearSpawnedParticles();
        server.getScheduler().performTicks(BallStrikeSequence.tickForStep(6) + BallStrikeSequence.STEP_TICKS + 5);

        assertTrue(world.getSpawnedParticles().isEmpty(), "a cancelled sequence must never burn its remaining flames");
        assertFalse(completed[0], "a cancelled sequence must never hand off to the dragon spawn");
    }

    private Map<Integer, Location> sevenBallOrigins() {
        Map<Integer, Location> origins = new LinkedHashMap<>();
        for (int star = 1; star <= 7; star++) {
            origins.put(star, new Location(world, star, 70, 0));
        }
        return origins;
    }
}
