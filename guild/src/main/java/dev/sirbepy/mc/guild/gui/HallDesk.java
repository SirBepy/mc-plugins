package dev.sirbepy.mc.guild.gui;

import dev.sirbepy.mc.guild.Chat;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who is standing at the Guild Hall's desk. Taking and turning in work (registering, claiming, rerolling,
 * beginning or completing a trial) only happens on a board opened by talking to a staff member, so players
 * come to the hall (ImMijo's ask, 2026-10-09); {@code /guild} anywhere else is for looking. The staff stand
 * wherever ImMijo puts them, so the desk is wherever the staff member was talked to, never fixed coordinates.
 */
public final class HallDesk {

    /** Players can't walk with a menu open, so this only has to cover entity reach (3) plus a desk between. */
    static final double REACH = 6;
    static final String WARP = "/warp guild";

    private final Map<UUID, Location> visits = new HashMap<>();

    /** The player just talked to a staff member standing at {@code staffAt}. */
    public void visit(Player player, Location staffAt) {
        visits.put(player.getUniqueId(), staffAt.clone());
    }

    /** True while the player is still beside the staff member they last talked to. */
    public boolean at(Player player) {
        Location staffAt = visits.get(player.getUniqueId());
        Location here = player.getLocation();
        return staffAt != null && staffAt.getWorld() != null && staffAt.getWorld().equals(here.getWorld())
                && staffAt.distanceSquared(here) <= REACH * REACH;
    }

    /** Runs {@code action} at the desk; anywhere else tells the player where to go instead. */
    boolean require(Player player, Runnable action) {
        if (at(player)) {
            action.run();
            return true;
        }
        Chat.send(player, "Contracts are taken and turned in at the Guild Hall: talk to Lyra or Bram there. "
                + WARP, NamedTextColor.YELLOW);
        return false;
    }
}
