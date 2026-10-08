package dev.sirbepy.mc.dragonballs.placed;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Whether a player may take a floating ball from where it sits. A ball used to be a head block, so
 * Towny kept balls inside a town safe from anyone who couldn't break blocks there; hitting an
 * entity never reaches Towny's block rules, so this asks it the same question directly. Towny is a
 * soft dependency, only touched when it's enabled (same pattern as {@code ScatterTownyGuard}).
 */
public interface PickupGuard {

    boolean mayPickUp(Player player, Location block);

    static PickupGuard load(JavaPlugin plugin) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("Towny")) {
            return new TownyPickupGuard();
        }
        return (player, block) -> true;
    }
}
