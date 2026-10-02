package dev.sirbepy.mc.safechests.listener;

import dev.sirbepy.mc.safechests.SafeChestsConfig;
import dev.sirbepy.mc.safechests.bolt.BoltAccess;
import dev.sirbepy.mc.safechests.request.RequestManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.block.Block;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.InventoryHolder;
import org.popcraft.bolt.protection.Protection;

import java.util.function.Supplier;

/**
 * Bolt's own {@code InventoryListener} cancels {@link InventoryOpenEvent} silently when a non-owner opens
 * a locked chest (confirmed in bolt-bukkit {@code listeners/InventoryListener#onInventoryOpen}, which runs
 * at default/NORMAL priority). This listener runs at MONITOR so it only ever sees the final outcome, never
 * pre-empts Bolt's own decision, and double-checks with {@code canAccess} itself rather than trusting that
 * every cancellation it sees came from Bolt.
 */
public final class ChestDenialListener implements Listener {

    private final BoltAccess bolt;
    private final RequestManager requests;
    private final Supplier<SafeChestsConfig> config;

    public ChestDenialListener(BoltAccess bolt, RequestManager requests, Supplier<SafeChestsConfig> config) {
        this.bolt = bolt;
        this.requests = requests;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!event.isCancelled() || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        Block block = blockOf(event.getInventory().getHolder(false));
        if (block == null) {
            return;
        }
        Protection protection = bolt.api().findProtection(block);
        if (protection == null || bolt.canOpen(protection, player.getUniqueId())) {
            return;
        }
        requests.rememberDeniedBlock(player.getUniqueId(), block);
        SafeChestsConfig cfg = config.get();
        String ownerName = bolt.ownerDisplayName(protection.getOwner());
        if (requests.shouldNotifyDenial(player.getUniqueId(), cfg.debounce())) {
            cfg.messages().send(player, "denied", Placeholder.unparsed("owner", ownerName));
        }
        if (cfg.notifyOwnerOnAttempt()) {
            Player owner = player.getServer().getPlayer(protection.getOwner());
            if (owner != null) {
                cfg.messages().send(owner, "owner-alert-attempt",
                        Placeholder.unparsed("requester", player.getName()),
                        Placeholder.unparsed("x", String.valueOf(block.getX())),
                        Placeholder.unparsed("y", String.valueOf(block.getY())),
                        Placeholder.unparsed("z", String.valueOf(block.getZ())));
            }
        }
    }

    private Block blockOf(InventoryHolder holder) {
        return switch (holder) {
            case DoubleChest doubleChest -> doubleChest.getLocation().getBlock();
            case BlockInventoryHolder blockHolder -> blockHolder.getBlock();
            case null, default -> null;
        };
    }
}
