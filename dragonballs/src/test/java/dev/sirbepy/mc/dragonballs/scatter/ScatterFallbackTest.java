package dev.sirbepy.mc.dragonballs.scatter;

import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The ground fallback's own placement math: {@code getHighestBlockYAt} returns the
 * surface block's own y, so a ball resting "on" it belongs one block above, not at it - the same
 * rule {@code TownEviction} already used. Before this fix {@code placeAtSurfaceNow} placed straight
 * at that y, replacing the surface block instead of resting on it.
 */
class ScatterFallbackTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void restsOneBlockAboveTheSurfaceInsteadOfReplacingIt() {
        world.getBlockAt(10, 64, 10).setType(Material.STONE);

        Location spot = ScatterFallback.acceptedSurfaceSpot(world, 10, 10, m -> true);

        assertEquals(65, spot.getBlockY(), "on top of the stone at 64, not inside it");
    }

    @Test
    void rejectsAColumnUnderALeafCanopy() {
        world.getBlockAt(10, 64, 10).setType(Material.STONE);
        world.getBlockAt(10, 70, 10).setType(Material.OAK_LEAVES);

        Location spot = ScatterFallback.acceptedSurfaceSpot(world, 10, 10, m -> true);

        assertNull(spot, "a canopy column is never an accepted ground fallback spot");
    }

    @Test
    void rejectsAMaterialTheCallerDoesNotAccept() {
        world.getBlockAt(10, 64, 10).setType(Material.WATER);

        Location spot = ScatterFallback.acceptedSurfaceSpot(world, 10, 10,
                m -> SurfaceClassifier.classify(m) == SurfaceKind.SOLID);

        assertNull(spot, "the overworld ground fallback never lands on open water");
    }

    @Test
    void theLastResortSpotAlsoRestsAboveTheSurface() {
        world.getBlockAt(20, 70, 20).setType(Material.GRASS_BLOCK);

        Location spot = ScatterFallback.desperateSpot(world, 20, 20);

        assertEquals(71, spot.getBlockY(), "still on top of the ground, even in the give-up path");
    }
}
