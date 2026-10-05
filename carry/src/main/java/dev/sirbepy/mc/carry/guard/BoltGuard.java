package dev.sirbepy.mc.carry.guard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.popcraft.bolt.BoltAPI;
import org.popcraft.bolt.util.Permission;

/**
 * A Bolt-locked mob can only be carried by someone allowed to destroy it, which covers its owner,
 * players the owner trusted, and SafeChests' grants (those are Bolt access entries too).
 */
public final class BoltGuard implements CarryGuard {

    private final BoltAPI api;

    private BoltGuard(BoltAPI api) {
        this.api = api;
    }

    public static BoltGuard load() {
        BoltAPI api = Bukkit.getServicesManager().load(BoltAPI.class);
        if (api == null) {
            throw new IllegalStateException("Bolt's BoltAPI service is not registered, is Bolt enabled?");
        }
        return new BoltGuard(api);
    }

    @Override
    public String denyPickUp(Player player, Entity entity) {
        return api.canAccess(entity, player, Permission.DESTROY) ? null : "denied-locked";
    }

    @Override
    public String denyPlace(Player player, Location location) {
        return null;
    }
}
