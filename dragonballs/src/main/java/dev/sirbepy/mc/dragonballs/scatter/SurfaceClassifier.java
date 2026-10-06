package dev.sirbepy.mc.dragonballs.scatter;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

/** Pure: no server needed, so the "resample over water" decision is testable without MockBukkit. */
public final class SurfaceClassifier {

    private static final Set<Material> LIQUID_OR_ICE = EnumSet.of(
            Material.WATER, Material.LAVA,
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE
    );

    private SurfaceClassifier() {
    }

    public static SurfaceKind classify(Material highestBlock) {
        return LIQUID_OR_ICE.contains(highestBlock) ? SurfaceKind.LIQUID_OR_ICE : SurfaceKind.SOLID;
    }
}
