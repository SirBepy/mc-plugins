package dev.sirbepy.mc.haru;

import dev.sirbepy.mc.haru.config.HaruConfig;
import dev.sirbepy.mc.haru.data.HaruStore;
import dev.sirbepy.mc.haru.data.OwnerState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** A mocked server with Haru's services wired around a {@link FakeClock}, and an owner player. */
public abstract class HaruTestBase {

    @TempDir
    protected Path dataDir;

    protected ServerMock server;
    protected WorldMock world;
    protected PluginMock plugin;
    protected FakeClock clock;
    protected AtomicReference<HaruConfig> config;
    protected HaruStore store;
    protected HaruContext ctx;
    protected PlayerMock owner;
    protected final List<Player> celebrated = new ArrayList<>();

    @BeforeEach
    void setUpHaru() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        owner = server.addPlayer("ImMijo");
        owner.teleport(new Location(world, 0, 64, 0));
        clock = new FakeClock();
        config = new AtomicReference<>(new HaruConfig(owner.getUniqueId(), ZoneId.of("UTC"), "world", 3, 0.15,
                dev.sirbepy.mc.core.npc.NpcSkin.NONE));
        store = new HaruStore(new File(dataDir.toFile(), "data.yml"), plugin.getLogger());
        ctx = new HaruContext(plugin, config::get, clock, store, celebrated::add);
    }

    @AfterEach
    void tearDownHaru() {
        MockBukkit.unmock();
    }

    protected OwnerState state() {
        return ctx.ownerState();
    }

    protected void give(Player player, Material material, int amount) {
        player.getInventory().addItem(new ItemStack(material, amount));
    }

    protected int count(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }
}
