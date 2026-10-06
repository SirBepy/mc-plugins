package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.registry.BallView;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Self-heal: whenever a chunk loads, every PLACED ball (of any of the 3 sets) whose stored block
 * falls inside it is checked against the actual block there. A missing/changed block (someone
 * bypassed the break handler, a world edit, chunk corruption) gets re-scattered within its own set.
 * A block that's still there just gets its texture re-synced to the registry's current stone/active
 * state, which is also how a block missed by {@link BallRegistry#clearStone}'s one-time sweep (its
 * chunk was unloaded at the time) eventually catches up.
 */
public final class BallChunkLoadListener implements Listener {

    private final BallSets sets;

    public BallChunkLoadListener(BallSets sets) {
        this.sets = sets;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        var chunk = event.getChunk();
        for (BallRegistry registry : sets.all()) {
            for (BallView view : registry.placedBallsInChunk(event.getWorld().getName(), chunk.getX(), chunk.getZ())) {
                Block block = event.getWorld().getBlockAt((int) view.x(), (int) view.y(), (int) view.z());
                if (block.getState() instanceof Skull skull && sets.items().isBallBlock(skull)
                        && sets.items().starOfBlock(skull).orElse(-1) == view.star()
                        && sets.items().setOfBlock(skull) == registry.set()) {
                    sets.items().applyBlockTexture(skull, registry.set(), view.star(), view.stone());
                    skull.update(true);
                } else {
                    var lastSummon = registry.lastSummon();
                    if (lastSummon != null) {
                        registry.scatterOne(view.star(), lastSummon, view.stone());
                    }
                }
            }
        }
    }
}
