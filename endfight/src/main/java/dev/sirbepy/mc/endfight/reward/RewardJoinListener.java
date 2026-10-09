package dev.sirbepy.mc.endfight.reward;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/** On join, a one-second-later delivery of anything {@link RewardService} owes this player from a
 * fight they missed while offline. */
public final class RewardJoinListener implements Listener {

    private static final long DELIVER_DELAY_TICKS = 20L;

    private final Plugin plugin;
    private final RewardService rewards;

    public RewardJoinListener(Plugin plugin, RewardService rewards) {
        this.plugin = plugin;
        this.rewards = rewards;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> rewards.deliverPending(player), DELIVER_DELAY_TICKS);
    }
}
