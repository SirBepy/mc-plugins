package dev.sirbepy.mc.endfight.opening;

import dev.sirbepy.mc.endfight.arena.BlockLookup;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalRoomFinderTest {

    /** Frames at y=10 around a 3x3 portal centred on (0, 0), lava under it, a stone floor at y=9
     * everywhere else, air above. */
    private final Map<Long, Material> blocks = new HashMap<>();
    private final BlockLookup lookup = (x, y, z) -> blocks.getOrDefault(key(x, y, z), Material.AIR);

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                blocks.put(key(x, 9, z), Material.STONE_BRICKS);
            }
        }
        for (int i = -1; i <= 1; i++) {
            blocks.put(key(i, 10, -2), Material.END_PORTAL_FRAME);
            blocks.put(key(i, 10, 2), Material.END_PORTAL_FRAME);
            blocks.put(key(-2, 10, i), Material.END_PORTAL_FRAME);
            blocks.put(key(2, 10, i), Material.END_PORTAL_FRAME);
            for (int j = -1; j <= 1; j++) {
                blocks.put(key(i, 10, j), Material.END_PORTAL);
                blocks.put(key(i, 9, j), Material.LAVA);
            }
        }
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static long key(int x, int y, int z) {
        return (((long) x) << 40) ^ (((long) y) << 20) ^ (long) z;
    }

    @Test
    void picksASpotJustOutsideTheFrameRingLevelWithTheFrames() {
        Optional<PortalRoomFinder.Spot> spot = PortalRoomFinder.find(lookup, -8, 0, -8, 8, 20, 8);

        assertTrue(spot.isPresent());
        PortalRoomFinder.Spot s = spot.get();
        assertEquals(10, s.y());
        assertEquals(3, Math.max(Math.abs(s.x()), Math.abs(s.z())));
        assertTrue(Math.min(Math.abs(s.x()), Math.abs(s.z())) == 0, "straight out from a side: " + s);
    }

    @Test
    void theYawFacesThePortal() {
        blocks.put(key(-3, 10, 0), Material.STONE);
        blocks.put(key(0, 10, 3), Material.STONE);
        blocks.put(key(3, 10, 0), Material.STONE);

        PortalRoomFinder.Spot s = PortalRoomFinder.find(lookup, -8, 0, -8, 8, 20, 8).orElseThrow();

        // Only the north side (z = -3) is left; facing south (+Z) is yaw 0.
        assertEquals(-3, s.z());
        assertEquals(0f, s.yaw(), 0.5f);
    }

    @Test
    void neverStandsOnLavaOrInsideTheRing() {
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                if (Math.abs(x) > 2 || Math.abs(z) > 2) {
                    blocks.put(key(x, 9, z), Material.LAVA);
                }
            }
        }
        blocks.put(key(6, 9, 6), Material.STONE_BRICKS);

        PortalRoomFinder.Spot s = PortalRoomFinder.find(lookup, -8, 0, -8, 8, 20, 8).orElseThrow();

        assertEquals(6, s.x());
        assertEquals(6, s.z());
    }

    @Test
    void noFramesNoSpot() {
        assertTrue(PortalRoomFinder.find(lookup, 20, 0, 20, 30, 20, 30).isEmpty());
    }

    @Test
    void findAroundALocationInThePortalWorks() {
        assertTrue(PortalRoomFinder.findAround(lookup, 0, 10, 0, 4).isPresent());
    }
}
