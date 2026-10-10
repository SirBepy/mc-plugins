package dev.sirbepy.mc.tweaks.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import dev.sirbepy.mc.core.Inventories;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loyalty tridents always come back to their owner.
 *
 * Void (Bedrock parity, Joe 2026-10-09): on 26.3 Loyalty only pulls a trident home once it has hit
 * something or landed (ThrownTrident.tick checks dealtDamage or noPhysics), so one falling into the
 * void never starts back and Entity.checkBelowWorld discards it 64 blocks under the world's min
 * height (cause OUT_OF_WORLD). The per-tick task marks a tracked trident as having dealt damage once
 * it drops below the min height, and vanilla Loyalty flies it back from there. One the void still
 * discards goes straight into its owner's inventory.
 *
 * Strays (Joe 2026-10-11): vanilla Loyalty only runs while the trident ticks, and only towards an
 * owner in its own world. Live's simulation-distance is 6, so a trident lodged ~160 blocks from every
 * player just sat there. A trident that hasn't come home {@link #GRACE_TICKS} after it entered the
 * world, and is out of entity-ticking range, in another world from its owner, or owed to an offline
 * owner, is removed and its item handed over; so is any that is about to unload with its chunk.
 * Vanilla still flies home every trident that can get there itself.
 *
 * Handing over is always the exact item stack with every component, removing the entity first in
 * the same tick so the trident never exists twice. An owner who is offline or dead gets it on join
 * or respawn, kept in {@link PendingTridents} across restarts. Only tridents a player threw with
 * pickup allowed are ever handed over: creative throws stayed in the inventory, and drowned tridents
 * have no player to go to. One picked up by someone else is theirs, as in vanilla.
 */
public final class LoyaltyTridentListener implements Listener {

    /** Vanilla starts the return 5 ticks after a trident lands, so 3 s leaves it the normal flight home. */
    static final int GRACE_TICKS = 60;

    private final Server server;
    private final PendingTridents pending;
    // Each tracked trident's tick count when it entered the world.
    private final Map<Trident, Long> tracked = new HashMap<>();
    private long ticks;

    public LoyaltyTridentListener(Server server, PendingTridents pending) {
        this.server = server;
        this.pending = pending;
    }

    /** Covers throws, tridents loaded with their chunk (after a restart too) and dimension changes alike. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Trident trident && trident.getLoyaltyLevel() > 0) tracked.put(trident, ticks);
    }

    public void tick() {
        ticks++;
        for (Trident trident : List.copyOf(tracked.keySet())) {
            if (!trident.isValid()) {
                tracked.remove(trident);
            } else if (trident.getLocation().getY() < trident.getWorld().getMinHeight()) {
                if (recall(trident)) tracked.remove(trident);
            } else if (ticks - tracked.get(trident) >= GRACE_TICKS) {
                checkStray(trident);
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

    private void checkStray(Trident trident) {
        if (!returnable(trident)) {
            tracked.remove(trident);
            return;
        }
        Player owner = owner(trident);
        // An online, living owner in the trident's world gets it from vanilla while it ticks.
        if (owner != null && !owner.isDead() && owner.getWorld().equals(trident.getWorld()) && trident.isTicking()) return;
        sendHome(trident);
    }

    /** Hands every tracked trident about to unload over now: unloaded, nothing would ever move it. */
    @EventHandler
    public void onUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Trident trident && tracked.remove(trident) != null && returnable(trident)) sendHome(trident);
        }
    }

    /**
     * A dead owner makes vanilla drop the trident as an item where it lies (isAcceptibleReturnOwner),
     * so their tridents are taken back before it next ticks and wait for the respawn instead.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        for (Trident trident : List.copyOf(tracked.keySet())) {
            if (id.equals(trident.getOwnerUniqueId()) && trident.isValid() && returnable(trident)) {
                tracked.remove(trident);
                ItemStack item = trident.getItemStack();
                trident.remove();
                pending.add(id, item);
            }
        }
    }

    /**
     * At shutdown, before players are disconnected and saved: a trident still out would otherwise
     * wait in its chunk until someone loads it again.
     */
    public void returnAll() {
        for (Trident trident : List.copyOf(tracked.keySet())) {
            if (trident.isValid() && returnable(trident)) sendHome(trident);
        }
        tracked.clear();
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        if (event.getCause() != EntityRemoveEvent.Cause.OUT_OF_WORLD) return;
        if (!(event.getEntity() instanceof Trident trident) || trident.getLoyaltyLevel() <= 0) return;
        tracked.remove(trident);
        if (returnable(trident)) deliver(trident.getOwnerUniqueId(), trident.getItemStack());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        deliverPending(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerPostRespawnEvent event) {
        deliverPending(event.getPlayer());
    }

    /**
     * Pickup allowed (a creative throw's item never left the inventory) and owned by a player, online
     * or one who has played here (a drowned's trident has a mob's UUID or none).
     */
    private boolean returnable(Trident trident) {
        if (trident.getPickupStatus() != AbstractArrow.PickupStatus.ALLOWED) return false;
        UUID ownerId = trident.getOwnerUniqueId();
        return ownerId != null && (server.getPlayer(ownerId) != null || server.getOfflinePlayer(ownerId).hasPlayedBefore());
    }

    private void sendHome(Trident trident) {
        tracked.remove(trident);
        ItemStack item = trident.getItemStack();
        trident.remove();
        deliver(trident.getOwnerUniqueId(), item);
    }

    private void deliver(UUID ownerId, ItemStack item) {
        Player owner = server.getPlayer(ownerId);
        if (owner != null && !owner.isDead()) {
            give(owner, item);
        } else {
            pending.add(ownerId, item);
        }
    }

    private void deliverPending(Player player) {
        if (player.isDead()) return;
        pending.take(player.getUniqueId()).forEach(item -> give(player, item));
    }

    private Player owner(Trident trident) {
        UUID ownerId = trident.getOwnerUniqueId();
        return ownerId == null ? null : server.getPlayer(ownerId);
    }

    private static void give(Player player, ItemStack item) {
        Inventories.giveOrDrop(player, item);
        player.playSound(player, Sound.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 1f, 1f);
    }
}
