package dev.sirbepy.mc.dragonballs.registry;

import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BallViewTest {

    private ServerMock server;

    /** MockBukkit answers getWorld(null) with null, but Paper throws "name cannot be null"; mirror Paper. */
    private static final class PaperLikeServer extends ServerMock {
        @Override
        public World getWorld(String name) {
            if (name == null) {
                throw new IllegalArgumentException("name cannot be null");
            }
            return super.getWorld(name);
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new PaperLikeServer());
        server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static BallView at(String worldName) {
        return new BallView(1, BallState.PLACED, false, worldName, 1, 64, 2, null, null, 0, null);
    }

    @Test
    void aBallWithNoWorldHasNoLocation() {
        assertNull(at(null).lastKnownLocation());
    }

    @Test
    void aBallInAnUnloadedWorldHasNoLocation() {
        assertNull(at("world_nether").lastKnownLocation());
    }

    @Test
    void aBallInALoadedWorldIsWhereItWasLeft() {
        var location = at("world").lastKnownLocation();
        assertEquals("world", location.getWorld().getName());
        assertEquals(64, location.getY());
    }
}
