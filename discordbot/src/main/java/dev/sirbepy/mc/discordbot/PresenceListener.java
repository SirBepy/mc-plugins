package dev.sirbepy.mc.discordbot;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Posts a join/leave line to Discord, counting everyone connected (vanished or not). */
final class PresenceListener implements Listener {

    private final DiscordNotifier notifier;
    private final String channelId;

    PresenceListener(DiscordNotifier notifier, String channelId) {
        this.notifier = notifier;
        this.channelId = channelId;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // The joining player is already in getOnlinePlayers() by the time this event fires.
        int online = event.getPlayer().getServer().getOnlinePlayers().size();
        notifier.send(channelId, PresenceMessages.join(event.getPlayer().getName(), online));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // PlayerQuitEvent fires while the leaving player is still in getOnlinePlayers(), so the
        // post-leave count is one less than what the server reports here.
        int online = Math.max(0, event.getPlayer().getServer().getOnlinePlayers().size() - 1);
        notifier.send(channelId, PresenceMessages.leave(event.getPlayer().getName(), online));
    }
}
