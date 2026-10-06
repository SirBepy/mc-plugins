package dev.sirbepy.mc.dragonballs.radar;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

/** Wiring entry point for the radar feature: {@code DragonBallsPlugin.onEnable} calls this once. */
public final class RadarModule {

    private RadarModule() {
    }

    // config isn't needed by the radar task itself (registry.activeTargets() already excludes
    // dormant/stone balls), but every phase-2a module shares this signature so DragonBallsPlugin can
    // wire them identically.
    public static void register(JavaPlugin plugin, BallRegistry registry, Supplier<DragonBallsConfig> config) {
        RadarItems items = new RadarItems(plugin);
        RadarTask task = new RadarTask(registry, items);
        plugin.getServer().getScheduler().runTaskTimer(plugin, task, RadarTask.PERIOD_TICKS, RadarTask.PERIOD_TICKS);
    }
}
