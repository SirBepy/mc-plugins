package dev.sirbepy.mc.endfight.reward;

import org.bukkit.Particle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuraParticlesTest {

    // Paper throws "missing required data" for a particle spawned without the data its type
    // needs; dragon breath needs a power float on 26.x.
    @Test
    void everyAuraParticleGetsTheDataItsTypeNeeds() {
        for (DragonAura aura : DragonAura.values()) {
            assertFits(aura.particle());
        }
        for (Particle presence : new Particle[] {Particle.DRAGON_BREATH, Particle.END_ROD, Particle.PORTAL}) {
            assertFits(presence);
        }
        assertEquals(AuraParticles.BREATH_POWER, AuraParticles.dataFor(Particle.DRAGON_BREATH));
    }

    private static void assertFits(Particle particle) {
        Object data = AuraParticles.dataFor(particle);
        Class<?> type = particle.getDataType();
        assertTrue(type == Void.class ? data == null : type.isInstance(data), particle + " needs " + type + ", got " + data);
    }
}
