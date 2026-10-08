package dev.sirbepy.mc.dragonballs.radar;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Joining, respawning and changing worlds each reset the client's compass target to the world
 * spawn, behind {@link RadarTask}'s back: without this a radar held through a death kept pointing
 * at spawn until its ball moved (SirBepy read it as a ball under spawn, 2026-10-08).
 */
public final class RadarResyncListener implements Listener {

    private final RadarTask task;

    public RadarResyncListener(RadarTask task) {
        this.task = task;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        task.forgetTarget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        task.forgetTarget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        task.forgetTarget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        task.forgetTarget(event.getPlayer().getUniqueId());
    }
}
