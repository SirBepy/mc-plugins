package dev.sirbepy.mc.endfight.arena;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainScannerTest {

    /** End stone columns everywhere within 20 blocks of the portal, as high as {@code ground} says. */
    private static FakeBlockLookup island(java.util.function.IntBinaryOperator ground) {
        FakeBlockLookup lookup = new FakeBlockLookup();
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                int top = ground.applyAsInt(x, z);
                for (int y = top - 3; y <= top; y++) {
                    lookup.set(x, y, z, Material.END_STONE);
                }
            }
        }
        return lookup;
    }

    @Test
    void flatGroundPutsBothOnIt() {
        Terrain terrain = TerrainScanner.scan(island((x, z) -> 60)).orElseThrow();

        assertEquals(new Terrain(60, 60), terrain);
    }

    // A domed island: the pillars' far-out ground (~52 here) would bury the fortress, the real ring
    // ground is what counts.
    @Test
    void theFortressStandsOnItsOwnRingNotTheIslandsEdge() {
        Terrain terrain = TerrainScanner.scan(island((x, z) -> 70 - (int) Math.round(Math.hypot(x, z) / 2))).orElseThrow();

        assertTrue(terrain.fortressY() >= 62 && terrain.fortressY() <= 63, "ring ground is ~62-63, got " + terrain.fortressY());
        assertEquals(66, terrain.altarY(), "the highest ground under the dais (the corner nearest the portal)");
    }

    @Test
    void aQuarterOfTheRingBeingHigherDoesNotLiftTheFortress() {
        // one side of the ring (x > 12) is a 6-block ridge
        Terrain terrain = TerrainScanner.scan(island((x, z) -> x > 12 ? 66 : 60)).orElseThrow();

        assertEquals(60, terrain.fortressY());
    }

    @Test
    void noEndStoneMeansNoTerrain() {
        assertTrue(TerrainScanner.scan(new FakeBlockLookup()).isEmpty());
    }
}
