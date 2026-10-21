package dev.sirbepy.mc.minibosses.chronicle;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Chronicler's stray-body sweep, which runs on every chunk load. */
class ChroniclerNpcTest {

    private ServerMock server;
    private PluginMock plugin;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aMarkedLeftoverIsRemovedEvenWhileSheHasNoRecordedBodies(@TempDir Path dir) {
        // Never placed, so every body slot is empty: the state after /chronicle admin remove too.
        ChroniclerNpc npc = new ChroniclerNpc(plugin, dir.toFile(), Logger.getLogger("chronicler-test"), id -> false);
        Zombie leftover = world.spawn(new Location(world, 0, 64, 0), Zombie.class);
        leftover.getPersistentDataContainer().set(new NamespacedKey(plugin, "chronicler"), PersistentDataType.STRING, "body");
        Zombie bystander = world.spawn(new Location(world, 2, 64, 0), Zombie.class);

        npc.removeStrays(new Entity[] {leftover, bystander});

        assertFalse(leftover.isValid(), "a marked body she does not own is swept");
        assertTrue(bystander.isValid(), "an unmarked entity is left alone");
    }
}
