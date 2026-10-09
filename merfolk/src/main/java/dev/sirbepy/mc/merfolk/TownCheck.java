package dev.sirbepy.mc.merfolk;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Land-claim rules, kept behind an interface so the plugin runs (and tests) without Towny. */
public interface TownCheck {

    /** True when {@code location} is inside a town the player is not a resident of. */
    boolean inOtherTown(Player player, Location location);

    /** True when the player may build at {@code location} (placing a Kelp Bed). */
    boolean mayBuild(Player player, Location location);

    /** True when the player may break blocks at {@code location} (picking a Kelp Bed up). */
    boolean mayDestroy(Player player, Location location);

    TownCheck NONE = new TownCheck() {
        @Override
        public boolean inOtherTown(Player player, Location location) {
            return false;
        }

        @Override
        public boolean mayBuild(Player player, Location location) {
            return true;
        }

        @Override
        public boolean mayDestroy(Player player, Location location) {
            return true;
        }
    };
}
