package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The drop/pickup half of a ball's lifecycle: DROPPED while it's a free-standing item, HELD once a
 * player has it. Also the one place that rejects a stray duplicate ball item (one the registry
 * doesn't actually have DROPPED under this entity), per the dupe-protection invariant.
 */
public final class BallPickupListener implements Listener {

    private static final long SUPPRESS_BROADCAST_MILLIS = 30_000;

    private final BallRegistry registry;
    private final Supplier<DragonBallsConfig> config;
    // Tracks, per star, who last dropped it and when: a player re-picking up their own drop within
    // 30s doesn't re-announce it (so bouncing a ball between hand and ground doesn't spam chat).
    private final Map<Integer, UUID> lastDropper = new HashMap<>();
    private final Map<Integer, Long> lastDroppedAt = new HashMap<>();

    public BallPickupListener(BallRegistry registry, Supplier<DragonBallsConfig> config) {
        this.registry = registry;
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        registry.starOf(event.getItemDrop().getItemStack()).ifPresent(star -> {
            boolean stone = registry.items().isStoneItem(event.getItemDrop().getItemStack());
            registry.markDropped(star, event.getItemDrop(), stone);
            lastDropper.put(star, event.getPlayer().getUniqueId());
            lastDroppedAt.put(star, System.currentTimeMillis());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Item itemEntity = event.getItem();
        registry.starOf(itemEntity.getItemStack()).ifPresent(star -> {
            if (!(event.getEntity() instanceof Player player)) {
                // Hoppers, allays, foxes, minecart hoppers: balls never leave player hands this way.
                event.setCancelled(true);
                return;
            }
            if (!registry.isDroppedAs(star, itemEntity.getUniqueId())) {
                // Registry disagrees about where this star actually is: a stray dupe (creative,
                // duplication glitch). Deny the pickup and delete the impostor rather than hand it out.
                event.setCancelled(true);
                itemEntity.remove();
                return;
            }
            boolean stone = registry.items().isStoneItem(itemEntity.getItemStack());
            registry.markHeld(star, player, stone);
            if (!recentlySelfDropped(star, player)) {
                config.get().messages().send(player.getServer(), "found",
                        Placeholder.unparsed("player", player.getName()),
                        Placeholder.unparsed("star", String.valueOf(star)));
            }
        });
    }

    private boolean recentlySelfDropped(int star, Player picker) {
        UUID dropper = lastDropper.get(star);
        Long droppedAt = lastDroppedAt.get(star);
        return dropper != null && dropper.equals(picker.getUniqueId()) && droppedAt != null
                && System.currentTimeMillis() - droppedAt < SUPPRESS_BROADCAST_MILLIS;
    }
}
