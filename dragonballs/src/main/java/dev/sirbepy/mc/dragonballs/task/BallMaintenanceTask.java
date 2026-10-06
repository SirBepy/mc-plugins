package dev.sirbepy.mc.dragonballs.task;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.registry.BallView;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Runs every 5 minutes (never per-tick): re-scatters a ball whose holder has been offline past
 * {@code logout-hours}, and ends the stone cooldown once it's due, independently for each of the 3
 * sets (each has its own stone cooldown and held balls). Both checks are cheap bookkeeping against
 * already-stored timestamps, not a scan of the world. A disabled set (the End before it opens) is
 * skipped entirely.
 */
public final class BallMaintenanceTask implements Runnable {

    public static final long PERIOD_TICKS = 20L * 60 * 5;

    private final BallSets sets;
    private final Supplier<DragonBallsConfig> config;

    public BallMaintenanceTask(BallSets sets, Supplier<DragonBallsConfig> config) {
        this.sets = sets;
        this.config = config;
    }

    @Override
    public void run() {
        for (BallRegistry registry : sets.all()) {
            if (!sets.isEnabled(registry.set())) {
                continue;
            }
            checkLogoutTimeouts(registry);
            checkStoneExpiry(registry);
        }
    }

    private void checkLogoutTimeouts(BallRegistry registry) {
        long now = System.currentTimeMillis();
        long timeoutMillis = Duration.ofHours(config.get().logoutHours()).toMillis();
        Location lastSummon = registry.lastSummon();
        for (BallView view : registry.held()) {
            if (Bukkit.getPlayer(view.holderId()) != null) {
                registry.touchHolderSeen(view.star(), now);
            } else if (now - view.lastSeenMillis() >= timeoutMillis && lastSummon != null) {
                registry.scatterOne(view.star(), lastSummon, false);
            }
        }
    }

    private void checkStoneExpiry(BallRegistry registry) {
        if (!registry.isDormant()) {
            return;
        }
        World world = registry.set().world(config.get());
        if (world == null) {
            return;
        }
        long currentDay = Math.floorDiv(world.getFullTime(), 24000L);
        if (registry.isStoneExpired(currentDay, System.currentTimeMillis())) {
            registry.clearStone();
        }
    }
}
