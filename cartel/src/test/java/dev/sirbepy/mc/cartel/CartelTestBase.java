package dev.sirbepy.mc.cartel;

import dev.sirbepy.mc.cartel.call.CallManager;
import dev.sirbepy.mc.cartel.call.Cousins;
import dev.sirbepy.mc.cartel.call.TestSpawner;
import dev.sirbepy.mc.cartel.call.Trophies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A MockBukkit server with a stone floor at y=99 around the origin, an owner and a victim. */
public abstract class CartelTestBase {

    protected static final int FLOOR_Y = 100;

    @TempDir
    protected Path tempDir;
    protected ServerMock server;
    protected WorldMock world;
    protected PluginMock plugin;
    protected long[] now = {1_000_000L};
    protected CartelConfig config;
    protected CartelStore store;
    protected Cousins cousins;
    protected Trophies trophies;
    protected CallManager calls;
    protected PlayerMock owner;
    protected PlayerMock victim;

    @BeforeEach
    void setUpServer() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        owner = server.addPlayer("Archontopouloi");
        victim = server.addPlayer("Victim");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("owner", owner.getUniqueId().toString());
        config = CartelConfig.from(yaml);
        store = new CartelStore(new File(tempDir.toFile(), "state.yml"), plugin.getLogger());
        cousins = new Cousins(plugin, () -> config);
        trophies = new Trophies(store);
        calls = new CallManager(() -> config, store, TestSpawner.of(cousins), cousins, trophies, () -> now[0], new Random(7));
        floor(0, 0, 30);
        owner.teleport(new Location(world, 0.5, FLOOR_Y, 0.5));
        victim.teleport(new Location(world, 5.5, FLOOR_Y, 5.5));
    }

    @AfterEach
    void tearDownServer() {
        MockBukkit.unmock();
    }

    protected void floor(int cx, int cz, int radius) {
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                world.getBlockAt(x, FLOOR_Y - 1, z).setType(Material.STONE);
            }
        }
    }

    protected static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Every chat line {@code player} has received so far, as plain text. */
    protected static List<String> chat(PlayerMock player) {
        List<String> lines = new ArrayList<>();
        Component next;
        while ((next = player.nextComponentMessage()) != null) {
            lines.add(plain(next));
        }
        return lines;
    }
}
