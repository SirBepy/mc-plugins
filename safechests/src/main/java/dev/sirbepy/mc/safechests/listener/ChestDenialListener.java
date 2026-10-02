package dev.sirbepy.mc.safechests.listener;

import dev.sirbepy.mc.safechests.SafeChestsConfig;
import dev.sirbepy.mc.safechests.bolt.BoltAccess;
import dev.sirbepy.mc.safechests.request.RequestManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.InventoryHolder;
import org.popcraft.bolt.protection.Protection;

import java.util.function.Supplier;

/**
 * A right-click on a locked chest is normally refused by Bolt's {@code BlockListener#onPlayerInteract}
 * (cancels the {@link PlayerInteractEvent}, so no inventory ever opens); {@code InventoryListener} cancels
 * {@link InventoryOpenEvent} for the remaining paths. Both handlers run at MONITOR so they only see Bolt's
 * final decision, and they re-check {@code canAccess} rather than trusting that every cancellation came
 * from Bolt.
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
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        boolean denied = event.useInteractedBlock() == Event.Result.DENY;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || !denied || block == null || !(block.getState(false) instanceof Container)) {
            return;
        }
        handleDenied(event.getPlayer(), block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!event.isCancelled() || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        Block block = blockOf(event.getInventory().getHolder(false));
        if (block != null) {
            handleDenied(player, block);
        }
    }

    private void handleDenied(Player player, Block block) {
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
