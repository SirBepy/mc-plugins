package dev.sirbepy.mc.endfight.altar;

import dev.sirbepy.mc.endfight.arena.AltarStructures;
import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.Pillar;

import java.util.List;

/**
 * Where the altar's clickable block is right now: computed on demand from {@link ArenaRegistry
 * #pillars()} (a few arithmetic ops, cheap enough per right-click) rather than cached from an
 * {@link dev.sirbepy.mc.endfight.arena.ArenaListener} callback, so there's no registration-order
 * trap between this and whichever auto-build already ran during {@code EndFightPlugin#onEnable}
 * before this class existed to hear about it.
 *
 * <p>Duplicates {@code ArenaBuilder#averageSurfaceY}'s trivial one-line average rather than
 * reaching into {@code arena/}'s own registry/service classes for it, since this module isn't
 * scoped to change those.
 */
public final class AltarLocator {

    private final ArenaRegistry registry;

    public AltarLocator(ArenaRegistry registry) {
        this.registry = registry;
    }

    /** True if (x, y, z) is the altar's current clickable block; false before the arena has ever
     * built (no pillars scanned yet, so there is nowhere to anchor the altar to). */
    public boolean isClickBlock(int x, int y, int z) {
        List<Pillar> pillars = registry.pillars();
        if (pillars.isEmpty()) {
            return false;
        }
        AltarStructures.AltarResult altar = AltarStructures.build(averageSurfaceY(pillars));
        return altar.clickX() == x && altar.clickY() == y && altar.clickZ() == z;
    }

    private static int averageSurfaceY(List<Pillar> pillars) {
        int sum = 0;
        for (Pillar p : pillars) {
            sum += p.surfaceY();
        }
        return sum / pillars.size();
    }
}
