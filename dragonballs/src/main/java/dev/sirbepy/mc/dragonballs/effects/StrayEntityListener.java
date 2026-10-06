package dev.sirbepy.mc.dragonballs.effects;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Removes marked dragon/flying-ball entities left behind by a crash: once for every already-loaded
 * chunk at construction (this package's stand-in for "plugin enable", since {@code effects/} never
 * touches {@code DragonBallsPlugin}), and again on every future chunk load.
 */
final class StrayEntityListener implements Listener {

    private final DragonActor dragonActor;
    private final FlyingBalls flyingBalls;

    StrayEntityListener(JavaPlugin plugin, DragonActor dragonActor, FlyingBalls flyingBalls) {
        this.dragonActor = dragonActor;
        this.flyingBalls = flyingBalls;
        for (World world : plugin.getServer().getWorlds()) {
            dragonActor.removeStray(world);
            flyingBalls.removeStray(world);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Entity entity : chunk.getEntities()) {
            if (entity.getScoreboardTags().contains(EffectMarkers.DRAGON_TAG)) {
                dragonActor.removeStray(entity.getWorld());
            } else if (entity.getScoreboardTags().contains(EffectMarkers.BALL_DISPLAY_TAG)) {
                flyingBalls.removeStray(entity.getWorld());
            }
        }
    }
}
