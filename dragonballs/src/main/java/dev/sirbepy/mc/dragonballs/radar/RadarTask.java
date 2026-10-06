package dev.sirbepy.mc.dragonballs.radar;

import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs every 20 ticks (never per-tick, never touching NBT on every pass): points each online
 * radar-holder's compass at the nearest awake ball in their own world, or shows the no-signal
 * screen when there is none. {@code Player#setCompassTarget} is only called when the target block
 * actually changed, and the item's model string is only rewritten when active/idle actually flips -
 * both to avoid redundant packets/animation replays on a quarter-second-granularity hunt.
 */
public final class RadarTask implements Runnable {

    public static final long PERIOD_TICKS = 20L;

    private final BallRegistry registry;
    private final RadarItems items;

    // Players currently tracked as "holding a radar", so a player who stops holding one gets their
    // compass target reset to world spawn exactly once, then is dropped from both maps.
    private final Set<UUID> tracked = new HashSet<>();
    private final Map<UUID, BlockKey> lastTarget = new HashMap<>();

    public RadarTask(BallRegistry registry, RadarItems items) {
        this.registry = registry;
        this.items = items;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            tick(player);
        }
    }

    private void tick(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack main = inventory.getItemInMainHand();
        ItemStack off = inventory.getItemInOffHand();

        ItemStack radar;
        EquipmentSlot slot;
        if (looksLikeRadar(main)) {
            radar = main;
            slot = EquipmentSlot.HAND;
        } else if (looksLikeRadar(off)) {
            radar = off;
            slot = EquipmentSlot.OFF_HAND;
        } else {
            radar = null;
            slot = null;
        }

        UUID id = player.getUniqueId();
        if (radar == null) {
            if (tracked.remove(id)) {
                lastTarget.remove(id);
                player.setCompassTarget(player.getWorld().getSpawnLocation());
            }
            return;
        }
        tracked.add(id);

        Location nearest = nearestTarget(player);
        boolean active = nearest != null;
        if (active != items.isShowingActive(radar)) {
            items.setActive(radar, active);
            if (slot == EquipmentSlot.HAND) {
                inventory.setItemInMainHand(radar);
            } else {
                inventory.setItemInOffHand(radar);
            }
        }
        if (active) {
            BlockKey block = BlockKey.of(nearest);
            if (!block.equals(lastTarget.get(id))) {
                player.setCompassTarget(nearest);
                lastTarget.put(id, block);
            }
        }
    }

    // Cheap material check first; the PDC lookup only runs for an actual compass.
    private boolean looksLikeRadar(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && items.isRadar(item);
    }

    /**
     * {@code activeTargets()} returns bare locations, not ball identities (registry/ is off-limits
     * for this phase), so a ball the player is carrying themselves is excluded by matching the
     * player's own current block rather than by ball id - an exact-same-block coincidence with some
     * other ball is the only false-negative this can produce, and is vanishingly rare.
     */
    private Location nearestTarget(Player player) {
        boolean holdsABallThemself = registry.held().stream()
                .anyMatch(view -> player.getUniqueId().equals(view.holderId()));
        BlockKey self = BlockKey.of(player.getLocation());
        World world = player.getWorld();

        Location nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (Location candidate : registry.activeTargets()) {
            if (candidate.getWorld() == null || !candidate.getWorld().equals(world)) {
                continue;
            }
            if (holdsABallThemself && BlockKey.of(candidate).equals(self)) {
                continue;
            }
            double distanceSquared = candidate.distanceSquared(player.getLocation());
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /** Block-coordinate identity, so two {@code getLocation()} samples of the same spot (different
     * pitch/yaw, sub-block jitter) never look like a "target changed" event. */
    private record BlockKey(String world, int x, int y, int z) {
        static BlockKey of(Location location) {
            return new BlockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}
