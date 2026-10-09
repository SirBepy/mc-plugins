package dev.sirbepy.mc.endfight.tier;

import org.bukkit.World;

import java.util.Optional;
import java.util.function.Supplier;

/** Reads the real End world's own battle record, through whichever End world {@code endWorld}
 * currently resolves to (it may not exist at all yet - live keeps {@code allow-end: false} until
 * Joe opens it). */
public final class BukkitEndDragonBattleHistory implements EndDragonBattleHistory {

    private final Supplier<Optional<World>> endWorld;

    public BukkitEndDragonBattleHistory(Supplier<Optional<World>> endWorld) {
        this.endWorld = endWorld;
    }

    @Override
    public boolean hasBeenPreviouslyKilled() {
        return endWorld.get().map(world -> world.getEnderDragonBattle().hasBeenPreviouslyKilled()).orElse(false);
    }
}
