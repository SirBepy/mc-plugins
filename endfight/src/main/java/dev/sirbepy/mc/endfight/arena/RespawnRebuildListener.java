package dev.sirbepy.mc.endfight.arena;

import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;

/**
 * When a fresh Ender Dragon spawns in the End, vanilla has just regenerated every end-spike pillar
 * ({@code EndSpikeFeature} runs again as part of the respawn sequence, clearing each pillar's own
 * square footprint above y 65 and rebuilding its obsidian cylinder from scratch, see
 * {@link Geometry}), eating the parts of ours closest to each pillar. Schedule a rescan +
 * rebuild a couple seconds later so the structures reappear on the regenerated pillars.
 */
public final class RespawnRebuildListener implements Listener {

    private static final long REBUILD_DELAY_TICKS = 40L;

    private final Plugin plugin;
    private final Runnable rebuild;

    public RespawnRebuildListener(Plugin plugin, Runnable rebuild) {
        this.plugin = plugin;
        this.rebuild = rebuild;
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.ENDER_DRAGON) {
            return;
        }
        if (event.getEntity().getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, rebuild, REBUILD_DELAY_TICKS);
    }
}
