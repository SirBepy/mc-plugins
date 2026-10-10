package dev.sirbepy.mc.tag;

import dev.sirbepy.mc.core.SpawnArea;
import dev.sirbepy.mc.tag.board.LeaderboardEntityService;
import dev.sirbepy.mc.tag.board.ScoreboardService;
import dev.sirbepy.mc.tag.config.TagConfig;
import dev.sirbepy.mc.tag.head.HeadService;
import dev.sirbepy.mc.tag.listener.TagDamageListener;
import dev.sirbepy.mc.tag.log.LogService;
import dev.sirbepy.mc.tag.prefix.PrefixService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spawn is a safe space, "also no tagging" (Joe, 2026-10-10): no tag lands in or from the spawn area. */
class TagSpawnSafeTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private WorldMock world;
    private TagService service;
    private TagDamageListener listener;
    private PlayerMock it;
    private PlayerMock victim;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        world.setSpawnLocation(0, 64, 0);
        PluginMock plugin = MockBukkit.createMockPlugin();
        it = server.addPlayer();
        victim = server.addPlayer();
        it.setGameMode(GameMode.SURVIVAL);
        victim.setGameMode(GameMode.SURVIVAL);

        TagConfig config = TagConfig.load(new YamlConfiguration(), new LogService(null));
        File stateFile = new File(tempDir.toFile(), "state.yml");
        LeaderboardEntityService leaderboard = new LeaderboardEntityService(plugin, config.leaderboard, () -> service.state());
        service = new TagService(plugin, stateFile, new HeadService(plugin), new PrefixService(new NoopNamesApi()),
                new ScoreboardService(new NoopNamesApi()), leaderboard, config);
        service.state().currentIt = it.getUniqueId();
        listener = new TagDamageListener(service);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EntityDamageByEntityEvent hit() {
        return new EntityDamageByEntityEvent(it, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1.0);
    }

    private Location inSpawn() {
        return new Location(world, SpawnArea.RADIUS, 64, -SpawnArea.RADIUS);
    }

    private Location outOfSpawn(int dx) {
        return new Location(world, SpawnArea.RADIUS + 10 + dx, 64, 0);
    }

    @Test
    void aVictimInSpawnIsNotTagged() {
        it.teleport(outOfSpawn(0));
        victim.teleport(inSpawn());
        EntityDamageByEntityEvent event = hit();
        listener.onDamage(event);
        assertFalse(event.isCancelled(), "no tag, so the hit is left for the PvP rules (Tweaks cancels it in spawn)");
        assertEquals(it.getUniqueId(), service.state().currentIt);
    }

    @Test
    void itInSpawnCannotTagSomeoneOutside() {
        it.teleport(inSpawn());
        victim.teleport(outOfSpawn(0));
        listener.onDamage(hit());
        assertEquals(it.getUniqueId(), service.state().currentIt);
    }

    @Test
    void outsideSpawnTagsStillLand() {
        it.teleport(outOfSpawn(0));
        victim.teleport(outOfSpawn(1));
        EntityDamageByEntityEvent event = hit();
        listener.onDamage(event);
        assertTrue(event.isCancelled(), "a valid tag cancels the damage");
        assertEquals(victim.getUniqueId(), service.state().currentIt);
    }
}
