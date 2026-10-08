package dev.sirbepy.mc.dragonballs.radar;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

/** Wiring entry point for the radar feature: {@code DragonBallsPlugin.onEnable} calls this once. */
public final class RadarModule {

    private RadarModule() {
    }

    public static void register(JavaPlugin plugin, BallSets sets, Supplier<DragonBallsConfig> config) {
        RadarItems items = new RadarItems(plugin);
        RadarTracking tracking = new RadarTracking();
        RadarTask task = new RadarTask(sets, items, tracking, config);
        plugin.getServer().getScheduler().runTaskTimer(plugin, task, RadarTask.PERIOD_TICKS, RadarTask.PERIOD_TICKS);
        plugin.getServer().getPluginManager().registerEvents(new RadarCycleListener(items, tracking, task), plugin);
        plugin.getServer().getPluginManager().registerEvents(new RadarResyncListener(task), plugin);
    }
}
