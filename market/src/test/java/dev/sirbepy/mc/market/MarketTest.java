package dev.sirbepy.mc.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketTest {

    private static final Logger LOG = Logger.getLogger("market-test");

    @TempDir
    File dir;

    private ServerMock server;
    private final AtomicReference<String> period = new AtomicReference<>("minecraft-day-100");
    private MarketLayout layout;
    private MarketConfig config;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        layout = MarketLayout.load(Objects.requireNonNull(getClass().getResourceAsStream("/stalls.yml")));
        var stream = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"));
        config = new MarketConfig(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Market market(long seed) {
        return new Market(layout, () -> config, period::get, new Random(seed), new File(dir, "state.yml"), LOG);
    }

    @Test
    void shippedLayoutHasSixStallsWithThreeSpotsEach() {
        assertEquals(List.of("farmer", "lumberjack", "fishmonger", "smith", "mob_drops", "rare_goods"),
                layout.stalls().stream().map(Stall::id).toList());
        for (Stall stall : layout.stalls()) {
            assertEquals(3, stall.slots().size(), stall.id());
        }
        assertEquals(Material.SPRUCE_WALL_SIGN, layout.signBlock());
    }

    @Test
    void eachStallBuysThreeDistinctItemsInPoolOrder() {
        for (long seed = 0; seed < 200; seed++) {
            Market market = market(seed);
            market.rotate();
            for (Stall stall : layout.stalls()) {
                List<Offer> buying = market.buying(stall.id());
                assertEquals(3, buying.size(), stall.id() + " buys " + buying.size());
                List<String> items = buying.stream().map(Offer::item).toList();
                assertEquals(items.size(), new HashSet<>(items).size(), "duplicate offer at " + stall.id());
                List<String> poolItems = stall.buys().stream().map(Offer::item).toList();
                List<Integer> order = items.stream().map(poolItems::indexOf).toList();
                assertTrue(order.stream().allMatch(i -> i >= 0), "picked an item outside the pool at " + stall.id());
                assertEquals(order.stream().sorted().toList(), order, "not in pool order at " + stall.id());
            }
        }
    }

    @Test
    void reloadedConfigAppliesFromTheNextRotation() {
        Market market = market(5);
        market.rotate();
        assertEquals(3, market.buying("farmer").size());

        // What /market reload does: a fresh MarketConfig swapped into the supplier the Market reads.
        YamlConfiguration edited = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
        edited.set("items-per-stall.min", 1);
        edited.set("items-per-stall.max", 1);
        config = new MarketConfig(edited);

        assertEquals(3, market.buying("farmer").size(), "today's picks stay until the market rotates");
        market.rotate();
        for (Stall stall : layout.stalls()) {
            assertEquals(1, market.buying(stall.id()).size(), stall.id());
        }
    }

    @Test
    void shippedConfigHasAReloadMessage() {
        var stream = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"));
        String message = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getString("messages.reload-success", "");
        assertFalse(message.isBlank(), "/market reload confirms with messages.reload-success");
    }

    @Test
    void pricesRollWithinTwentyPercentOfBaseToTheCent() {
        boolean cheaper = false;
        boolean dearer = false;
        for (long seed = 0; seed < 200; seed++) {
            Market market = market(seed);
            market.rotate();
            for (Stall stall : layout.stalls()) {
                for (Offer offer : market.buying(stall.id())) {
                    BigDecimal base = stall.buys().stream().filter(o -> o.item().equals(offer.item())).findFirst()
                            .orElseThrow().price();
                    BigDecimal low = base.multiply(new BigDecimal("0.8")).setScale(2, java.math.RoundingMode.HALF_UP)
                            .max(new BigDecimal("0.01"));
                    BigDecimal high = base.multiply(new BigDecimal("1.2")).setScale(2, java.math.RoundingMode.HALF_UP);
                    String what = stall.id() + " " + offer.item() + " " + offer.price() + " vs base " + base;
                    assertTrue(offer.price().compareTo(low) >= 0 && offer.price().compareTo(high) <= 0, what);
                    assertTrue(offer.price().scale() <= 2, what);
                    cheaper |= offer.price().compareTo(base) < 0;
                    dearer |= offer.price().compareTo(base) > 0;
                }
            }
        }
        assertTrue(cheaper && dearer, "prices should land both below and above base");
    }

    @Test
    void picksDifferBetweenDays() {
        Market market = market(1);
        market.refresh();
        var first = layout.stalls().stream().map(s -> market.buying(s.id())).toList();
        boolean changed = false;
        for (int day = 101; day <= 110 && !changed; day++) {
            period.set("minecraft-day-" + day);
            market.refresh();
            changed = !first.equals(layout.stalls().stream().map(s -> market.buying(s.id())).toList());
        }
        assertTrue(changed, "ten days of rotation never changed the picks");
    }

    @Test
    void refreshRotatesOnlyWhenTheDayChanges() {
        Market market = market(7);
        assertTrue(market.refresh(), "first start has no picks yet");
        assertFalse(market.refresh());
        period.set("minecraft-day-101");
        assertTrue(market.refresh());
        assertEquals("minecraft-day-101", market.day());
        assertFalse(market.refresh());
    }

    @Test
    void restartOnTheSameDayKeepsThePicks() {
        Market before = market(3);
        before.refresh();

        Market after = market(99);
        assertFalse(after.refresh(), "a restart the same day must not pick again");
        for (Stall stall : layout.stalls()) {
            assertEquals(before.buying(stall.id()), after.buying(stall.id()), stall.id());
        }
    }

    @Test
    void itemDroppedFromThePoolDisappearsFromSavedPicks() throws Exception {
        YamlConfiguration state = new YamlConfiguration();
        state.set("day", period.get());
        state.set("buying.farmer.wheat", "2.91");
        state.set("buying.farmer.no_longer_bought", "5");
        state.save(new File(dir, "state.yml"));

        Market market = market(0);
        assertFalse(market.refresh());
        assertEquals(List.of(new Offer("wheat", 64, new BigDecimal("2.91"))), market.buying("farmer"));
    }

    @Test
    void marketDayIsTheInGameDayByDefault() {
        Instant now = Instant.parse("2026-10-05T21:30:00Z");
        assertEquals("minecraft-day-0", config.period(0, now));
        assertEquals("minecraft-day-0", config.period(23_999, now), "still the same day just before sunrise");
        assertEquals("minecraft-day-1", config.period(24_000, now), "sunrise starts a new market day");
        assertEquals("minecraft-day-42", config.period(42 * 24_000L + 13_000, now), "night belongs to its day");
    }

    @Test
    void realDayModeUsesTheCalendarDateInTheZone() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("rotate-on", "real-day");
        yaml.set("zone", "Europe/Zagreb");
        MarketConfig real = new MarketConfig(yaml);
        assertEquals("2026-10-05", real.period(0, Instant.parse("2026-10-05T21:30:00Z")));
        assertEquals("2026-10-06", real.period(0, Instant.parse("2026-10-05T22:30:00Z")), "past midnight in Zagreb (UTC+2)");
    }

    @Test
    void priceTextMatchesTheSignFormat() {
        assertEquals("$3.2", new Offer("wheat", 64, new BigDecimal("3.20")).priceText());
        assertEquals("$16", new Offer("iron_ingot", 64, new BigDecimal("16.0")).priceText());
        assertEquals("$1.28", new Offer("sugar_cane", 64, new BigDecimal("1.28")).priceText());
        assertEquals("$400", new Offer("ancient_debris", 16, new BigDecimal("400")).priceText());
    }

    @Test
    void applyWritesDyedGlowingSellSignsAndClearsUnusedSpots() {
        WorldMock world = server.addSimpleWorld("world");
        Stall stall = new Stall("test", DyeColor.YELLOW,
                List.of(new Stall.Slot(0, 70, 0, BlockFace.SOUTH), new Stall.Slot(1, 70, 0, BlockFace.SOUTH),
                        new Stall.Slot(2, 70, 0, BlockFace.SOUTH)),
                List.of(new Offer("wheat", 64, new BigDecimal("3.2")), new Offer("carrot", 64, new BigDecimal("3.2"))));
        world.getBlockAt(2, 70, 0).setType(Material.SPRUCE_WALL_SIGN);

        SignWriter.write(world, Material.SPRUCE_WALL_SIGN, stall, stall.buys());

        Block first = world.getBlockAt(0, 70, 0);
        assertEquals(Material.SPRUCE_WALL_SIGN, first.getType());
        assertEquals(BlockFace.SOUTH, ((Directional) first.getBlockData()).getFacing());
        SignSide front = ((Sign) first.getState()).getSide(Side.FRONT);
        assertEquals(Component.text("[Sell]", NamedTextColor.DARK_BLUE), front.line(0));
        assertEquals(Component.text("64"), front.line(1));
        assertEquals(Component.text("wheat"), front.line(2));
        assertEquals(Component.text("$3.2"), front.line(3));
        assertEquals(DyeColor.YELLOW, front.getColor());
        assertTrue(front.isGlowingText());

        assertEquals(Component.text("carrot"), ((Sign) world.getBlockAt(1, 70, 0).getState()).getSide(Side.FRONT).line(2));
        assertEquals(Material.AIR, world.getBlockAt(2, 70, 0).getType(), "an unused spot is cleared");
    }

    @Test
    void applyUsesTodaysPicksForEveryStall() {
        WorldMock world = server.addSimpleWorld("world");
        Market market = market(5);
        market.refresh();
        market.apply(world);
        for (Stall stall : layout.stalls()) {
            List<Offer> buying = market.buying(stall.id());
            for (int i = 0; i < stall.slots().size(); i++) {
                Stall.Slot slot = stall.slots().get(i);
                Block block = world.getBlockAt(slot.x(), slot.y(), slot.z());
                if (i < buying.size()) {
                    assertEquals(Component.text(buying.get(i).item()),
                            ((Sign) block.getState()).getSide(Side.FRONT).line(2), stall.id());
                } else {
                    assertNotEquals(Material.SPRUCE_WALL_SIGN, block.getType(), stall.id());
                }
            }
        }
    }
}
