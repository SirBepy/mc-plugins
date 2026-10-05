package dev.sirbepy.mc.carry.guard;

import com.palmergames.bukkit.towny.object.TownyPermission.ActionType;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Taking a mob out of a plot needs the same permission as breaking a block there, and setting one
 * down needs build permission. Towny's own mob protection only covers killing mobs and stealing
 * them with boats, so carrying would otherwise bypass it.
 */
public final class TownyGuard implements CarryGuard {

    // Towny's permission cache is keyed by material only for its item_use/switch exemption lists;
    // a plain block keeps this check on the plot's ordinary build/destroy rules.
    private static final Material PLAIN = Material.DIRT;

    @Override
    public String denyPickUp(Player player, Entity entity) {
        return PlayerCacheUtil.getCachePermission(player, entity.getLocation(), PLAIN, ActionType.DESTROY) ? null : "denied-claim";
    }

    @Override
    public String denyPlace(Player player, Location location) {
        return PlayerCacheUtil.getCachePermission(player, location, PLAIN, ActionType.BUILD) ? null : "denied-place";
    }
}
