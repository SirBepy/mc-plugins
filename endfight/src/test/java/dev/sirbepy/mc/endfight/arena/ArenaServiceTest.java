package dev.sirbepy.mc.endfight.arena;

import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the spec change: the arena auto-builds the first time an End world is seen with nothing
 * built yet, but a restart that already has {@code state.yml} marked built only restores the
 * registry from disk - it never rescans/rebuilds on its own after that, even if an End world with
 * a full (different) pillar set is sitting right there. */
class ArenaServiceTest {

    private ServerMock server;
    private PluginMock plugin;
    private ArenaRegistry registry;
    private ArenaService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("EndFight");
        registry = new ArenaRegistry();
        service = new ArenaService(plugin, registry);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private WorldMock seededEndWorld() {
        WorldMock end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        EndFightTestSupport.seedWorld(end, EndFightTestSupport.samplePillars());
        return end;
    }

    @Test
    void endWorldPresentWithNoStateBuilds() {
        seededEndWorld();
        assertFalse(registry.isBuilt());

        service.autoBuildIfNeeded();

        assertTrue(registry.isBuilt());
        assertEquals(10, registry.pillars().size());
        assertTrue(registry.blockCount() > 0);
    }

    @Test
    void aRestartWithStateAlreadyBuiltOnlyRestoresTheRegistryNeverRescans() {
        Pillar sentinel = new Pillar(9999, 9999, 2, 76, 64);
        ArenaStateStore stateStore = new ArenaStateStore(new File(plugin.getDataFolder(), "state.yml"), Logger.getLogger("endfight-test"));
        stateStore.save(new ArenaState(true, ArenaState.CURRENT_LAYOUT_VERSION, List.of(sentinel), null));

        // A real, fully-seeded End world also exists - if auto-build's "already built" guard were
        // missing, this is what it would wrongly rescan and overwrite the sentinel with.
        seededEndWorld();

        service.restoreFromState();
        assertTrue(registry.isBuilt());
        assertEquals(1, registry.pillars().size());
        int blockCountAfterRestore = registry.blockCount();

        service.autoBuildIfNeeded();

        assertEquals(1, registry.pillars().size(), "should not have rescanned the real 10-pillar world");
        assertEquals(blockCountAfterRestore, registry.blockCount());
    }

    // A rebuild must stand where the first build did: reading the ground again would find our own
    // foundations instead of the island.
    @Test
    void aRebuildKeepsTheTerrainTheFirstBuildRead() {
        seededEndWorld();
        service.buildNow();
        Terrain first = registry.terrain();
        assertNotNull(first);

        ArenaStateStore stateStore = new ArenaStateStore(new File(plugin.getDataFolder(), "state.yml"), Logger.getLogger("endfight-test"));
        Terrain moved = new Terrain(first.fortressY() + 7, first.altarY() + 3);
        stateStore.save(new ArenaState(true, ArenaState.CURRENT_LAYOUT_VERSION, registry.pillars(), moved));
        service.buildNow();

        assertEquals(moved, registry.terrain());
        assertEquals(moved, stateStore.load().terrain());
    }
}
