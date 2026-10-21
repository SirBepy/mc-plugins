package dev.sirbepy.mc.minibosses.sentinel;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** The Nature Pulse's look and sound: a green ring that grows from the caster out to the pulse radius. */
public final class NaturePulseVisuals implements NaturePulse.Visuals {

    private static final int RING_STEPS = 6;
    private static final int POINTS_PER_BLOCK = 2;

    private final JavaPlugin plugin;

    public NaturePulseVisuals(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void play(Player caster, double radius) {
        Location center = caster.getLocation().add(0, 0.2, 0);
        World world = center.getWorld();
        world.playSound(center, Sound.BLOCK_AZALEA_LEAVES_BREAK, 1.0f, 0.8f);
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        // One ring per tick rather than one burst, so the pulse reads as travelling outward.
        new BukkitRunnable() {
            private int step = 1;

            @Override
            public void run() {
                double ringRadius = radius * step / RING_STEPS;
                int points = (int) Math.max(8, Math.round(2 * Math.PI * ringRadius * POINTS_PER_BLOCK));
                for (int p = 0; p < points; p++) {
                    double angle = Math.PI * 2 * p / points;
                    world.spawnParticle(Particle.HAPPY_VILLAGER,
                            center.getX() + Math.cos(angle) * ringRadius, center.getY(),
                            center.getZ() + Math.sin(angle) * ringRadius, 1, 0, 0, 0, 0);
                }
                if (++step > RING_STEPS) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
