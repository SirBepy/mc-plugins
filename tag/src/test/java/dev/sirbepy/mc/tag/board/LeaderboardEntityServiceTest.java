package dev.sirbepy.mc.tag.board;

import dev.sirbepy.mc.tag.config.LeaderboardConfig;
import dev.sirbepy.mc.tag.state.TagState;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the entity side of todo 03 (in-world leaderboard): a second {@code ensureSpawned()} must
 * replace the live entity rather than duplicate it (e.g. a chunk reload firing twice), and
 * {@code despawn()} must leave nothing behind.
 */
class LeaderboardEntityServiceTest {

    private ServerMock server;
    private PluginMock plugin;
    private WorldMock world;
    private NamespacedKey key;
    private TagState state;
    private LeaderboardEntityService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        world.getChunkAt(0, 0).load();
        key = new NamespacedKey(plugin, "leaderboard");
        state = new TagState();
        service = new LeaderboardEntityService(plugin, LeaderboardConfig.from(null), () -> state);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private List<Entity> ours() {
        return world.getEntities().stream().filter(this::isOurs).toList();
    }

    private boolean isOurs(Entity entity) {
        return entity instanceof TextDisplay && entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    @Test
    void ensureSpawnedTwiceLeavesExactlyOneEntityAtTheLocation() {
        state.leaderboardLocation = new TagState.LeaderboardLocation("world", 0.5, 65.0, 0.5);

        service.ensureSpawned();
        service.ensureSpawned();

        List<Entity> ours = ours();
        assertEquals(1, ours.size(), "a second ensureSpawned() must replace the old entity, not duplicate it");
        Location loc = ours.get(0).getLocation();
        assertEquals(0.5, loc.getX(), 1e-9);
        assertEquals(65.0, loc.getY(), 1e-9);
        assertEquals(0.5, loc.getZ(), 1e-9);
    }

    @Test
    void despawnRemovesTheEntityAndASubsequentEnsureSpawnedBringsItBack() {
        state.leaderboardLocation = new TagState.LeaderboardLocation("world", 0.5, 65.0, 0.5);
        service.ensureSpawned();
        assertEquals(1, ours().size());

        service.despawn();
        assertEquals(0, ours().size(), "despawn() must remove every entity we spawned");

        service.ensureSpawned();
        assertEquals(1, ours().size(), "ensureSpawned() after despawn() must spawn a fresh one");
    }

    @Test
    void ensureSpawnedIsANoOpWithNoLocationSet() {
        service.ensureSpawned();

        assertEquals(0, ours().size());
    }
}
