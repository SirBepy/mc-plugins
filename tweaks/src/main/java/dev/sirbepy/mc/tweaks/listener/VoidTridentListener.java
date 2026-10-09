package dev.sirbepy.mc.tweaks.listener;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bedrock parity (Joe, 2026-10-09): a Loyalty trident thrown into the void comes back to its thrower
 * instead of being deleted. Tridents without Loyalty keep vanilla behaviour.
 *
 * On 26.3 Loyalty only pulls a trident home once it has hit something or landed (ThrownTrident.tick
 * checks dealtDamage or noPhysics), so one falling into the void never starts back and
 * Entity.checkBelowWorld discards it 64 blocks under the world's min height (cause OUT_OF_WORLD).
 * The per-tick task marks a tracked trident as having dealt damage once it drops below the min
 * height, and vanilla Loyalty flies it back from there. A Loyalty trident that is still discarded
 * by the void (thrown before the plugin enabled, owner in another world or dead) goes straight
 * into its owner's inventory, the exact item stack with every component.
 */
public final class VoidTridentListener implements Listener {

    private final Server server;
    private final Set<Trident> thrown = new HashSet<>();
    // Tridents whose owner was dead or offline when the void took them; in memory, so a restart
    // before the owner respawns or rejoins loses them, as vanilla always did.
    private final Map<UUID, List<ItemStack>> pending = new HashMap<>();

    public VoidTridentListener(Server server) {
        this.server = server;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Trident trident && trident.getLoyaltyLevel() > 0) thrown.add(trident);
    }

    public void tick() {
        for (Iterator<Trident> it = thrown.iterator(); it.hasNext(); ) {
            Trident trident = it.next();
            if (!trident.isValid()) {
                it.remove();
            } else if (trident.getLocation().getY() < trident.getWorld().getMinHeight() && recall(trident)) {
                it.remove();
            }
        }
    }

    /**
     * Starts Loyalty's return when the owner can take it, i.e. what isAcceptibleReturnOwner and
     * getOwner accept: alive, not a spectator, in the trident's world. Otherwise it keeps falling
     * and {@link #onRemove} hands the item over once the void discards it.
     */
    private boolean recall(Trident trident) {
        Player owner = owner(trident);
        if (owner == null || owner.isDead() || owner.getGameMode() == GameMode.SPECTATOR
                || !owner.getWorld().equals(trident.getWorld())) {
            return false;
        }
        // Loyalty adds 0.05 * level blocks/tick towards the owner each tick; a trident still
        // falling at full speed would pass the discard height before it turned around.
        trident.setVelocity(new Vector());
        trident.setHasDealtDamage(true);
        return true;
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        if (event.getCause() != EntityRemoveEvent.Cause.OUT_OF_WORLD) return;
        if (!(event.getEntity() instanceof Trident trident) || trident.getLoyaltyLevel() <= 0) return;
        thrown.remove(trident);
        // Creative throws keep the trident in the inventory (CREATIVE_ONLY), so returning one would duplicate it.
        if (trident.getPickupStatus() != AbstractArrow.PickupStatus.ALLOWED) return;
        UUID ownerId = trident.getOwnerUniqueId();
        if (ownerId == null) return;
        ItemStack item = trident.getItemStack();
        Player owner = server.getPlayer(ownerId);
        if (owner != null && !owner.isDead()) {
            give(owner, item);
        } else {
            pending.computeIfAbsent(ownerId, id -> new ArrayList<>()).add(item);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        deliverPending(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerPostRespawnEvent event) {
        deliverPending(event.getPlayer());
    }

    private void deliverPending(Player player) {
        if (player.isDead()) return;
        List<ItemStack> items = pending.remove(player.getUniqueId());
        if (items != null) items.forEach(item -> give(player, item));
    }

    private Player owner(Trident trident) {
        UUID ownerId = trident.getOwnerUniqueId();
        return ownerId == null ? null : server.getPlayer(ownerId);
    }

    private static void give(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }
        player.playSound(player, Sound.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 1f, 1f);
    }
}
