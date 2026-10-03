package dev.sirbepy.mc.names.label;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

/**
 * Spawns/destroys/refreshes the floating label on every event that can change who sees it. State-change
 * events fire before the change applies (isSneaking() still reports the old value inside
 * PlayerToggleSneakEvent), so those refreshes run on the next tick.
 */
public final class LabelTrackingListener implements Listener {

    private final FloatingLabelManager labels;
    private final Plugin plugin;

    public LabelTrackingListener(FloatingLabelManager labels, Plugin plugin) {
        this.labels = labels;
        this.plugin = plugin;
    }

    private void refreshNextTick(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                labels.refresh(player);
            }
        });
    }

    @EventHandler
    public void onTrack(PlayerTrackEntityEvent event) {
        if (event.getEntity() instanceof Player target && labels.onTrack(target, event.getPlayer())) {
            Player viewer = event.getPlayer();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (target.isOnline() && viewer.isOnline()) {
                    labels.showTo(target, viewer);
                }
            });
        }
    }

    @EventHandler
    public void onUntrack(PlayerUntrackEntityEvent event) {
        if (event.getEntity() instanceof Player target) {
            labels.onUntrack(target, event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneakToggle(PlayerToggleSneakEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player player && event.getModifiedType() == PotionEffectType.INVISIBILITY) {
            refreshNextTick(player);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent event) {
        labels.refresh(event.getPlayer());
    }
}
