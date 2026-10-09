package dev.sirbepy.mc.merfolk;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownyPermission.ActionType;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Only loaded when Towny is enabled; the class references Towny types directly. */
final class TownyTownCheck implements TownCheck {

    // Towny's permission cache is keyed by material only for its item_use/switch exemption lists;
    // a plain block keeps this check on the plot's ordinary build/destroy rules (same as carry's TownyGuard).
    private static final Material PLAIN = Material.DIRT;

    @Override
    public boolean inOtherTown(Player player, Location location) {
        Town town = TownyAPI.getInstance().getTown(location);
        return town != null && !town.hasResident(player.getUniqueId());
    }

    @Override
    public boolean mayBuild(Player player, Location location) {
        return PlayerCacheUtil.getCachePermission(player, location, PLAIN, ActionType.BUILD);
    }

    @Override
    public boolean mayDestroy(Player player, Location location) {
        return PlayerCacheUtil.getCachePermission(player, location, PLAIN, ActionType.DESTROY);
    }
}
