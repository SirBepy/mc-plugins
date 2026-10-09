package dev.sirbepy.mc.endfight.reward;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Spawns an aura's particles with whatever data the particle needs. On 26.x {@code dragon_breath}
 * carries a power float (paper-api's {@code Particle.DRAGON_BREATH} has data type {@code Float}),
 * and Paper refuses a particle spawned without its data ("missing required data", javap on 26.3
 * {@code CraftParticle}), so a plain {@code spawnParticle} for it throws every tick.
 */
final class AuraParticles {

    /** Vanilla's own dragon breath power. */
    static final float BREATH_POWER = 1.0f;

    private AuraParticles() {
    }

    static void spawn(World world, Particle particle, Location at, int count, double spreadX, double spreadY, double spreadZ) {
        world.spawnParticle(particle, at, count, spreadX, spreadY, spreadZ, 0, dataFor(particle));
    }

    /** The data {@code particle} needs, or null when it takes none. */
    static Object dataFor(Particle particle) {
        return particle.getDataType() == Float.class ? BREATH_POWER : null;
    }
}
