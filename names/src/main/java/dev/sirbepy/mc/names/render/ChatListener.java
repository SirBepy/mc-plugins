package dev.sirbepy.mc.names.render;

import dev.sirbepy.mc.names.config.NamesConfig;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Chat format, built from scratch rather than wrapping the default renderer (see Paper issue
 * #13574, where wrapping double-applies formatting for some clients/plugins).
 */
public final class ChatListener implements Listener {

    private final NamesConfig config;
    private final NameService nameService;

    public ChatListener(NamesConfig config, NameService nameService) {
        this.config = config;
        this.nameService = nameService;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        event.renderer(ChatRenderer.viewerUnaware(this::render));
    }

    private Component render(Player source, Component sourceDisplayName, Component message) {
        return config.renderChat(
                Placeholder.component("prefixes", nameService.prefixesComponent(source)),
                Placeholder.component("name", nameService.plainName(source)),
                Placeholder.component("message", message));
    }
}
