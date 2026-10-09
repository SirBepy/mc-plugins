package dev.sirbepy.mc.endfight.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RespawnRebuildListenerTest {

    private ServerMock server;
    private WorldMock endWorld;
    private WorldMock overworld;
    private PluginMock plugin;
    private AtomicInteger rebuildCount;
    private RespawnRebuildListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        endWorld = server.addSimpleWorld("world_the_end");
        endWorld.setEnvironment(World.Environment.THE_END);
        overworld = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin("EndFight");
        rebuildCount = new AtomicInteger();
        listener = new RespawnRebuildListener(plugin, rebuildCount::incrementAndGet);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aDragonSpawningInTheEndSchedulesARebuildAfterTheDelay() {
        EnderDragon dragon = endWorld.spawn(new Location(endWorld, 0, 80, 0), EnderDragon.class);
        CreatureSpawnEvent event = new CreatureSpawnEvent(dragon, CreatureSpawnEvent.SpawnReason.DEFAULT);

        listener.onSpawn(event);

        assertEquals(0, rebuildCount.get(), "should not rebuild immediately");
        server.getScheduler().performTicks(39);
        assertEquals(0, rebuildCount.get(), "should still be waiting just before the delay");
        server.getScheduler().performTicks(2);
        assertEquals(1, rebuildCount.get());
    }

    @Test
    void aDragonSpawningInAnotherWorldNeverSchedulesARebuild() {
        EnderDragon dragon = overworld.spawn(new Location(overworld, 0, 80, 0), EnderDragon.class);
        CreatureSpawnEvent event = new CreatureSpawnEvent(dragon, CreatureSpawnEvent.SpawnReason.DEFAULT);

        listener.onSpawn(event);
        server.getScheduler().performTicks(100);

        assertEquals(0, rebuildCount.get());
    }

    @Test
    void aNonDragonSpawningInTheEndNeverSchedulesARebuild() {
        Zombie zombie = endWorld.spawn(new Location(endWorld, 0, 80, 0), Zombie.class);
        CreatureSpawnEvent event = new CreatureSpawnEvent(zombie, CreatureSpawnEvent.SpawnReason.NATURAL);

        listener.onSpawn(event);
        server.getScheduler().performTicks(100);

        assertEquals(0, rebuildCount.get());
    }
}
