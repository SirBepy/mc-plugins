package dev.sirbepy.mc.dragonballs.radar;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs every 20 ticks (never per-tick, never touching NBT on every pass): points each online
 * radar-holder's compass at the ball their radar tracks (the one they picked with sneak +
 * right-click, else the nearest awake ball of THEIR radar's own set), while they're standing in
 * that set's home world; anywhere else (including a different set's home world) shows the
 * no-signal screen, same as when there's nothing left to find. {@code Player#setCompassTarget} is
 * only called when the target block actually changed, and the item is only rewritten when its
 * reading (ball, distance band, picked or not) actually changed - both to avoid redundant
 * packets/animation replays on a second-granularity hunt.
 */
public final class RadarTask implements Runnable {

    public static final long PERIOD_TICKS = 20L;

    private final BallSets sets;
    private final RadarItems items;
    private final RadarTracking tracking;
    private final Supplier<DragonBallsConfig> config;

    // Players currently holding a radar, so a player who stops holding one gets their
    // compass target reset to world spawn exactly once, then is dropped from both maps.
    private final Set<UUID> holding = new HashSet<>();
    private final Map<UUID, BlockKey> lastTarget = new HashMap<>();

    public RadarTask(BallSets sets, RadarItems items, RadarTracking tracking, Supplier<DragonBallsConfig> config) {
        this.sets = sets;
        this.items = items;
        this.tracking = tracking;
        this.config = config;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    /** One player's update, also run straight after a sneak + right-click so the new pick shows at once. */
    public void refresh(Player player) {
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
            if (holding.remove(id)) {
                lastTarget.remove(id);
                player.setCompassTarget(player.getWorld().getSpawnLocation());
            }
            return;
        }
        holding.add(id);

        BallSet set = items.setOf(radar);
        Map<Integer, Location> targets = visibleTargets(player, set);
        Integer picked = tracking.picked(id, set);
        if (picked != null && !targets.containsKey(picked)) {
            // The picked ball went stone, flew off or into this player's own inventory.
            tracking.clear(id, set);
            picked = null;
        }
        Integer star = picked != null ? picked : nearest(player, targets);
        Location target = star == null ? null : targets.get(star);
        RadarReading reading = target == null ? null : new RadarReading(star,
                RadarBand.of(target.distance(player.getLocation()), set), picked != null);

        if (items.show(radar, reading)) {
            if (slot == EquipmentSlot.HAND) {
                inventory.setItemInMainHand(radar);
            } else {
                inventory.setItemInOffHand(radar);
            }
        }
        if (target != null) {
            BlockKey block = BlockKey.of(target);
            if (!block.equals(lastTarget.get(id))) {
                player.setCompassTarget(target);
                lastTarget.put(id, block);
            }
        }
    }

    /**
     * Makes the next pass re-send {@code player}'s compass target even if the ball hasn't moved.
     * Called on join, respawn and world change: the server sends the client the world spawn as its
     * compass target then, so an unchanged cached target would leave the radar pointing at spawn.
     */
    public void forgetTarget(UUID player) {
        lastTarget.remove(player);
    }

    /** The stars {@code player}'s {@code set} radar can see right now, nearest first (empty outside the set's home world). */
    public List<Integer> starsNearestFirst(Player player, BallSet set) {
        Map<Integer, Location> targets = visibleTargets(player, set);
        List<Integer> stars = new ArrayList<>(targets.keySet());
        stars.sort(Comparator.comparingDouble(star -> targets.get(star).distanceSquared(player.getLocation())));
        return stars;
    }

    // Cheap material check first; the PDC lookup only runs for an actual compass.
    private boolean looksLikeRadar(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && items.isRadar(item);
    }

    /**
     * Awake balls of {@code set} in the player's own world, minus any ball the player carries
     * themself, and nothing at all outside the set's home world. Scoped to {@code set}'s own
     * registry only: a Nether radar never sees an Overworld ball, even one sitting at the same
     * coordinates in a different world.
     */
    private Map<Integer, Location> visibleTargets(Player player, BallSet set) {
        World home = set.world(config.get());
        if (home == null || !home.equals(player.getWorld())) {
            return Map.of();
        }
        BallRegistry registry = sets.registry(set);
        Map<Integer, Location> targets = new TreeMap<>();
        registry.activeTargetsByStar().forEach((star, location) -> {
            if (home.equals(location.getWorld()) && !registry.isHeldBy(star, player.getUniqueId())) {
                targets.put(star, location);
            }
        });
        return targets;
    }

    private static Integer nearest(Player player, Map<Integer, Location> targets) {
        Integer nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (Map.Entry<Integer, Location> entry : targets.entrySet()) {
            double distanceSquared = entry.getValue().distanceSquared(player.getLocation());
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = entry.getKey();
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
