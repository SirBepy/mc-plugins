package dev.sirbepy.mc.dragonballs.effects;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paper throws "missing required data" for a particle spawned without the data its type needs
 * (dragon breath needs a power float on 26.x), and MockBukkit's world accepts it silently, so this
 * records every spawn and checks the data against {@link Particle#getDataType()} itself.
 */
class DragonParticlesTest {

    private static final EffectsConfig CONFIG = new EffectsConfig(18.0, 1.0, 0.5, 18.0, 2.0, 3.0, 8, 4, 6);

    private ServerMock server;
    private RecordingWorld world;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new RecordingWorld();
        server.addWorld(world);
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theEndAuraGivesEveryParticleTheDataItsTypeNeeds() {
        assertEverySpawnFits(new DragonPlacement.Look(1.0, 18.0, 2.0, false, true));
        assertTrue(world.spawned.contains(Particle.DRAGON_BREATH));
    }

    @Test
    void theNetherColumnGivesEveryParticleTheDataItsTypeNeeds() {
        assertEverySpawnFits(new DragonPlacement.Look(0.5, 18.0, 2.0, true, false));
        assertFalse(world.spawned.isEmpty());
    }

    private void assertEverySpawnFits(DragonPlacement.Look look) {
        Location center = new Location(world, 0, 70, 0);
        DragonParticles particles = new DragonParticles(plugin, center, 18.0, look, 1.0, () -> center.clone().add(0, 18, 0));
        particles.startAuraTask(world, CONFIG);
        server.getScheduler().performTicks(10);
        particles.stopAuraTask();
        assertTrue(world.misfits.isEmpty(), "spawned without the data their type needs: " + world.misfits);
    }

    private static final class RecordingWorld extends WorldMock {
        final List<Particle> spawned = new ArrayList<>();
        final List<String> misfits = new ArrayList<>();

        @Override
        public void spawnParticle(Particle particle, Location location, int count,
                                  double offsetX, double offsetY, double offsetZ, double extra) {
            record(particle, null);
        }

        @Override
        public <T> void spawnParticle(Particle particle, Location location, int count,
                                      double offsetX, double offsetY, double offsetZ, double extra, T data) {
            record(particle, data);
        }

        private void record(Particle particle, Object data) {
            spawned.add(particle);
            Class<?> type = particle.getDataType();
            boolean fits = type == Void.class ? data == null : type.isInstance(data);
            if (!fits) {
                misfits.add(particle + " needs " + type.getSimpleName() + ", got " + data);
            }
        }
    }
}
