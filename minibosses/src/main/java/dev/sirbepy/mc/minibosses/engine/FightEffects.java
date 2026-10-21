package dev.sirbepy.mc.minibosses.engine;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;

/**
 * The only place the engine draws and plays sound. A seam so geometry and loop tests never reach
 * MockBukkit's unimplemented particle calls, and a test can record exactly what a telegraph drew.
 */
public interface FightEffects {

    /** One dust particle; a telegraph is hundreds of these. */
    void dust(World world, double x, double y, double z, Color color, float size);

    void sound(Location at, Sound sound, float volume, float pitch);

    /**
     * A few particles of any non-dust kind (ambient motes, glints). A no-op unless an implementation
     * draws them, so the loop tests that only record dust and sound need not know about it.
     */
    default void particle(World world, Particle particle, double x, double y, double z, int count, double spread) {
    }

    /** The real thing: dust seen from across a 30 block arena, sounds on the hostile channel. */
    FightEffects WORLD = new FightEffects() {
        @Override
        public void dust(World world, double x, double y, double z, Color color, float size) {
            // force = true: the default range is 32 blocks, short of the far corner of a big arena.
            world.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, new Particle.DustOptions(color, size), true);
        }

        @Override
        public void sound(Location at, Sound sound, float volume, float pitch) {
            at.getWorld().playSound(at, sound, SoundCategory.HOSTILE, volume, pitch);
        }

        @Override
        public void particle(World world, Particle particle, double x, double y, double z, int count, double spread) {
            world.spawnParticle(particle, x, y, z, count, spread, spread, spread, 0.0);
        }
    };
}
