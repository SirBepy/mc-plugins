package dev.sirbepy.mc.dragonballs.placed;

import com.palmergames.bukkit.towny.object.TownyPermission.ActionType;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Taking a ball needs the same permission breaking its old head block did: destroy, on that plot. */
final class TownyPickupGuard implements PickupGuard {

    @Override
    public boolean mayPickUp(Player player, Location block) {
        return PlayerCacheUtil.getCachePermission(player, block, Material.PLAYER_HEAD, ActionType.DESTROY);
    }
}
