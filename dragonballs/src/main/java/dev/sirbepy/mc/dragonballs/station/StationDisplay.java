package dev.sirbepy.mc.dragonballs.station;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.placed.FloatingBalls;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Floats the 1- to 7-star Overworld balls over the 7 spawn-market pedestals, spinning like real
 * placed balls so players learn what one looks like (Joe, 2026-10-08). They're displays only: no
 * hitbox, never in {@code BallRegistry}, non-persistent and respawned whenever their chunk loads.
 * A pedestal still holding the old decorative head block (marked {@code dragonballs:decor}) loses
 * it the first time its ball spawns.
 */
public final class StationDisplay implements Listener {

    static final String DECOR_TAG = "dragonballs.decor_ball";

    private final Supplier<StationConfig> stationConfig;
    private final FloatingBalls balls;
    private final BallItems items;
    private final NamespacedKey decorKey;
    private final Map<StationConfig.HeadLocation, ItemDisplay> spawned = new HashMap<>();

    public StationDisplay(JavaPlugin plugin, Supplier<StationConfig> stationConfig, FloatingBalls balls, BallItems items) {
        this.stationConfig = stationConfig;
        this.balls = balls;
        this.items = items;
        this.decorKey = new NamespacedKey(plugin, "decor");
    }

    /** Called once on enable; a pedestal whose world or chunk isn't loaded yet is picked up later by
     * {@link #onChunkLoad}. */
    public void placeAll() {
        List<StationConfig.HeadLocation> heads = stationConfig.get().displayHeads();
        for (int i = 0; i < heads.size(); i++) {
            placeIfNeeded(heads.get(i), i);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        String worldName = event.getWorld().getName();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        List<StationConfig.HeadLocation> heads = stationConfig.get().displayHeads();
        for (int i = 0; i < heads.size(); i++) {
            StationConfig.HeadLocation head = heads.get(i);
            if (head.world().equals(worldName) && (head.x() >> 4) == chunkX && (head.z() >> 4) == chunkZ) {
                placeIfNeeded(head, i);
            }
        }
    }

    /** The decor ball floating over one pedestal, if it's spawned (tests). */
    ItemDisplay displayAt(StationConfig.HeadLocation head) {
        return spawned.get(head);
    }

    private void placeIfNeeded(StationConfig.HeadLocation head, int index) {
        World world = Bukkit.getWorld(head.world());
        if (world == null || !world.isChunkLoaded(head.x() >> 4, head.z() >> 4)) {
            return;
        }
        ItemDisplay current = spawned.get(head);
        if (current != null && current.isValid()) {
            return;
        }
        Block block = world.getBlockAt(head.x(), head.y(), head.z());
        if (block.getState() instanceof Skull skull
                && skull.getPersistentDataContainer().has(decorKey, PersistentDataType.BOOLEAN)) {
            block.setType(Material.AIR);
        }
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        spawned.put(head, balls.spawnSpinning(center, items.createItem(BallSet.OVERWORLD, index % 7 + 1), DECOR_TAG));
    }
}
