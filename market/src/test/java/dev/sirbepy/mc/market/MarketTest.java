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
import java.time.LocalDate;
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
    private final AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 10, 5));
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
        return new Market(layout, () -> config, today::get, new Random(seed), new File(dir, "state.yml"), LOG);
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
    void eachStallBuysTwoOrThreeDistinctItemsInPoolOrder() {
        for (long seed = 0; seed < 200; seed++) {
            Market market = market(seed);
            market.rotate();
            for (Stall stall : layout.stalls()) {
                List<Offer> buying = market.buying(stall.id());
                assertTrue(buying.size() >= 2 && buying.size() <= 3, stall.id() + " buys " + buying.size());
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
        for (int day = 1; day <= 10 && !changed; day++) {
            today.set(today.get().plusDays(1));
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
        today.set(today.get().plusDays(1));
        assertTrue(market.refresh());
        assertEquals(today.get(), market.day());
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
        state.set("day", today.get().toString());
        state.set("buying.farmer.wheat", "2.91");
        state.set("buying.farmer.no_longer_bought", "5");
        state.save(new File(dir, "state.yml"));

        Market market = market(0);
        assertFalse(market.refresh());
        assertEquals(List.of(new Offer("wheat", 64, new BigDecimal("2.91"))), market.buying("farmer"));
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
