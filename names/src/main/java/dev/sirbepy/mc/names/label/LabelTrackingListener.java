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
import org.bukkit.potion.PotionEffectType;

/** Spawns/destroys/refreshes the floating label on every event that can change who sees it. */
public final class LabelTrackingListener implements Listener {

    private final FloatingLabelManager labels;

    public LabelTrackingListener(FloatingLabelManager labels) {
        this.labels = labels;
    }

    @EventHandler
    public void onTrack(PlayerTrackEntityEvent event) {
        if (event.getEntity() instanceof Player target) {
            labels.onTrack(target, event.getPlayer());
        }
    }

    @EventHandler
    public void onUntrack(PlayerUntrackEntityEvent event) {
        if (event.getEntity() instanceof Player target) {
            labels.onUntrack(target, event.getPlayer());
        }
    }

    @EventHandler
    public void onSneakToggle(PlayerToggleSneakEvent event) {
        labels.refresh(event.getPlayer());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        labels.refresh(event.getPlayer());
    }

    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player player && event.getModifiedType() == PotionEffectType.INVISIBILITY) {
            labels.refresh(player);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        labels.refresh(event.getPlayer());
    }

    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent event) {
        labels.refresh(event.getPlayer());
    }
}
