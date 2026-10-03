package dev.sirbepy.mc.tag.listener;

import dev.sirbepy.mc.tag.TagService;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Low-frequency activity signals that AfkSamplingTask's position/facing sampling wouldn't catch
 * on its own (e.g. chatting or clicking an inventory while standing still). {@link
 * TagService#markActive} writes into a {@code ConcurrentHashMap}-backed tracker, so firing from
 * {@link AsyncChatEvent}'s async thread is safe without hopping to the main thread.
 */
public final class AfkActivityListener implements Listener {

    private final TagService service;

    public AfkActivityListener(TagService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        service.markActive(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        service.markActive(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        service.markActive(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        service.markActive(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        service.markActive(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            service.markActive(player.getUniqueId());
        }
    }
}
