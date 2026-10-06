package dev.sirbepy.mc.dragonballs.scatter;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Towny is a soft dependency: these tests never install it, so {@link ScatterTownyGuard#load}
 * always takes the no-op branch - exactly the "Towny absent" case the scatter prompt calls out. */
class ScatterTownyGuardTest {

    private ServerMock server;
    private WorldMock world;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void allowsEverythingWhenTownyIsNotInstalled() {
        ScatterTownyGuard guard = ScatterTownyGuard.load(plugin);

        assertTrue(guard.isWilderness(new Location(world, 0, 64, 0)));
        assertTrue(guard.isWilderness(new Location(world, 123456, 64, -98765)));
    }
}
