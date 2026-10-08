package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.ItemNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceListTest {

    private MarketLayout layout;
    private MarketConfig config;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        layout = MarketLayout.load(Objects.requireNonNull(getClass().getResourceAsStream("/stalls.yml")));
        var stream = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"));
        config = new MarketConfig(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private BigDecimal base(String item) {
        return layout.stalls().stream().flatMap(s -> s.buys().stream()).filter(o -> o.item().equals(item))
                .findFirst().orElseThrow(() -> new AssertionError(item + " is not bought")).price();
    }

    @Test
    void tallTreeLogsPayHalfAndNetherStemsTwentyPercentMore() {
        BigDecimal log = base("oak_log");
        for (String tall : List.of("spruce_log", "jungle_log", "dark_oak_log", "pale_oak_log")) {
            assertEquals(0, base(tall).compareTo(log.divide(BigDecimal.TWO)), tall);
        }
        for (String plain : List.of("birch_log", "acacia_log", "mangrove_log", "cherry_log")) {
            assertEquals(0, base(plain).compareTo(log), plain);
        }
        for (String stem : List.of("crimson_stem", "warped_stem")) {
            assertEquals(0, base(stem).compareTo(log.multiply(new BigDecimal("1.2"))), stem);
        }
    }

    @Test
    void shippedLayoutNamesItsStallsAndPlacesThePriceLectern() {
        assertEquals(List.of("Farmer", "Lumberjack", "Fishmonger", "Smith", "Mob drops", "Rare goods"),
                layout.stalls().stream().map(Stall::name).toList());
        assertNotNull(layout.priceLectern(), "stalls.yml has a price-lectern");
    }

    @Test
    void bookListsEveryStallItemAtItsBasePriceOnPagesThatFit() {
        List<Component> pages = PriceList.pages(layout, config);
        assertTrue(plain(pages.getFirst()).contains("20%"), plain(pages.getFirst()));
        String all = pages.stream().map(PriceListTest::plain).collect(Collectors.joining("\n"));
        for (Stall stall : layout.stalls()) {
            assertTrue(all.contains(stall.name()), stall.name());
            for (Offer offer : stall.buys()) {
                String entry = PriceList.name(offer.item()) + "\n  " + offer.amount() + " for " + PriceList.money(offer.price());
                assertTrue(all.contains(entry), entry);
            }
        }
        assertTrue(all.contains("Dark oak log\n  64 for $14.40"), "a tall log at half the $28.80 a normal log pays");
        assertTrue(all.contains("Wither skeleton skull\n  1 for $20.00"), "the withersskull sign alias spelled out");
        for (Component page : pages.subList(1, pages.size())) {
            assertTrue(plain(page).split("\n").length <= 14, plain(page));
        }
    }

    @Test
    void todayListsEachStallsPicksAtTodaysPrice() {
        Map<String, List<Offer>> picks = Map.of(
                "lumberjack", List.of(new Offer("dark_oak_log", 64, new BigDecimal("13.9")),
                        new Offer("warped_stem", 64, new BigDecimal("36.01"))),
                "smith", List.of(new Offer("diamond", 16, new BigDecimal("150"))),
                "rare_goods", List.of(new Offer("withersskull", 1, new BigDecimal("21.5"))));
        Function<String, List<Offer>> buying = id -> picks.getOrDefault(id, List.of());

        List<String> lines = PriceList.today(layout, buying, config).stream().map(PriceListTest::plain).toList();

        assertEquals(List.of("Today the spawn market buys:",
                "Lumberjack: 64 dark oak log $13.90, 64 warped stem $36.01",
                "Smith: 16 diamond $150.00",
                "Rare goods: 1 wither skeleton skull $21.50"), lines);
    }

    @Test
    void itemNamesReadAsWords() {
        assertEquals("Dark oak log", ItemNames.sentence("dark_oak_log"));
        assertEquals("Membrane", ItemNames.sentence("membrane"));
    }
}
