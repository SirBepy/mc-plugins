package dev.sirbepy.mc.dragonballs.summon;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Captures the active session's summoner chat as a wish instead of normal chat. Checked first and
 * cheapest: a non-summoner (or no active session at all) returns immediately and is never touched,
 * so this never affects anyone else's chat. Runs on the async chat thread, so the actual session
 * mutation, broadcast and logging is handed to the main thread via the scheduler.
 */
public final class SummonChatListener implements Listener {

    private final JavaPlugin plugin;
    private final SummonSessionManager sessions;

    public SummonChatListener(JavaPlugin plugin, SummonSessionManager sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!sessions.isSummoner(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> sessions.recordWish(player, plain));
    }
}
