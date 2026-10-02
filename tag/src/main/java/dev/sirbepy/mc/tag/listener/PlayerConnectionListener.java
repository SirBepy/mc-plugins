package dev.sirbepy.mc.tag.listener;

import dev.sirbepy.mc.tag.TagService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Applies pending admin actions and re-asserts IT state (prefix, head) across a reconnect. */
public final class PlayerConnectionListener implements Listener {

    private final TagService service;

    public PlayerConnectionListener(TagService service) {
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.onQuit(event.getPlayer());
    }
}
