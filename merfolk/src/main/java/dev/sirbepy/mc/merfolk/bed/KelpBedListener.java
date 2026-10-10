package dev.sirbepy.mc.merfolk.bed;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import dev.sirbepy.mc.merfolk.TownCheck;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Placing, using, picking up and protecting Kelp Beds. A bed goes on the sea floor into two water
 * source blocks in a row (foot where the player clicks, head the way they face) and keeps that water:
 * no block can be placed into it, buckets and sponges leave it alone. Right-clicking one makes it the
 * player's respawn point, kept by the plugin because vanilla won't respawn anyone in water.
 */
public final class KelpBedListener implements Listener {

    private final KelpBeds beds;
    private final TownCheck towns;

    public KelpBedListener(KelpBeds beds, TownCheck towns) {
        this.beds = beds;
        this.towns = towns;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(KelpBeds.RECIPE);
    }

    // Not ignoreCancelled: a right-click aimed at the floor through water can arrive pre-cancelled.
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() == null) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getHand());
        if (!KelpBeds.isItem(held)) return;
        event.setCancelled(true);
        Block foot = event.getClickedBlock().getRelative(event.getBlockFace());
        BlockFace facing = horizontal(player.getLocation().getYaw());
        Block head = foot.getRelative(facing);
        String problem = problem(player, foot, head);
        if (problem != null) {
            player.sendActionBar(Component.text(problem, NamedTextColor.RED));
            return;
        }
        beds.add(foot, facing);
        if (player.getGameMode() != GameMode.CREATIVE) held.subtract();
        foot.getWorld().playSound(foot.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_WET_GRASS_PLACE, 1f, 0.8f);
    }

    private String problem(Player player, Block foot, Block head) {
        if (!isWaterSource(foot) || !isWaterSource(head)
                || !foot.getRelative(BlockFace.DOWN).getType().isSolid() || !head.getRelative(BlockFace.DOWN).getType().isSolid()) {
            return "Place it on the sea floor.";
        }
        if (beds.at(foot) != null || beds.at(head) != null) return "Another Kelp Bed is already there.";
        if (!towns.mayBuild(player, foot.getLocation()) || !towns.mayBuild(player, head.getLocation())) {
            return "You can't build here.";
        }
        return null;
    }

    private static final BlockFace[] BY_YAW = {BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST};

    /** The horizontal direction a Bukkit yaw looks along (0 = south, 90 = west), like a placed bed. */
    static BlockFace horizontal(float yaw) {
        return BY_YAW[Math.floorMod(Math.round(yaw / 90f), 4)];
    }

    static boolean isWaterSource(Block block) {
        return block.getType() == Material.WATER && block.getBlockData() instanceof Levelled water && water.getLevel() == 0;
    }

    /**
     * Hitting either half picks the bed up, under the same claim rule as breaking a block there.
     * Not ignoreCancelled: Paper hands an attack on an Interaction over already cancelled
     * (willAttack=false, logged on the 26.2 e2e server), since nothing can be damaged.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(PrePlayerAttackEntityEvent event) {
        String id = KelpBeds.partOf(event.getAttacked());
        if (id == null) return;
        event.setCancelled(true);
        KelpBed bed = beds.byId(id);
        if (bed == null) {
            event.getAttacked().remove();
            return;
        }
        Player player = event.getPlayer();
        if (!towns.mayDestroy(player, event.getAttacked().getLocation())) {
            player.sendActionBar(Component.text("You can't break that here.", NamedTextColor.RED));
            return;
        }
        beds.remove(bed);
        var centre = bed.centre(player.getWorld()).add(0, 0.3, 0);
        if (player.getGameMode() != GameMode.CREATIVE) player.getWorld().dropItemNaturally(centre, KelpBeds.item(1));
        player.getWorld().playSound(centre, Sound.BLOCK_WET_GRASS_BREAK, 1f, 0.8f);
    }

    /** Right-clicking either half sets the respawn point there, like a vanilla bed (no sleeping). */
    // Not ignoreCancelled, same as onHit: an Interaction has nothing to interact with by default.
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEntityEvent event) {
        String id = KelpBeds.partOf(event.getRightClicked());
        if (id == null) return;
        event.setCancelled(true);
        KelpBed bed = beds.byId(id);
        if (bed == null || event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (towns.inOtherTown(player, event.getRightClicked().getLocation())) {
            player.sendActionBar(Component.text("You can't respawn in another town.", NamedTextColor.RED));
            return;
        }
        if (!beds.setSpawn(player.getUniqueId(), bed)) return;
        // Otherwise vanilla would still resolve the old bed or anchor at death (spending an anchor
        // charge) before onRespawn moves the player. onSpawnChange ignores plugin-caused changes.
        player.setRespawnLocation(null);
        player.sendMessage(Component.translatable("block.minecraft.set_spawn"));
    }

    /** Dying, or leaving the End through its portal, lands the player on their Kelp Bed. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.getRespawnReason() == PlayerRespawnEvent.RespawnReason.PLUGIN) return;
        Location at = respawnAt(beds.spawnOf(event.getPlayer().getUniqueId()));
        if (at != null) event.setRespawnLocation(at);
    }

    /** Where a player respawns on {@code bed}: its middle, on the sea floor. Null if its world is gone. */
    static Location respawnAt(KelpBed bed) {
        if (bed == null) return null;
        World world = Bukkit.getWorld(bed.world());
        return world == null ? null : bed.centre(world);
    }

    /** A vanilla bed, an anchor or /spawnpoint replaces the Kelp Bed, so the newest choice wins. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnChange(PlayerSetSpawnEvent event) {
        switch (event.getCause()) {
            case BED, RESPAWN_ANCHOR, COMMAND -> beds.clearSpawn(event.getPlayer().getUniqueId());
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (beds.at(event.getBlockPlaced()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent event) {
        if (beds.at(event.getBlock()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSponge(SpongeAbsorbEvent event) {
        event.getBlocks().removeIf(state -> beds.at(state.getBlock()) != null);
    }
}
