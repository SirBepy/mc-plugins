package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The drop/pickup half of a ball's lifecycle: DROPPED while it's a free-standing item, HELD once a
 * player has it. Also the one place that rejects a stray duplicate ball item (one the owning set's
 * registry doesn't actually have DROPPED under this entity), per the dupe-protection invariant. The
 * "found" broadcast names the set for Nether/End; the overworld wording is unchanged.
 */
public final class BallPickupListener implements Listener {

    private static final long SUPPRESS_BROADCAST_MILLIS = 30_000;

    private final BallSets sets;
    private final Supplier<DragonBallsConfig> config;
    // Tracks, per (set, star), who last dropped it and when: a player re-picking up their own drop
    // within 30s doesn't re-announce it (so bouncing a ball between hand and ground doesn't spam chat).
    private final Map<BallKey, UUID> lastDropper = new HashMap<>();
    private final Map<BallKey, Long> lastDroppedAt = new HashMap<>();

    public BallPickupListener(BallSets sets, Supplier<DragonBallsConfig> config) {
        this.sets = sets;
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        sets.items().starOf(stack).ifPresent(star -> {
            BallRegistry registry = sets.registryFor(stack).orElseThrow();
            boolean stone = sets.items().isStoneItem(stack);
            registry.markDropped(star, event.getItemDrop(), stone);
            BallKey key = new BallKey(registry.set(), star);
            lastDropper.put(key, event.getPlayer().getUniqueId());
            lastDroppedAt.put(key, System.currentTimeMillis());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Item itemEntity = event.getItem();
        ItemStack stack = itemEntity.getItemStack();
        sets.items().starOf(stack).ifPresent(star -> {
            BallRegistry registry = sets.registryFor(stack).orElseThrow();
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
            boolean stone = sets.items().isStoneItem(stack);
            registry.markHeld(star, player, stone);
            if (!recentlySelfDropped(registry.set(), star, player)) {
                config.get().messages().send(player.getServer(), foundMessageKey(registry.set()),
                        Placeholder.unparsed("player", player.getName()),
                        Placeholder.unparsed("star", String.valueOf(star)));
            }
        });
    }

    private static String foundMessageKey(BallSet set) {
        return switch (set) {
            case OVERWORLD -> "found";
            case NETHER -> "found-nether";
            case END -> "found-end";
        };
    }

    private boolean recentlySelfDropped(BallSet set, int star, Player picker) {
        BallKey key = new BallKey(set, star);
        UUID dropper = lastDropper.get(key);
        Long droppedAt = lastDroppedAt.get(key);
        return dropper != null && dropper.equals(picker.getUniqueId()) && droppedAt != null
                && System.currentTimeMillis() - droppedAt < SUPPRESS_BROADCAST_MILLIS;
    }

    private record BallKey(BallSet set, int star) {
    }
}
