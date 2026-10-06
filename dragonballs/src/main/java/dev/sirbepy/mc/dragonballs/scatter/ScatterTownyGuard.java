package dev.sirbepy.mc.dragonballs.scatter;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Whether a candidate spot is wilderness (so a ball may land there) or inside a town claim (so it
 * may not). Towny is a soft dependency: {@link #load} only touches {@link TownyWildernessGuard},
 * which references Towny's own API classes, when the plugin is actually enabled, so DragonBalls
 * still loads without Towny installed.
 */
public interface ScatterTownyGuard {

    boolean isWilderness(Location location);

    static ScatterTownyGuard load(JavaPlugin plugin) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("Towny")) {
            return new TownyWildernessGuard();
        }
        return location -> true;
    }
}
