package dev.sirbepy.mc.minibosses.engine;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * The only place the engine draws, plays sound, hurts a player and holds the boss's body still. A
 * seam so geometry and loop tests never reach MockBukkit's unimplemented particle, damage and
 * pathfinder calls, and a test can record exactly what a telegraph drew and who was hurt.
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

    /**
     * Debris of {@code material} flying up from a point, as if that block cracked. Purely visual:
     * no block changes. A no-op unless an implementation draws it.
     */
    default void blockCrack(World world, Material material, double x, double y, double z, int count, double spread) {
    }

    /** Hurts {@code target} as the damage {@code source} describes. */
    default void hurt(Player target, double amount, DamageSource source) {
        target.damage(amount, source);
    }

    /** Restores {@code amount} health to {@code target} (never above its maximum); fires the regain event plugins listen to. */
    default void heal(LivingEntity target, double amount) {
        target.heal(amount, EntityRegainHealthEvent.RegainReason.CUSTOM);
    }

    /**
     * Stops the boss's own AI (no walking, no melee) while an attack winds up and lands, or gives it
     * back. An unaware mob still takes knockback and gravity.
     */
    default void hold(Mob boss, boolean held) {
        if (held) {
            boss.getPathfinder().stopPathfinding();
        }
        boss.setAware(!held);
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

        @Override
        public void blockCrack(World world, Material material, double x, double y, double z, int count,
                double spread) {
            world.spawnParticle(Particle.BLOCK, x, y, z, count, spread, spread * 0.5, spread, 0.0, material.createBlockData());
        }
    };
}
