package dev.sirbepy.mc.dragonballs.scatter;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurfaceClassifierTest {

    @Test
    void waterLavaAndIceAreLiquidOrIce() {
        assertEquals(SurfaceKind.LIQUID_OR_ICE, SurfaceClassifier.classify(Material.WATER));
        assertEquals(SurfaceKind.LIQUID_OR_ICE, SurfaceClassifier.classify(Material.LAVA));
        assertEquals(SurfaceKind.LIQUID_OR_ICE, SurfaceClassifier.classify(Material.ICE));
        assertEquals(SurfaceKind.LIQUID_OR_ICE, SurfaceClassifier.classify(Material.PACKED_ICE));
        assertEquals(SurfaceKind.LIQUID_OR_ICE, SurfaceClassifier.classify(Material.BLUE_ICE));
        assertEquals(SurfaceKind.LIQUID_OR_ICE, SurfaceClassifier.classify(Material.FROSTED_ICE));
    }

    @Test
    void ordinaryGroundIsSolid() {
        assertEquals(SurfaceKind.SOLID, SurfaceClassifier.classify(Material.GRASS_BLOCK));
        assertEquals(SurfaceKind.SOLID, SurfaceClassifier.classify(Material.SAND));
        assertEquals(SurfaceKind.SOLID, SurfaceClassifier.classify(Material.STONE));
        assertEquals(SurfaceKind.SOLID, SurfaceClassifier.classify(Material.END_STONE));
    }
}
