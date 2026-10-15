package dev.sirbepy.mc.classes.talent;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * Particles and sounds for every ability and proc. Both go through the world, so every player in
 * range sees and hears them (the client's own particle range, about 32 blocks), not just the caster.
 */
public final class Fx {

    /** A puff of {@code count} particles spread {@code spread} blocks around {@code at}. */
    public void burst(Location at, Particle particle, int count, double spread) {
        World world = at.getWorld();
        if (world != null) world.spawnParticle(particle, at, count, spread, spread, spread, 0.02);
    }

    /** {@link #burst} at chest height on an entity. */
    public void burst(Entity on, Particle particle, int count, double spread) {
        burst(on.getLocation().add(0, on.getHeight() * 0.6, 0), particle, count, spread);
    }

    /** A flat ring of particles, {@code radius} blocks out, at {@code at}'s height. */
    public void ring(Location at, Particle particle, double radius, int points) {
        World world = at.getWorld();
        if (world == null) return;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            world.spawnParticle(particle, at.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius), 1, 0, 0, 0, 0);
        }
    }

    /** A line of particles from {@code from} to {@code to}, one every half block. */
    public void line(Location from, Location to, Particle particle) {
        World world = from.getWorld();
        if (world == null || to.getWorld() != world) return;
        double length = from.distance(to);
        var step = to.toVector().subtract(from.toVector()).normalize().multiply(0.5);
        Location at = from.clone();
        for (double d = 0; d < length; d += 0.5) {
            world.spawnParticle(particle, at, 1, 0, 0, 0, 0);
            at.add(step);
        }
    }

    public void sound(Location at, Sound sound, float volume, float pitch) {
        World world = at.getWorld();
        if (world != null) world.playSound(at, sound, SoundCategory.PLAYERS, volume, pitch);
    }
}
