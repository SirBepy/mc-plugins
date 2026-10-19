package dev.sirbepy.mc.market;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /market watch}: subscribe/unsubscribe/list/cap, matching a watch against today's picks
 * (both a regular stall's buyers and the Heads stall's wanted mobs), the rotate-time ping to online
 * watchers, the join-time catch-up ping (once per market day), and the {@code watches.yml} round trip.
 * Uses small inline fixtures, not the shipped {@code stalls.yml}/{@code heads.yml}, so which item or
 * mob gets picked each day is deterministic: every stall/the head-stall here has exactly as many
 * pool items as it has signs where the test needs a guaranteed pick (birch_log, lumberjack), and
 * exactly one spare slot's worth of pool where it needs a genuine "not picked today" case (smith,
 * the head-stall) - the test reads back which of the pair rotate actually chose, like
 * HeadsStallTest's {@code unwantedHead()} does, rather than hard-coding a lucky seed.
 */
class MarketWatchesTest {

    private static final String STALLS = """
            sign-block: spruce_wall_sign
            head-stall:
              name: Heads
              color: red
              slots:
                - {x: 10, y: 70, z: 0, facing: south}
            stalls:
              lumberjack:
                name: Lumberjack
                color: brown
                slots:
                  - {x: 0, y: 70, z: 0, facing: south}
                buys:
                  - {item: birch_log, amount: 64, price: 28.8}
              smith:
                name: Smith
                color: gray
                slots:
                  - {x: 1, y: 70, z: 0, facing: south}
                buys:
                  - {item: diamond, amount: 16, price: 176}
                  - {item: raw_iron, amount: 64, price: 44}
              bulk:
                name: Bulk
                color: white
                slots:
                  - {x: 2, y: 70, z: 0, facing: south}
                buys:
                  - {item: item_a, amount: 1, price: 1}
                  - {item: item_b, amount: 1, price: 1}
                  - {item: item_c, amount: 1, price: 1}
                  - {item: item_d, amount: 1, price: 1}
                  - {item: item_e, amount: 1, price: 1}
                  - {item: item_f, amount: 1, price: 1}
                  - {item: item_g, amount: 1, price: 1}
                  - {item: item_h, amount: 1, price: 1}
                  - {item: item_i, amount: 1, price: 1}
                  - {item: item_j, amount: 1, price: 1}
            """;

    private static final String HEADS = """
            mobs:
              allay:
                name: "Allay"
                heads:
                  - {name: "Allay Head", texture: aaaa1111, price: 2.5}
              bee:
                name: "Bee"
                heads:
                  - {name: "Bee Head", texture: bbbb2222, price: 2.5}
            """;

    @TempDir
    File dir;

    private ServerMock server;
    private MarketLayout layout;
    private MobHeads heads;
    private MarketConfig config;
    private Market market;
    private MarketWatches watches;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        layout = MarketLayout.from(YamlConfiguration.loadConfiguration(new StringReader(STALLS)));
        heads = new MobHeads(YamlConfiguration.loadConfiguration(new StringReader(HEADS)));
        var stream = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"));
        config = new MarketConfig(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        market = new Market(layout, heads, () -> config, () -> "day-1", new Random(7), new File(dir, "state.yml"),
                Logger.getLogger("market-watches-test"));
        market.refresh();
        watches = new MarketWatches(market, layout, heads, () -> config, new File(dir, "watches.yml"),
                Logger.getLogger("market-watches-test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** The smith item NOT among today's picks (smith's pool has 2, only 1 slot). */
    private String unpickedStallItem() {
        List<String> picked = market.buying("smith").stream().map(Offer::item).toList();
        return layout.stalls().stream().filter(s -> s.id().equals("smith")).findFirst().orElseThrow()
                .buys().stream().map(Offer::item).filter(item -> !picked.contains(item)).findFirst().orElseThrow();
    }

    /** The mob NOT wanted today (2 mobs, only 1 head-stall slot). */
    private MobHeads.Mob unwantedMob() {
        return heads.mobs().stream().filter(m -> !market.wantedMobs().contains(m)).findFirst().orElseThrow();
    }

    private MobHeads.Mob wantedMob() {
        return market.wantedMobs().getFirst();
    }

    @Test
    void subscribingListsInOrderUnsubscribingRemovesCapsAtTenAndRefusesAnUnknownItem() {
        UUID id = UUID.randomUUID();
        assertTrue(watches.watching(id).isEmpty());

        assertEquals(MarketWatches.AddResult.ADDED, watches.add(id, "birch_log"));
        assertEquals(MarketWatches.AddResult.ADDED, watches.add(id, "diamond"));
        assertEquals(List.of("birch_log", "diamond"), watches.watching(id), "listed in the order they were added");

        assertEquals(MarketWatches.AddResult.ALREADY_WATCHING, watches.add(id, "birch_log"));
        assertEquals(MarketWatches.AddResult.UNKNOWN_ITEM, watches.add(id, "not_a_real_item"));

        assertTrue(watches.remove(id, "birch_log"));
        assertFalse(watches.remove(id, "birch_log"), "already removed");
        assertFalse(watches.remove(id, "never_watched"));
        assertEquals(List.of("diamond"), watches.watching(id));

        // Fill back up to the cap with distinct catalogue items, then the 11th is refused.
        watches.add(id, "birch_log");
        for (char c = 'a'; c <= 'h'; c++) {
            watches.add(id, "item_" + c);
        }
        assertEquals(MarketWatches.MAX_WATCHES, watches.watching(id).size());
        assertEquals(MarketWatches.AddResult.CAP_REACHED, watches.add(id, "item_i"));

        assertTrue(watches.remove(id, "item_a"));
        assertEquals(MarketWatches.AddResult.ADDED, watches.add(id, "item_i"), "under the cap again after removing one");
    }

    @Test
    void watchersOfListsEveryWatcherOfThatItemAndNothingElse() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        watches.add(first, "birch_log");
        watches.add(second, "birch_log");
        watches.add(second, "diamond");

        assertEquals(List.of(first, second), watches.watchersOf("birch_log"));
        assertEquals(List.of(second), watches.watchersOf("diamond"));
        assertTrue(watches.watchersOf("raw_iron").isEmpty());
    }

    @Test
    void rotateNotifiesAnOnlineWatcherOnceForAPickedStallItemButNotForOneLeftOutOfTodaysPicks() {
        PlayerMock player = server.addPlayer();
        String unpicked = unpickedStallItem();
        watches.add(player.getUniqueId(), "birch_log");
        watches.add(player.getUniqueId(), unpicked);

        watches.notifyRotate(List.of(player));

        String message = player.nextMessage();
        assertTrue(message.contains("Birch log"), message);
        assertTrue(message.contains("Lumberjack"), message);
        assertNull(player.nextMessage(), "no second line for the item smith didn't pick today");

        // Calling it again the same day (e.g. the scheduler ticking again) never repeats the line.
        watches.notifyRotate(List.of(player));
        assertNull(player.nextMessage());
    }

    @Test
    void rotateNotifiesAWatcherOfAWantedHeadButNotOneThatIsntWantedToday() {
        PlayerMock player = server.addPlayer();
        MobHeads.Mob wanted = wantedMob();
        MobHeads.Mob unwanted = unwantedMob();
        watches.add(player.getUniqueId(), wanted.id());
        watches.add(player.getUniqueId(), unwanted.id());

        watches.notifyRotate(List.of(player));

        String message = player.nextMessage();
        assertTrue(message.contains(wanted.name()), message);
        assertTrue(message.contains("Heads"), message);
        assertNull(player.nextMessage(), "the unwanted mob never pings");
    }

    @Test
    void joinPingsOnceForAWatchedItemStillOnTodaysMarketAndASecondJoinTheSameDayDoesNotRepeatIt() {
        PlayerMock player = server.addPlayer();
        watches.add(player.getUniqueId(), "birch_log");

        watches.onJoin(new PlayerJoinEvent(player, Component.empty()));
        assertTrue(player.nextMessage().contains("Lumberjack"));
        assertNull(player.nextMessage());

        watches.onJoin(new PlayerJoinEvent(player, Component.empty()));
        assertNull(player.nextMessage(), "a second join the same market day must not re-ping");
    }

    @Test
    void anOnlineRotatePingAndALaterJoinTheSameDayNeverBothFire() {
        PlayerMock player = server.addPlayer();
        watches.add(player.getUniqueId(), "birch_log");

        watches.notifyRotate(List.of(player));
        assertTrue(player.nextMessage().contains("Lumberjack"));

        watches.onJoin(new PlayerJoinEvent(player, Component.empty()));
        assertNull(player.nextMessage(), "already told while online today");
    }

    @Test
    void watchesAndNotifiedDaysPersistAcrossARestart() {
        PlayerMock player = server.addPlayer();
        UUID id = player.getUniqueId();
        File stateFile = new File(dir, "persisted-watches.yml");
        MarketWatches first = new MarketWatches(market, layout, heads, () -> config, stateFile,
                Logger.getLogger("market-watches-test"));
        first.add(id, "birch_log");
        first.add(id, "diamond");
        first.notifyRotate(List.of(player));
        assertTrue(player.nextMessage() != null, "the first notify sends at least one line");
        player.nextMessage();

        MarketWatches reloaded = new MarketWatches(market, layout, heads, () -> config, stateFile,
                Logger.getLogger("market-watches-test"));
        assertEquals(List.of("birch_log", "diamond"), reloaded.watching(id), "the watch list survives a restart");

        reloaded.notifyRotate(List.of(player));
        assertNull(player.nextMessage(), "the notified-day bookkeeping survives a restart too, so this re-roll doesn't re-ping");
    }
}
