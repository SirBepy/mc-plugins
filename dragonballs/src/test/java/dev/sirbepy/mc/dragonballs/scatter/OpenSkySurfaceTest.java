package dev.sirbepy.mc.dragonballs.scatter;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Shared by {@code TownEviction} and {@code ScatterFallback}: a column only counts as open to the
 * sky if its NO_LEAVES height matches its MOTION_BLOCKING height, so a canopy or a trunk top is
 * never mistaken for ground. */
class OpenSkySurfaceTest {

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
    void findsTheHighestBlockOnOpenGround() {
        world.getBlockAt(10, 64, 10).setType(Material.STONE);

        OpenSkySurface.Spot spot = OpenSkySurface.find(world, 10, 10);

        assertEquals(64, spot.floorY(), "the stone block itself, not one above or below it");
        assertEquals(Material.STONE, spot.floorMaterial());
    }

    @Test
    void skipsAColumnUnderALeafCanopy() {
        world.getBlockAt(10, 64, 10).setType(Material.STONE);
        world.getBlockAt(10, 70, 10).setType(Material.OAK_LEAVES);

        OpenSkySurface.Spot spot = OpenSkySurface.find(world, 10, 10);

        assertNull(spot, "NO_LEAVES height (64) doesn't match MOTION_BLOCKING height (70)");
    }
}
