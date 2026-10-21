package dev.sirbepy.mc.minibosses.relic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Fires a relic's {@link RelicActive} on sneak + the swap-hands key while the relic sits in the
 * off hand. The swap is always cancelled for such a press, so the relic stays put whether the
 * ability fired or was still recharging. A Guardian whose relic has no registered active (the
 * Ashen Core and Hollow Crown for now) swaps hands as usual. Cooldowns are per player and per
 * ability, in memory only: a restart clears them.
 */
public final class RelicActiveListener implements Listener {

    private record CooldownKey(UUID player, String guardianId) {
    }

    private final Relics relics;
    private final RelicRanks ranks;
    private final LongSupplier now;
    private final Map<String, RelicActive> actives = new HashMap<>();
    private final Map<CooldownKey, Long> readyAt = new HashMap<>();

    public RelicActiveListener(Relics relics, RelicRanks ranks, LongSupplier now, List<RelicActive> actives) {
        this.relics = relics;
        this.ranks = ranks;
        this.now = now;
        actives.forEach(active -> this.actives.put(active.guardianId(), active));
    }

    // HIGH so a plugin that vetoes swaps at NORMAL (carry, while carrying) has already cancelled
    // the event and ignoreCancelled skips us.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        // The event fires before the swap, so the live off hand is still the relic's slot.
        ItemStack offHand = player.getInventory().getItemInOffHand();
        Optional<RelicActive> active = relics.guardianOf(offHand).map(actives::get);
        if (active.isEmpty()) {
            return;
        }
        event.setCancelled(true);

        CooldownKey key = new CooldownKey(player.getUniqueId(), active.get().guardianId());
        long time = now.getAsLong();
        long ready = readyAt.getOrDefault(key, 0L);
        if (time < ready) {
            long seconds = (ready - time + 999) / 1000;
            player.sendMessage(Component.text(active.get().name() + " is recharging: " + seconds + "s left.",
                    NamedTextColor.GRAY));
            return;
        }
        int rank = ranks.rankOf(offHand);
        readyAt.put(key, time + active.get().cooldownSeconds(rank) * 1000L);
        active.get().fire(player, rank);
    }
}
