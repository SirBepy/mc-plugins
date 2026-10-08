package dev.sirbepy.mc.guild.hall;

import dev.sirbepy.mc.guild.data.GuildStore;
import dev.sirbepy.mc.guild.data.Place;
import dev.sirbepy.mc.guild.listener.Responsible;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;

import java.util.Set;

/**
 * Keeps the Guild Hall as ImMijo built it, once an op has set its box ({@code /guildadmin hall guard});
 * with no box set it does nothing. Inside the box nobody but an op breaks or places blocks, empties or
 * fills buckets, takes the quest board's notices or the stands' armour, edits a sign, blows it up,
 * burns it or toggles its shutters and candles. Doors, the bell, the grindstone and the staff stay usable.
 */
public final class HallGuard implements Listener {

    /** Blocks whose right-click would change the hall's look or let items be stored or taken. */
    static final Set<Material> LOCKED = Set.of(Material.FLOWER_POT, Material.DECORATED_POT, Material.CHISELED_BOOKSHELF,
            Material.LECTERN, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.CANDLE, Material.CAKE,
            Material.REDSTONE_WIRE);

    private final GuildStore store;

    public HallGuard(GuildStore store) {
        this.store = store;
    }

    private boolean inHall(Location at) {
        Place.Box box = store.guard();
        return box != null && box.contains(at);
    }

    private boolean guarded(Player player, Location at) {
        return (player == null || !player.isOp()) && inHall(at);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (guarded(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (guarded(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (guarded(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (guarded(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Material type = block.getType();
        boolean locked = LOCKED.contains(type) || Tag.CANDLES.isTagged(type) || Tag.TRAPDOORS.isTagged(type)
                || type.name().startsWith("POTTED_");
        if (locked && guarded(event.getPlayer(), block.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSign(PlayerOpenSignEvent event) {
        if (guarded(event.getPlayer(), event.getSign().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLectern(PlayerTakeLecternBookEvent event) {
        if (guarded(event.getPlayer(), event.getLectern().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFrame(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Hanging && guarded(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (guarded(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (inHall(event.getEntity().getLocation())) {
            Player player = event instanceof org.bukkit.event.hanging.HangingBreakByEntityEvent byEntity
                    ? Responsible.player(byEntity.getRemover()) : null;
            if (player == null || !player.isOp()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDecorDamage(EntityDamageByEntityEvent event) {
        Entity hit = event.getEntity();
        if ((hit instanceof ArmorStand || hit instanceof Hanging) && guarded(Responsible.player(event.getDamager()), hit.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> inHall(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> inHall(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (inHall(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (guarded(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Endermen lifting blocks, ravagers trampling, falling blocks landing. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player) && inHall(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
}
