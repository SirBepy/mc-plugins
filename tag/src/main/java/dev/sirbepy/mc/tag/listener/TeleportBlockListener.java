package dev.sirbepy.mc.tag.listener;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.tag.TagService;
import dev.sirbepy.mc.tag.config.TeleportBlockConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.math.BigDecimal;

/**
 * Cancels command and plugin teleports (Essentials, HuskHomes, /tpa accepts, Haru's home item) for
 * players pinned by a nearby IT. Pearls, chorus fruit and portals have their own causes and pass.
 */
public final class TeleportBlockListener implements Listener {

    private final TagService service;

    public TeleportBlockListener(TagService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        TeleportCause cause = event.getCause();
        if (cause != TeleportCause.COMMAND && cause != TeleportCause.PLUGIN) {
            return;
        }
        Player player = event.getPlayer();
        Player it = service.itBlockingTeleport(player, event.getFrom());
        if (it == null) {
            return;
        }
        event.setCancelled(true);
        TeleportBlockConfig config = service.config().teleportBlock;
        if (!config.message.isBlank()) {
            player.sendMessage(Messages.mini().deserialize(config.message,
                    Placeholder.unparsed("it", it.getName()),
                    Placeholder.unparsed("radius", BigDecimal.valueOf(config.radius).stripTrailingZeros().toPlainString())));
        }
    }
}
