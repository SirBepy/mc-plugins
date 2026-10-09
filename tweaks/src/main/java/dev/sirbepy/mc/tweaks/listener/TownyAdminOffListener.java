package dev.sirbepy.mc.tweaks.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Listed players never act as Towny admins, op or not (Joe, 2026-10-09: SirBepy stays op but must not break
 * blocks or open chests in towns he isn't in). Towny 0.103.2.0's isTownyAdmin is true for any op unless the
 * player has the "adminbypass" mode on, and a LuckPerms negation can't help because the op check runs first.
 * Towny keeps modes in memory only: it clears them on quit, so the mode is turned on again every join.
 */
public final class TownyAdminOffListener implements Listener {

    /**
     * Towny's join task runs a tick after the join and applies the towny_default_modes meta a tick after that,
     * clearing every other mode when that meta is set; a second later is safely past both.
     */
    static final long DELAY_TICKS = 20L;

    /** Towny's "adminbypass" mode for one player; only {@link TownyAdminBypass} touches Towny itself. */
    public interface BypassMode {
        boolean isOn(Player player);

        void turnOn(Player player);
    }

    private final Plugin plugin;
    private final Supplier<Set<UUID>> players;
    private final BypassMode mode;

    public TownyAdminOffListener(Plugin plugin, Supplier<Set<UUID>> players, BypassMode mode) {
        this.plugin = plugin;
        this.players = players;
        this.mode = mode;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!players.get().contains(player.getUniqueId())) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Towny's mode switch is a toggle, so turning it "on" while it's on would switch it off.
            if (player.isOnline() && !mode.isOn(player)) mode.turnOn(player);
        }, DELAY_TICKS);
    }
}
