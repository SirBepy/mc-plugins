package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.item.KillStamp;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
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
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Player of the Day (Joe, 2026-10-09). A YAML fixture stands in for stalls.yml, same reason as
 * HeadsStallTest: the shipped file is being regenerated concurrently. */
class WantedBoardTest {

    private static final String STALLS = """
            sign-block: spruce_wall_sign
            wanted-board:
              sign: {x: 20, y: 70, z: 0, facing: west}
              head: {x: 20, y: 71, z: 0, facing: west}
            """;

    private static final Logger LOG = Logger.getLogger("wanted-board-test");

    @TempDir
    File dir;

    private ServerMock server;
    private PluginMock plugin;
    private MarketLayout layout;
    private WorldMock world;
    private final AtomicReference<String> period = new AtomicReference<>("day-1");
    private final AtomicReference<Instant> clock = new AtomicReference<>(Instant.parse("2026-10-09T00:00:00Z"));
    private final List<Player> online = new ArrayList<>();
    private final List<OfflinePlayer> everJoined = new ArrayList<>();
    private final List<Double> paid = new ArrayList<>();
    private final List<String> broadcasts = new ArrayList<>();
    private boolean bankOpen = true;
    // MockBukkit's ServerMock has no Audience implementation (audiences() throws Unimplemented), so
    // sell()'s server-wide broadcast needs a working stand-in here instead of plugin.getServer().
    private final Audience broadcastRecorder = new Audience() {
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
    private static MarketConfig config(double median, double sigma, long min, long max) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(WantedBoardTest.class.getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
        yaml.set("wanted.median", median);
        yaml.set("wanted.sigma", sigma);
        yaml.set("wanted.min", min);
        yaml.set("wanted.max", max);
        return new MarketConfig(yaml);
    }

    private WantedBoard board(Random random, MarketConfig cfg, File stateFile) {
        return new WantedBoard(plugin, layout, () -> cfg, period::get, random, clock::get, () -> List.copyOf(online),
                () -> List.copyOf(everJoined), (player, amount) -> {
            if (bankOpen) {
                paid.add(amount);
            }
            return bankOpen;
        }, broadcastRecorder, stateFile, LOG);
    }

    private WantedBoard board(Random random, MarketConfig cfg) {
        return board(random, cfg, new File(dir, "wanted.yml"));
    }

    // nextGaussian() fixed at 0 too: median * exp(sigma * 0) = median exactly, so price-dependent
    // assertions (sign text, payout amount) get a known round number instead of a real draw.
    private static final Random ALWAYS_ONLINE = new Random() {
        @Override
        public boolean nextBoolean() {
            return true;
        }

        @Override
        public double nextGaussian() {
            return 0;
        }
    };

    private static final Random NEVER_ONLINE = new Random() {
        @Override
        public boolean nextBoolean() {
            return false;
        }

        @Override
        public double nextGaussian() {
            return 0;
        }
    };

    private static ItemStack headOf(UUID id, String name) {
        return headOf(id, name, null);
    }

    /** A head stamped with {@code killedAt} (null for an unstamped head, every head before this feature). */
    private static ItemStack headOf(UUID id, String name, Instant killedAt) {
        ItemStack stack = ItemStack.of(Material.PLAYER_HEAD);
        stack.editMeta(SkullMeta.class, meta -> {
            meta.setPlayerProfile(Bukkit.createProfile(id, name));
            if (killedAt != null) {
                KillStamp.stamp(meta, killedAt);
            }
        });
        return stack;
    }

    @Test
    void halfTheTimeTheTargetIsAnOnlinePlayer() {
        PlayerMock steve = server.addPlayer("Steve");
        online.add(steve);
        WantedBoard board = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000));
        board.rotate();
        assertEquals(steve.getUniqueId(), board.state().targetId());
        assertEquals("Steve", board.state().targetName());
    }

    @Test
    void otherHalfOrWithNobodyOnlineTheTargetComesFromEveryoneWhoEverJoined() {
        PlayerMock steve = server.addPlayer("Steve");
        online.add(steve);
        // hasPlayedBefore() only flips true once MockBukkit sees them disconnect (matches real
        // Bukkit: it reflects playerdata on disk, not "currently connected").
        PlayerMock alex = server.addPlayer("Alex");
        alex.disconnect();
        everJoined.add(alex);
        // The coin says "offline" even though Steve is online right now.
        WantedBoard board = board(NEVER_ONLINE, config(100, 0.8, 10, 1000));
        board.rotate();
        assertEquals(alex.getUniqueId(), board.state().targetId());

        // Nobody online at all: always the offline pool, whatever the coin says.
        online.clear();
        WantedBoard noOneOnline = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000));
        noOneOnline.rotate();
        assertEquals(alex.getUniqueId(), noOneOnline.state().targetId());
    }

    @Test
    void noTargetWhenNobodyIsOnlineAndNobodyHasEverJoined() {
        WantedBoard board = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000));
        board.rotate();
        assertFalse(board.state().hasTarget());
        assertNull(board.state().targetId());
    }

    @Test
    void bannedPlayersAreExcludedFromBothPools() {
        PlayerMock steve = server.addPlayer("Steve");
        steve.ban("test", (Date) null, null);
        online.add(steve);
        // Has genuinely played before (disconnected once), banned regardless: still excluded.
        PlayerMock alex = server.addPlayer("Alex");
        alex.disconnect();
        alex.ban("test", (Date) null, null);
        everJoined.add(alex);

        WantedBoard board = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000));
        board.rotate();
        assertFalse(board.state().hasTarget(), "the only candidates in either pool are banned");
    }

    @Test
    void priceIsClampedToMinAndMax() {
        Random high = new Random() {
            @Override
            public double nextGaussian() {
                return 10;
            }
        };
        assertEquals(0, WantedBoard.price(high, 100, 0.8, 10, 1000).compareTo(BigDecimal.valueOf(1000)), "clamped to max");
        Random low = new Random() {
            @Override
            public double nextGaussian() {
                return -10;
            }
        };
        assertEquals(0, WantedBoard.price(low, 100, 0.8, 10, 1000).compareTo(BigDecimal.valueOf(10)), "clamped to min");
    }

    @Test
    void priceMedianLandsNearOneHundredOverManyDraws() {
        Random random = new Random(42);
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < 4000; i++) {
            prices.add(WantedBoard.price(random, 100, 0.8, 10, 1000));
        }
        prices.sort(Comparator.naturalOrder());
        BigDecimal median = prices.get(prices.size() / 2);
        assertTrue(median.compareTo(BigDecimal.valueOf(70)) >= 0 && median.compareTo(BigDecimal.valueOf(140)) <= 0,
                "median " + median + " should land close to 100");
    }

    @Test
    void signShowsFreshAndOldPricesWhileOpenThenClaimedThenCooldown() {
        PlayerMock target = server.addPlayer("Target");
        PlayerMock seller = server.addPlayer("Seller");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000));
        board.rotate();
        board.apply(world);

        var headBlock = world.getBlockAt(20, 71, 0);
        assertEquals(Material.PLAYER_WALL_HEAD, headBlock.getType());
        assertEquals(BlockFace.WEST, ((Directional) headBlock.getBlockData()).getFacing());
        Skull skull = (Skull) headBlock.getState();
        assertEquals(target.getUniqueId(), skull.getPlayerProfile().getId());

        var signBlock = world.getBlockAt(20, 70, 0);
        Sign sign = (Sign) signBlock.getState();
        assertEquals("WANTED", plainLine(sign, 0));
        assertEquals("Target", plainLine(sign, 1));
        assertEquals("Fresh: $100", plainLine(sign, 2));
        assertEquals("Old: $75", plainLine(sign, 3));

        // A kill timestamped after the bounty posted: a fresh head, full price.
        clock.set(board.state().postedAt().plusSeconds(10));
        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName(), clock.get()));
        board.sell(seller);

        assertEquals(List.of(100.0), paid);
        assertEquals(Material.AIR, seller.getInventory().getItemInMainHand().getType());
        Sign claimed = (Sign) signBlock.getState();
        assertEquals("Target", plainLine(claimed, 1));
        assertEquals("Claimed by", plainLine(claimed, 2));
        assertEquals("Seller", plainLine(claimed, 3));
        assertEquals(List.of("Seller brought in Target's head (fresh kill) for $100!"), broadcasts);

        // The period right after a claimed sale rests: no target, a different sign, head cleared.
        period.set("day-2");
        assertTrue(board.refresh(), "a new period after the sale");
        assertTrue(board.state().cooldown(), "the period right after a sale rests");
        assertFalse(board.state().hasTarget());
        board.apply(world);
        Sign resting = (Sign) signBlock.getState();
        assertEquals("WANTED", plainLine(resting, 0));
        assertEquals("Next bounty", plainLine(resting, 1));
        assertEquals("tomorrow", plainLine(resting, 2));
        assertEquals("", plainLine(resting, 3));
        assertEquals(Material.AIR, world.getBlockAt(20, 71, 0).getType(), "the head spot is cleared during the rest");
    }

    private static String plainLine(Sign sign, int line) {
        return PlainTextComponentSerializer.plainText().serialize(sign.getSide(Side.FRONT).line(line));
    }

    @Test
    void onlyOneSaleGoesThroughPerPeriodServerWide() {
        PlayerMock target = server.addPlayer("Target");
        PlayerMock firstSeller = server.addPlayer("First");
        PlayerMock secondSeller = server.addPlayer("Second");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000));
        board.rotate();

        firstSeller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName(), board.state().postedAt()));
        board.sell(firstSeller);
        assertEquals(List.of(100.0), paid);

        secondSeller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName()));
        board.sell(secondSeller);
        assertEquals(List.of(100.0), paid, "the second attempt is not paid");
        assertEquals(Material.PLAYER_HEAD, secondSeller.getInventory().getItemInMainHand().getType(), "kept: nothing to sell anymore");
        assertTrue(secondSeller.nextMessage().contains("already brought in"));
    }

    @Test
    void theTargetCannotSellTheirOwnHead() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000));
        board.rotate();

        target.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName()));
        board.sell(target);

        assertTrue(paid.isEmpty());
        assertEquals(Material.PLAYER_HEAD, target.getInventory().getItemInMainHand().getType());
        assertTrue(target.nextMessage().contains("can't turn in your own head"));
    }

    @Test
    void aHeadOfSomeoneElseOrNoHeadAtAllIsRefused() {
        PlayerMock target = server.addPlayer("Target");
        PlayerMock seller = server.addPlayer("Seller");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000));
        board.rotate();

        seller.getInventory().setItemInMainHand(headOf(UUID.randomUUID(), "SomeoneElse"));
        board.sell(seller);
        assertTrue(seller.nextMessage().contains("The board wants Target's head today"));
        assertTrue(paid.isEmpty());

        seller.getInventory().setItemInMainHand(ItemStack.of(Material.DIRT));
        board.sell(seller);
        assertTrue(seller.nextMessage().contains("The board wants Target's head today"));

        // A head whose profile has no id at all falls back to matching by name; unstamped, so it's
        // an old head and pays the old-head share, not the full price.
        ItemStack nameOnly = ItemStack.of(Material.PLAYER_HEAD);
        nameOnly.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(Bukkit.createProfile("Target")));
        seller.getInventory().setItemInMainHand(nameOnly);
        board.sell(seller);
        assertEquals(List.of(75.0), paid, "name-only profile still matches when there is no id to compare");
    }

    @Test
    void whenTheEconomyCannotPayTheHeadAndClaimStayOpen() {
        PlayerMock target = server.addPlayer("Target");
        PlayerMock seller = server.addPlayer("Seller");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000));
        board.rotate();

        bankOpen = false;
        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName()));
        board.sell(seller);

        assertTrue(paid.isEmpty());
        assertEquals(Material.PLAYER_HEAD, seller.getInventory().getItemInMainHand().getType(), "the head is kept");
        assertFalse(board.state().claimed(), "still open for a real sale");
        assertTrue(seller.nextMessage().contains("can't pay"));
    }

    @Test
    void stateSurvivesARestartAndANewPeriodPicksAgain() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        File stateFile = new File(dir, "wanted.yml");
        WantedBoard first = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000), stateFile);
        assertTrue(first.refresh(), "first start has no state yet");
        assertEquals(target.getUniqueId(), first.state().targetId());

        WantedBoard restarted = board(NEVER_ONLINE, config(100, 0.8, 10, 1000), stateFile);
        assertFalse(restarted.refresh(), "same period: keeps the saved target, does not re-roll");
        assertEquals(target.getUniqueId(), restarted.state().targetId());
        assertEquals(first.state().price(), restarted.state().price());
        assertEquals(first.state().postedAt(), restarted.state().postedAt());

        // restarted was built with NEVER_ONLINE, so the new pick still reads the offline pool;
        // "Other" needs to have genuinely played before (disconnected once) to qualify for it.
        PlayerMock other = server.addPlayer("Other");
        other.disconnect();
        online.clear();
        everJoined.add(other);
        period.set("day-2");
        assertTrue(restarted.refresh(), "a new period picks again");
        assertEquals(other.getUniqueId(), restarted.state().targetId());
    }

    @Test
    void freshKillPaysFullAnOldOrUnstampedHeadPaysTheOldShareRounded() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);

        WantedBoard freshBoard = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000), new File(dir, "fresh.yml"));
        freshBoard.rotate();
        PlayerMock freshSeller = server.addPlayer("FreshSeller");
        freshSeller.getInventory().setItemInMainHand(
                headOf(target.getUniqueId(), target.getName(), freshBoard.state().postedAt().plusSeconds(1)));
        freshBoard.sell(freshSeller);
        assertEquals(List.of(100.0), paid, "a kill timestamped after the bounty posted pays full");

        paid.clear();
        WantedBoard oldBoard = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000), new File(dir, "old.yml"));
        oldBoard.rotate();
        PlayerMock oldSeller = server.addPlayer("OldSeller");
        oldSeller.getInventory().setItemInMainHand(
                headOf(target.getUniqueId(), target.getName(), oldBoard.state().postedAt().minusSeconds(1)));
        oldBoard.sell(oldSeller);
        assertEquals(List.of(75.0), paid, "a kill timestamped before the bounty posted pays the old-head share, rounded");

        paid.clear();
        WantedBoard unstampedBoard = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000), new File(dir, "unstamped.yml"));
        unstampedBoard.rotate();
        PlayerMock unstampedSeller = server.addPlayer("UnstampedSeller");
        unstampedSeller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName()));
        unstampedBoard.sell(unstampedSeller);
        assertEquals(List.of(75.0), paid, "a head with no stamp at all pays the old-head share too");
    }

    @Test
    void oldHeadShareRoundsToTheNearestDollar() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        // sigma 0: price = median exactly, whatever the draw. 101 * 0.75 = 75.75, rounds up to 76.
        WantedBoard board = board(ALWAYS_ONLINE, config(101, 0, 10, 1000));
        board.rotate();
        PlayerMock seller = server.addPlayer("Seller");
        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName()));
        board.sell(seller);
        assertEquals(List.of(76.0), paid);
    }

    @Test
    void saleBroadcastNamesWhetherTheHeadWasFreshOrOld() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);

        WantedBoard freshBoard = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000), new File(dir, "fresh.yml"));
        freshBoard.rotate();
        PlayerMock freshSeller = server.addPlayer("FreshSeller");
        freshSeller.getInventory().setItemInMainHand(
                headOf(target.getUniqueId(), target.getName(), freshBoard.state().postedAt().plusSeconds(1)));
        freshBoard.sell(freshSeller);
        assertEquals(List.of("FreshSeller brought in Target's head (fresh kill) for $100!"), broadcasts);

        broadcasts.clear();
        WantedBoard oldBoard = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000), new File(dir, "old.yml"));
        oldBoard.rotate();
        PlayerMock oldSeller = server.addPlayer("OldSeller");
        oldSeller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName()));
        oldBoard.sell(oldSeller);
        assertEquals(List.of("OldSeller brought in Target's head (old head) for $75!"), broadcasts);
    }

    @Test
    void marketTodayLineShowsBothFreshAndOldPrices() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000));
        board.rotate();

        String line = PlainTextComponentSerializer.plainText().serialize(board.todayLines().getFirst());
        assertTrue(line.contains("100") && line.contains("75"), "shows both the fresh and the old-head price: " + line);
    }

    @Test
    void cooldownFollowsAClaimedPeriodButNotAnUnclaimedOne() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000));
        board.rotate();
        assertFalse(board.state().claimed());

        period.set("day-2");
        assertTrue(board.refresh());
        assertFalse(board.state().cooldown(), "nobody claimed it, so the next period picks normally");
        assertTrue(board.state().hasTarget());

        PlayerMock seller = server.addPlayer("Seller");
        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName(), board.state().postedAt()));
        board.sell(seller);
        assertTrue(board.state().claimed());

        period.set("day-3");
        assertTrue(board.refresh());
        assertTrue(board.state().cooldown(), "the period right after a claimed sale rests");
        assertFalse(board.state().hasTarget());

        period.set("day-4");
        assertTrue(board.refresh());
        assertFalse(board.state().cooldown(), "the period after the rest picks normally again");
        assertTrue(board.state().hasTarget());
    }

    @Test
    void cooldownSurvivesARestart() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        File stateFile = new File(dir, "wanted.yml");
        WantedBoard board = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000), stateFile);
        board.rotate();
        PlayerMock seller = server.addPlayer("Seller");
        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName(), board.state().postedAt()));
        board.sell(seller);

        period.set("day-2");
        board.refresh();
        assertTrue(board.state().cooldown());

        WantedBoard restarted = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000), stateFile);
        assertFalse(restarted.refresh(), "same period: keeps the saved cooldown, does not re-roll");
        assertTrue(restarted.state().cooldown());
        assertFalse(restarted.state().hasTarget());

        period.set("day-3");
        assertTrue(restarted.refresh());
        assertFalse(restarted.state().cooldown(), "the period after the rest picks normally");
        assertTrue(restarted.state().hasTarget());
    }

    @Test
    void adminRotateForcesANormalPickRightAfterASaleInsteadOfQueuingACooldown() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000));
        board.rotate();
        PlayerMock seller = server.addPlayer("Seller");
        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName(), board.state().postedAt()));
        board.sell(seller);
        assertTrue(board.state().claimed(), "a plain refresh() from here would queue a cooldown");

        // The admin command, same period: forces a normal pick right away instead.
        board.rotate();
        assertFalse(board.state().cooldown());
        assertTrue(board.state().hasTarget());
        assertFalse(board.state().claimed(), "a freshly picked target starts unclaimed");
    }

    @Test
    void adminRotateDuringAnActualCooldownPeriodAlsoPicksNormally() {
        PlayerMock target = server.addPlayer("Target");
        online.add(target);
        WantedBoard board = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000));
        board.rotate();
        PlayerMock seller = server.addPlayer("Seller");
        seller.getInventory().setItemInMainHand(headOf(target.getUniqueId(), target.getName(), board.state().postedAt()));
        board.sell(seller);

        period.set("day-2");
        board.refresh();
        assertTrue(board.state().cooldown());

        board.rotate();
        assertFalse(board.state().cooldown(), "the admin command forces a normal pick even during the rest period");
        assertTrue(board.state().hasTarget());
    }

    @Test
    void playerDeathStampsTheirOwnUnstampedHeadDropButNeverAnotherPlayersOrAnAlreadyStampedOne() {
        PlayerMock dead = server.addPlayer("Dead");
        WantedBoard board = board(ALWAYS_ONLINE, config(100, 0.8, 10, 1000));
        clock.set(Instant.parse("2026-10-09T12:00:00Z"));

        ItemStack ownHead = headOf(dead.getUniqueId(), "Dead");
        ItemStack someoneElsesHead = headOf(UUID.randomUUID(), "Other");
        Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
        ItemStack alreadyStamped = headOf(dead.getUniqueId(), "Dead", earlier);
        ItemStack dirt = ItemStack.of(Material.DIRT);
        List<ItemStack> drops = new ArrayList<>(List.of(ownHead, someoneElsesHead, alreadyStamped, dirt));

        PlayerDeathEvent event = new PlayerDeathEvent(dead, DamageSource.builder(DamageType.PLAYER_ATTACK).build(), drops, 0, null);
        board.onPlayerDeath(event);

        assertEquals(clock.get(), KillStamp.killedAt(event.getDrops().get(0)).orElseThrow(), "the dead player's own unstamped head is stamped now");
        assertTrue(KillStamp.killedAt(event.getDrops().get(1)).isEmpty(), "a head of someone else is left alone");
        assertEquals(earlier, KillStamp.killedAt(event.getDrops().get(2)).orElseThrow(), "an already-stamped head keeps its original time, never re-stamped");
        assertNotNull(event.getDrops().get(3), "a plain non-head drop is untouched");
    }
}
