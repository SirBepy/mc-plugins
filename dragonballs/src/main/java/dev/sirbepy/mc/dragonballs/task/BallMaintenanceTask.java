package dev.sirbepy.mc.dragonballs.task;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import org.bukkit.World;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs once right after startup and then every 5 minutes (never per-tick): drops any ball still
 * HELD by an offline player where they logged out, and ends the stone cooldown once it's due,
 * independently for each of the 3 sets (each has its own stone cooldown and held balls). Both
 * checks are cheap bookkeeping against the registry, not a scan of the world. A disabled set (the
 * End before it opens) is skipped entirely.
 */
public final class BallMaintenanceTask implements Runnable {

    public static final long PERIOD_TICKS = 20L * 60 * 5;

    private final BallSets sets;
    private final Supplier<DragonBallsConfig> config;
    private final Consumer<BallRegistry> offlineHolderDrop;

    public BallMaintenanceTask(BallSets sets, Supplier<DragonBallsConfig> config,
                               Consumer<BallRegistry> offlineHolderDrop) {
        this.sets = sets;
        this.config = config;
        this.offlineHolderDrop = offlineHolderDrop;
    }

    @Override
    public void run() {
        for (BallRegistry registry : sets.all()) {
            if (!sets.isEnabled(registry.set())) {
                continue;
            }
            offlineHolderDrop.accept(registry);
            checkStoneExpiry(registry);
        }
    }

    private void checkStoneExpiry(BallRegistry registry) {
        if (!registry.isDormant()) {
            return;
        }
        World world = BallRegistry.stoneClock(config.get());
        if (world == null) {
            return;
        }
        long currentDay = Math.floorDiv(world.getFullTime(), 24000L);
        if (registry.isStoneExpired(currentDay, System.currentTimeMillis())) {
            registry.clearStone();
        }
    }
}
