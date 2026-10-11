package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.item.KillStamp;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Player of the Day (Joe, 2026-10-09): the MockBukkit fixture shared by WantedBoardPostingTest,
 * WantedBoardSaleTest and WantedBoardSignTest, split by concern out of one over-long test class. A
 * YAML fixture stands in for stalls.yml, same reason as HeadsStallTest: the shipped file is being
 * regenerated concurrently.
 */
public abstract class WantedBoardTestBase {

    private static final String STALLS = """
            sign-block: spruce_wall_sign
            wanted-board:
              sign: {x: 20, y: 70, z: 0, facing: west}
              head: {x: 20, y: 71, z: 0, facing: west}
            """;

    protected static final Logger LOG = Logger.getLogger("wanted-board-test");
    protected static final long HOUR = 20L * 60 * 60;

    @TempDir
    protected File dir;

    protected ServerMock server;
    protected PluginMock plugin;
    protected MarketLayout layout;
    protected WorldMock world;
    protected final AtomicReference<String> period = new AtomicReference<>("day-1");
    protected final AtomicReference<Instant> clock = new AtomicReference<>(Instant.parse("2026-10-09T00:00:00Z"));
    protected final List<Player> online = new ArrayList<>();
    protected final List<OfflinePlayer> everJoined = new ArrayList<>();
    protected final List<Double> paid = new ArrayList<>();
    protected final List<UUID> paidTo = new ArrayList<>();
    // Each player's time_since_death in ticks; anyone missing has just died (0).
    protected final Map<UUID, Long> alive = new HashMap<>();
    protected final List<String> broadcasts = new ArrayList<>();
    protected boolean bankOpen = true;
    // MockBukkit's ServerMock has no Audience implementation (audiences() throws Unimplemented), so
    // sell()'s server-wide broadcast needs a working stand-in here instead of plugin.getServer().
    protected final Audience broadcastRecorder = new Audience() {
        @Override
        public void sendMessage(Component message) {
            broadcasts.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
    };

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        layout = MarketLayout.from(YamlConfiguration.loadConfiguration(new StringReader(STALLS)));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // The real messages section, not a bare YamlConfiguration: WantedBoard's refusal/sold messages
    // come from config.yml, and tests assert on their actual text.
    protected static MarketConfig config(double base, double perHour, long cap, double spread) {
        return config(base, perHour, cap, spread, true);
    }

    // The shipped config.yml has wanted.enabled: false (Joe, 2026-10-11); every test above exercises
    // the board itself, so they all force it back on except the enabled-flag test, which overrides it.
    protected static MarketConfig config(double base, double perHour, long cap, double spread, boolean enabled) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(WantedBoardTestBase.class.getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
        yaml.set("wanted.enabled", enabled);
        yaml.set("wanted.base", base);
        yaml.set("wanted.per-hour", perHour);
        yaml.set("wanted.cap", cap);
        yaml.set("wanted.spread", spread);
        // Most tests run with one or two players online; the minimum has its own test.
        yaml.set("wanted.min-online", 0);
        return new MarketConfig(yaml);
    }

    // A flat $100 bounty whatever the time alive, no random factor: the tests that aren't about
    // the value itself get the same round numbers the fixed-price board had.
    protected static MarketConfig flat() {
        return config(100, 0, 1000, 0);
    }

    // Joe's 2026-10-09 numbers with the random factor switched off: $50 + $25 an hour, cap $500.
    protected static MarketConfig growing() {
        return config(50, 25, 500, 0);
    }

    protected WantedBoard board(Random random, java.util.function.Supplier<MarketConfig> cfg, File stateFile) {
        return new WantedBoard(plugin, layout, cfg, period::get, random, clock::get, () -> List.copyOf(online),
                () -> List.copyOf(everJoined), id -> alive.getOrDefault(id, 0L), (player, amount) -> {
            if (bankOpen) {
                paid.add(amount);
                paidTo.add(player.getUniqueId());
            }
            return bankOpen;
        }, broadcastRecorder, stateFile, LOG);
    }

    protected WantedBoard board(Random random, MarketConfig cfg, File stateFile) {
        return board(random, () -> cfg, stateFile);
    }

    protected WantedBoard board(Random random, MarketConfig cfg) {
        return board(random, cfg, new File(dir, "wanted.yml"));
    }

    /** Every nextDouble() returns {@code roll}: the online coin compares it with online-chance (0.8). */
    protected static Random rolling(double roll) {
        return new Random() {
            @Override
            public double nextDouble() {
                return roll;
            }
        };
    }

    protected static final Random ALWAYS_ONLINE = rolling(0);
    protected static final Random NEVER_ONLINE = rolling(0.99);

    protected static ItemStack headOf(UUID id, String name) {
        return headOf(id, name, null);
    }

    /** A head stamped with {@code killedAt} (null for an unstamped head, every head before this feature). */
    protected static ItemStack headOf(UUID id, String name, Instant killedAt) {
        ItemStack stack = ItemStack.of(Material.PLAYER_HEAD);
        stack.editMeta(SkullMeta.class, meta -> {
            meta.setPlayerProfile(Bukkit.createProfile(id, name));
            if (killedAt != null) {
                KillStamp.stamp(meta, killedAt);
            }
        });
        return stack;
    }

    protected static String plainLine(Sign sign, int line) {
        return PlainTextComponentSerializer.plainText().serialize(sign.getSide(Side.FRONT).line(line));
    }
}
