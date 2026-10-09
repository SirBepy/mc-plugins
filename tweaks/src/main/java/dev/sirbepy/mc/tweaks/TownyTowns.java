package dev.sirbepy.mc.tweaks;

import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.Location;

/** Only loaded when Towny is enabled; the class references Towny types directly. */
final class TownyTowns {

    private TownyTowns() {
    }

    static boolean inAnyTown(Location location) {
        return TownyAPI.getInstance().getTown(location) != null;
    }
}
