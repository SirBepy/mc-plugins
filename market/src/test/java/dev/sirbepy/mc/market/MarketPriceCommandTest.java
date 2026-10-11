package dev.sirbepy.mc.market;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /market price}: top/median/bottom per bundle and per item from the stalls.yml base and the
 * shipped price-spread (0.2), which stall buys it, today's price or "not today", a mob's heads, the
 * held-item form, and a clear refusal for anything no stall buys. The smith has 2 items for 1 sign so
 * one of them is genuinely off the stall today; the test reads back which, like MarketWatchesTest.
 */
class MarketPriceCommandTest {

    private static final String STALLS = """
            sign-block: spruce_wall_sign
            head-stall:
              name: Heads
              color: red
              slots:
                - {x: 10, y: 70, z: 0, facing: south}
            stalls:
              smith:
                name: Smith
                color: gray
                slots:
                  - {x: 1, y: 70, z: 0, facing: south}
                buys:
                  - {item: raw_gold, amount: 64, price: 88}
                  - {item: raw_iron, amount: 64, price: 44}
              rare:
                name: Rare goods
                color: purple
                slots:
                  - {x: 2, y: 70, z: 0, facing: south}
                buys:
                  - {item: membrane, amount: 16, price: 32}
                  - {item: dragon_head, amount: 1, price: 100}
            """;

    private static final String HEADS = """
            mobs:
              axolotl:
                name: "Axolotl"
                heads:
                  - {name: "Pink Axolotl Head", texture: aaaa1111, price: 2}
                  - {name: "Blue Axolotl Head", texture: bbbb2222, price: 10}
              bee:
                name: "Bee"
                heads:
                  - {name: "Bee Head", texture: cccc3333, price: 2.5}
            """;

    @TempDir
    File dir;

    private ServerMock server;
    private Market market;
    private MarketPriceCommand command;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MarketLayout layout = MarketLayout.from(YamlConfiguration.loadConfiguration(new StringReader(STALLS)));
        MobHeads heads = new MobHeads(YamlConfiguration.loadConfiguration(new StringReader(HEADS)));
        var stream = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"));
        MarketConfig config = new MarketConfig(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        market = new Market(layout, heads, () -> config, () -> "day-1", new Random(7), new File(dir, "state.yml"),
                Logger.getLogger("market-price-test"));
        market.refresh();
        command = new MarketPriceCommand(market, () -> config);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Everything sent since the last call, colour codes stripped (PlayerMock hands back legacy text). */
    private List<String> messages() {
        List<String> lines = new ArrayList<>();
        for (String line = player.nextMessage(); line != null; line = player.nextMessage()) {
            lines.add(line.replaceAll("§.", ""));
        }
        return lines;
    }

    private Offer today(String stall, String item) {
        return market.buying(stall).stream().filter(o -> o.item().equals(item)).findFirst().orElse(null);
    }

    @Test
    void anItemShowsTopMedianAndBottomPerBundleAndPerItemAndWhichStallBuysIt() {
        command.show(player, "raw_gold");

        List<String> lines = messages();
        assertTrue(lines.get(0).contains("Raw gold") && lines.get(0).contains("20%"), lines.get(0));
        assertTrue(lines.get(1).contains("Smith") && lines.get(1).contains("64"), lines.get(1));
        // 88 * 1.2 and 88 * 0.8, the numbers sibling agents worked out by hand.
        assertEquals(" - Top $105.60 ($1.65 each)", lines.get(2));
        assertEquals(" - Median $88.00 ($1.38 each)", lines.get(3));
        assertEquals(" - Bottom $70.40 ($1.10 each)", lines.get(4));
        assertEquals(5 + 1, lines.size(), lines.toString());
    }

    @Test
    void todaysLineIsTheRolledPriceWhenOnTheStallAndSaysSoWhenNot() {
        String onToday = today("smith", "raw_gold") != null ? "raw_gold" : "raw_iron";
        String offToday = onToday.equals("raw_gold") ? "raw_iron" : "raw_gold";

        command.show(player, onToday);
        List<String> on = messages();
        assertTrue(on.getLast().startsWith(" - Today " + PriceList.money(today("smith", onToday).price())), on.getLast());

        command.show(player, offToday);
        assertEquals(" - Not on the stall today.", messages().getLast());
    }

    @Test
    void typedNamesTakeSpacesTheVanillaIdOfAnAliasedSignItemAndAMinecraftPrefix() {
        command.show(player, "Raw Gold");
        assertTrue(messages().get(0).contains("Raw gold"));

        command.show(player, "minecraft:phantom_membrane");
        List<String> lines = messages();
        assertTrue(lines.get(1).contains("Rare goods") && lines.get(1).contains("16"), lines.get(1));
        assertEquals(" - Median $32.00 ($2.00 each)", lines.get(3));
    }

    @Test
    void aSingleItemBundleLeavesOutThePerItemPart() {
        command.show(player, "dragon_head");
        List<String> lines = messages();
        assertEquals(" - Top $120.00", lines.get(2));
        assertEquals(" - Bottom $80.00", lines.get(4));
    }

    @Test
    void aMobIdPricesItsHeadsAtTheHeadsStallAsARangeAcrossItsLooks() {
        command.show(player, "axolotl");
        List<String> lines = messages();
        assertTrue(lines.get(0).contains("Axolotl heads"), lines.get(0));
        assertTrue(lines.get(1).contains("Heads"), lines.get(1));
        assertEquals(" - Top $2.40 to $12.00", lines.get(2));
        assertEquals(" - Median $2.00 to $10.00", lines.get(3));
        assertEquals(" - Bottom $1.60 to $8.00", lines.get(4));
        boolean wanted = market.wantedMobs().stream().anyMatch(m -> m.id().equals("axolotl"));
        assertEquals(wanted, lines.get(5).startsWith(" - Today "), lines.get(5));
    }

    @Test
    void theHeldItemIsPricedWhenNoNameIsGivenAndAnEmptyHandIsToldWhatToDo() {
        command.showHeld(player, player);
        assertTrue(messages().getFirst().contains("hold it in your main hand"));

        player.getInventory().setItemInMainHand(ItemStack.of(Material.PHANTOM_MEMBRANE));
        command.showHeld(player, player);
        assertTrue(messages().get(1).contains("Rare goods"));
    }

    @Test
    void anItemNoStallBuysGetsAClearReplyAndNothingElse() {
        command.show(player, "dirt");
        List<String> lines = messages();
        assertEquals(1, lines.size(), lines.toString());
        assertTrue(lines.getFirst().contains("No spawn stall buys dirt"), lines.getFirst());
        assertFalse(lines.getFirst().contains("Top"));
    }
}
