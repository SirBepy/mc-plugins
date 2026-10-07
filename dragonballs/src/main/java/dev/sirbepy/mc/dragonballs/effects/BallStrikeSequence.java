package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The spectacle right before the dragon appears: each placed ball lights up in star order, about
 * {@link #STEP_TICKS} apart, then {@code onComplete} (the dragon spawn) runs. Overworld and End
 * get harmless effect-only lightning ({@code World#strikeLightningEffect} never damages, ignites
 * or converts anything - confirmed against the 26.2 Paper API this session); the Nether swaps
 * that for a flame burst since a real bolt there would look wrong next to a smoke dragon, and
 * never places real fire or ignites anything either. Every scheduled task is tracked so a session
 * that ends early (onEnd/shutdown) can cancel the rest of the sequence, including the dragon-spawn
 * handoff itself if it hasn't fired yet.
 */
final class BallStrikeSequence {

    static final long STEP_TICKS = 6; // ~0.3s so 7 balls span ~2s before the dragon appears

    private final JavaPlugin plugin;
    private final List<BukkitTask> scheduled = new ArrayList<>();

    BallStrikeSequence(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Lightning for Overworld/End, a flame burst for the Nether. */
    static boolean usesLightning(BallSet set) {
        return set != BallSet.NETHER;
    }

    /** Tick offset of the step-th strike (0-based), relative to the sequence's own start. */
    static long tickForStep(int step) {
        return step * STEP_TICKS;
    }

    /** Starts the sequence over {@code ballOrigins} (struck in ascending star order), then runs
     * {@code onComplete}. Cancels any sequence already running first so two never overlap. */
    void play(World world, Map<Integer, Location> ballOrigins, BallSet set, Runnable onComplete) {
        cancel();
        List<Integer> stars = new ArrayList<>(ballOrigins.keySet());
        Collections.sort(stars);
        if (stars.isEmpty()) {
            onComplete.run();
            return;
        }
        boolean lightning = usesLightning(set);
        for (int step = 0; step < stars.size(); step++) {
            Location at = ballOrigins.get(stars.get(step)).clone().add(0.5, 1.0, 0.5);
            scheduled.add(plugin.getServer().getScheduler()
                    .runTaskLater(plugin, () -> strikeOne(world, at, lightning), tickForStep(step)));
        }
        long completeDelay = tickForStep(stars.size() - 1) + STEP_TICKS;
        scheduled.add(plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            scheduled.clear();
            onComplete.run();
        }, completeDelay));
    }

    private void strikeOne(World world, Location at, boolean lightning) {
        if (lightning) {
            world.strikeLightningEffect(at);
            return;
        }
        world.spawnParticle(Particle.FLAME, at, 14, 0.3, 0.3, 0.3, 0.02);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 6, 0.2, 0.2, 0.2, 0.01);
        world.spawnParticle(Particle.LAVA, at, 2, 0.2, 0.2, 0.2, 0);
        for (Player player : world.getPlayers()) {
            player.playSound(at, Sound.ITEM_FLINTANDSTEEL_USE, 1.0f, 1.0f);
        }
    }

    /** Stops every pending strike and, if it hasn't run yet, the dragon-spawn handoff too. */
    void cancel() {
        for (BukkitTask task : scheduled) {
            task.cancel();
        }
        scheduled.clear();
    }
}
