package dev.sirbepy.mc.dragonballs.station;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.radar.RadarItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

/** Wiring entry point for the spawn-market station: {@code DragonBallsPlugin.onEnable} calls this once. */
public final class StationModule {

    private StationModule() {
    }

    // registry isn't needed by the shop/display (decor heads are never registered as balls), but
    // every phase-2a module shares this signature so DragonBallsPlugin can wire them identically.
    public static void register(JavaPlugin plugin, BallRegistry registry, Supplier<DragonBallsConfig> config) {
        Supplier<StationConfig> stationConfig = () -> StationConfig.from(plugin.getConfig());
        RadarItems radarItems = new RadarItems(plugin);
        StationDisplay display = new StationDisplay(plugin, config, stationConfig);
        RadarShop shop = new RadarShop(plugin, stationConfig, radarItems);

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(display, plugin);
        pluginManager.registerEvents(new StationProtectionListener(display), plugin);
        pluginManager.registerEvents(shop, plugin);

        // Worlds aren't guaranteed loaded yet during onEnable; placeAll() quietly skips any head
        // whose world isn't up, and StationDisplay.onChunkLoad() catches it once that world does load.
        plugin.getServer().getScheduler().runTask(plugin, display::placeAll);
    }
}
