package dev.sirbepy.mc.dragonballs.task;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallView;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Runs every 5 minutes (never per-tick): re-scatters a ball whose holder has been offline past
 * {@code logout-hours}, and ends the stone cooldown once it's due. Both checks are cheap bookkeeping
 * against already-stored timestamps, not a scan of the world.
 */
public final class BallMaintenanceTask implements Runnable {

    public static final long PERIOD_TICKS = 20L * 60 * 5;

    private final BallRegistry registry;
    private final Supplier<DragonBallsConfig> config;

    public BallMaintenanceTask(BallRegistry registry, Supplier<DragonBallsConfig> config) {
        this.registry = registry;
        this.config = config;
    }

    @Override
    public void run() {
        checkLogoutTimeouts();
        checkStoneExpiry();
    }

    private void checkLogoutTimeouts() {
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

    private void checkStoneExpiry() {
        if (!registry.isDormant()) {
            return;
        }
        World world = Bukkit.getWorld(config.get().world());
        if (world == null) {
            return;
        }
        long currentDay = Math.floorDiv(world.getFullTime(), 24000L);
        if (registry.isStoneExpired(currentDay, System.currentTimeMillis())) {
            registry.clearStone();
        }
    }
}
