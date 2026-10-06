package dev.sirbepy.mc.dragonballs.scatter;

import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.Location;

/**
 * Asks Towny directly whether a location is wilderness - a scatter question ("could a ball ever
 * land here"), not a player-permission one, so {@code TownyAPI#isWilderness} is the right call, not
 * {@code PlayerCacheUtil} (what {@code carry}'s own TownyGuard uses, since that one is answering a
 * specific player's build/destroy permission).
 */
final class TownyWildernessGuard implements ScatterTownyGuard {

    @Override
    public boolean isWilderness(Location location) {
        return TownyAPI.getInstance().isWilderness(location);
    }
}
