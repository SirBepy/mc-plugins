package dev.sirbepy.mc.endfight.reset;

import dev.sirbepy.mc.endfight.arena.ChunkTickets;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies a snapshot back onto the live world: diffs it against what's there now
 * ({@link RestorePlan}), then spends {@code reset.restore-blocks-per-tick} block changes per tick
 * ({@link RestoreRun}) until done, chunks force-loaded and physics off throughout (same
 * {@code setBlockData(data, false)} pattern {@code ArenaBuilder} uses). Never touches entities -
 * only {@code World#getBlockAt}. Once every change is applied: releases the chunk tickets, removes
 * any remaining cult mob, re-registers the arena from saved state (the snapshot already contains
 * it, so no world rescan), and announces to the End that the arena is ready.
 */
public final class IslandRestorer implements RestoreStarter {

    private final Plugin plugin;
    private final Supplier<ResetConfig> config;
    private final Runnable removeCultMobs;
    private final Runnable reRegisterArena;
    private final Consumer<World> announceReady;
    private final Logger logger;

    private volatile boolean running;
    private volatile RestoreStats lastStats;

    public IslandRestorer(Plugin plugin, Supplier<ResetConfig> config, Runnable removeCultMobs,
                           Runnable reRegisterArena, Consumer<World> announceReady) {
        this.plugin = plugin;
        this.config = config;
        this.removeCultMobs = removeCultMobs;
        this.reRegisterArena = reRegisterArena;
        this.announceReady = announceReady;
        this.logger = plugin.getLogger();
    }

    public boolean isRunning() {
        return running;
    }

    /** {@code /endfight reset status}: how the last restore (any world) went, if one has run. */
    public Optional<RestoreStats> lastStats() {
        return Optional.ofNullable(lastStats);
    }

    /** Starts the restore for {@code end} against {@code snapshot}; {@code onComplete} (nullable)
     * runs once every block is applied and the arena's back in the registry - the respawn watcher
     * uses it to drop back to {@code IDLE}. */
    @Override
    public void start(World end, IslandSnapshot snapshot, Runnable onComplete) {
        running = true;
        long startMillis = System.currentTimeMillis();
        int radius = Math.max(snapshot.bounds().width(), snapshot.bounds().depth()) / 2 + 1;
        ChunkTickets.load(end, plugin, radius);

        BlockDataLookup currentLookup = (x, y, z) -> end.getBlockAt(x, y, z).getBlockData().getAsString();
        List<ChangedBlock> changes = RestorePlan.diff(snapshot, currentLookup);
        RestoreRun run = new RestoreRun(changes);
        int totalChanges = run.total();

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int budget = Math.max(1, config.get().restoreBlocksPerTick());
            for (ChangedBlock change : run.takeNext(budget)) {
                applyOne(end, change);
            }
            if (run.isDone()) {
                taskHolder[0].cancel();
                ChunkTickets.unload(end, plugin, radius);
                removeCultMobs.run();
                reRegisterArena.run();
                announceReady.accept(end);
                long elapsedMillis = System.currentTimeMillis() - startMillis;
                logger.info("Island restore complete: " + totalChanges + " blocks changed in " + elapsedMillis + "ms.");
                lastStats = new RestoreStats(totalChanges, elapsedMillis);
                running = false;
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        }, 0L, 1L);
    }

    private void applyOne(World end, ChangedBlock change) {
        try {
            end.getBlockAt(change.x(), change.y(), change.z())
                    .setBlockData(Bukkit.createBlockData(change.targetBlockData()), false);
        } catch (RuntimeException e) {
            // Same resilience as ArenaBuilder.placeOne: one bad/unsupported blockstate string must
            // never abort the whole restore.
            logger.log(Level.WARNING, "Could not restore " + change.targetBlockData() + " at "
                    + change.x() + "," + change.y() + "," + change.z(), e);
        }
    }
}
