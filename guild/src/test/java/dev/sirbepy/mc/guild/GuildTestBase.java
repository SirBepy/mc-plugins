package dev.sirbepy.mc.guild;

import dev.sirbepy.mc.guild.config.GuildConfig;
import dev.sirbepy.mc.guild.data.GuildStore;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.econ.Bank;
import dev.sirbepy.mc.guild.time.GuildClock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** A mocked server with the Guild wired around a fixed clock and an in-memory bank, plus a Guildmaster. */
public abstract class GuildTestBase {

    @TempDir
    protected Path dataDir;

    protected ServerMock server;
    protected WorldMock world;
    protected FakeClock clock;
    protected FakeBank bank;
    protected AtomicReference<GuildConfig> config;
    protected GuildStore store;
    protected Guild guild;
    protected PlayerMock gm;
    protected PlayerMock alice;

    @BeforeEach
    void setUpGuild() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        gm = server.addPlayer("ImMijo");
        alice = server.addPlayer("Alice");
        gm.teleport(new Location(world, 0, 64, 0));
        alice.teleport(new Location(world, 5, 64, 0));
        clock = new FakeClock(LocalDateTime.of(2026, 10, 8, 12, 0));
        bank = new FakeBank();
        config = new AtomicReference<>(GuildConfig.defaults(gm.getUniqueId(), ZoneId.of("UTC")));
        store = newStore();
        guild = new Guild(config::get, clock, store, bank, new Random(42));
    }

    @AfterEach
    void tearDownGuild() {
        MockBukkit.unmock();
    }

    protected GuildStore newStore() {
        return new GuildStore(new File(dataDir.toFile(), "guild.yml"), java.util.logging.Logger.getLogger("guild-test"));
    }

    /** Registers with exactly the fee in the bank, so the balance afterwards is whatever the test adds. */
    protected Member join(Player player) {
        bank.set(player, config.get().registrationFee());
        Guild.RegisterResult result = guild.register(player);
        if (result != Guild.RegisterResult.REGISTERED) {
            throw new AssertionError("could not register " + player.getName() + ": " + result);
        }
        return guild.member(player);
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

    public static final class FakeClock implements GuildClock {
        private LocalDateTime now;

        public FakeClock(LocalDateTime now) {
            this.now = now;
        }

        public void set(LocalDateTime now) {
            this.now = now;
        }

        public void plusHours(long hours) {
            now = now.plusHours(hours);
        }

        public void plusDays(long days) {
            now = now.plusDays(days);
        }

        @Override
        public LocalDateTime localNow() {
            return now;
        }

        @Override
        public long nowMillis() {
            return now.toInstant(ZoneOffset.UTC).toEpochMilli();
        }
    }

    public static final class FakeBank implements Bank {
        private final Map<UUID, Double> balances = new HashMap<>();
        public boolean up = true;

        public void set(OfflinePlayer player, double amount) {
            balances.put(player.getUniqueId(), amount);
        }

        @Override
        public boolean available() {
            return up;
        }

        @Override
        public double balance(OfflinePlayer player) {
            return balances.getOrDefault(player.getUniqueId(), 0.0);
        }

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            if (!up || balance(player) < amount) {
                return false;
            }
            balances.put(player.getUniqueId(), balance(player) - amount);
            return true;
        }

        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            if (!up) {
                return false;
            }
            balances.put(player.getUniqueId(), balance(player) + amount);
            return true;
        }
    }
}
