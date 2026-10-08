package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.ItemNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PriceBookTest {

    private ServerMock server;
    private PriceBook book;
    private MarketConfig config;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        book = PriceBook.load(Objects.requireNonNull(getClass().getResourceAsStream("/prices.yml")));
        var stream = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"));
        config = new MarketConfig(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private double each(ItemStack stack) {
        PriceBook.Quote quote = book.quote(stack);
        assertEquals(PriceBook.Kind.PRICED, quote.kind(), stack.getType().name());
        return quote.each();
    }

    @Test
    void marketItemsAreTwiceTheStallBasePrice() {
        assertEquals(20.0, each(new ItemStack(Material.DIAMOND)), 1e-9); // stall base $10
        assertEquals(1.25, each(new ItemStack(Material.RAW_IRON)), 1e-9); // stall base $0.625
    }

    @Test
    void craftedItemsCostTheirRecipe() {
        // 3 diamonds + 2 sticks, a stick being half a plank from a $0.90 log.
        assertEquals(60.0, each(new ItemStack(Material.DIAMOND_PICKAXE)), 0.3);
        assertEquals(180.0, each(new ItemStack(Material.DIAMOND_BLOCK)), 1e-9);
    }

    private static ItemStack book(Enchantment enchantment, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(enchantment, level, true);
        book.setItemMeta(meta);
        return book;
    }

    @Test
    void enchantmentsAddTheirMaxLevelPriceByRarity() {
        ItemStack plain = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemStack enchanted = new ItemStack(Material.DIAMOND_PICKAXE);
        enchanted.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5); // common: a diamond
        enchanted.addUnsafeEnchantment(Enchantment.UNBREAKING, 3); // uncommon
        enchanted.addUnsafeEnchantment(Enchantment.FORTUNE, 3); // rare
        enchanted.addUnsafeEnchantment(Enchantment.MENDING, 1); // treasure, set by hand
        assertEquals(each(plain) + 20 + 30 + 40 + 100, each(enchanted), 1e-9);
    }

    @Test
    void enchantedBooksCountTheirStoredEnchantments() {
        assertEquals(0.5 + 100, each(book(Enchantment.MENDING, 1)), 1e-9);
        assertEquals(0.5 + 60, each(book(Enchantment.SILK_TOUCH, 1)), 1e-9); // very rare
        assertEquals(0.5 + 90, each(book(Enchantment.SWIFT_SNEAK, 3)), 1e-9); // very rare treasure, 1.5x
    }

    @Test
    void eachLevelBelowMaxHalvesAndEachLevelPastMaxAddsItAgain() {
        assertEquals(0.5 + 20, each(book(Enchantment.SHARPNESS, 5)), 1e-9);
        assertEquals(0.5 + 10, each(book(Enchantment.SHARPNESS, 4)), 1e-9);
        assertEquals(0.5 + 1.25, each(book(Enchantment.SHARPNESS, 1)), 1e-9);
        assertEquals(0.5 + 20 * 6, each(book(Enchantment.SHARPNESS, 10)), 1e-9);
    }

    @Test
    void cursesCutTheWholePrice() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        double plain = each(sword);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        sword.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
        assertEquals((plain + 20) * 0.75, each(sword), 1e-9);
        sword.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1);
        assertEquals((plain + 20) * 0.75 * 0.5, each(sword), 1e-9);
    }

    @Test
    void wearOnlyScalesTheItemNotItsEnchantments() {
        ItemStack worn = new ItemStack(Material.DIAMOND_PICKAXE);
        worn.addUnsafeEnchantment(Enchantment.MENDING, 1);
        Damageable meta = (Damageable) worn.getItemMeta();
        meta.setDamage(Material.DIAMOND_PICKAXE.getMaxDurability() / 2);
        worn.setItemMeta(meta);
        PriceBook.Quote quote = book.quote(worn);
        assertEquals(each(new ItemStack(Material.DIAMOND_PICKAXE)) / 2, quote.item(), 0.05);
        assertEquals(quote.item() + 100, quote.each(), 1e-9);
        assertEquals(0.5, quote.wear(), 0.001);
    }

    @Test
    void wearScalesThePriceDown() {
        ItemStack fresh = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemStack worn = new ItemStack(Material.DIAMOND_PICKAXE);
        Damageable meta = (Damageable) worn.getItemMeta();
        meta.setDamage(Material.DIAMOND_PICKAXE.getMaxDurability() / 2);
        worn.setItemMeta(meta);
        assertEquals(each(fresh) / 2, each(worn), 0.05);
    }

    @Test
    void potionsAddTheirContents() {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.setBasePotionType(PotionType.STRONG_SWIFTNESS);
        potion.setItemMeta(meta);
        assertEquals(0.05 + 1.5, each(potion), 1e-9);
    }

    @Test
    void creativeOnlyItemsAreUntradeable() {
        PriceBook.Quote quote = book.quote(new ItemStack(Material.COW_SPAWN_EGG));
        assertEquals(PriceBook.Kind.UNTRADEABLE, quote.kind());
        assertNull(quote.each());
        assertEquals(PriceBook.Kind.UNTRADEABLE, book.quote(new ItemStack(Material.COMMAND_BLOCK)).kind());
    }

    @Test
    void itemsMissingFromTheBookAreUnknown() {
        PriceBook empty = new PriceBook(new YamlConfiguration());
        assertEquals(PriceBook.Kind.UNKNOWN, empty.quote(new ItemStack(Material.DIAMOND)).kind());
    }

    @Test
    void moneyRoundsToTheCent() {
        assertEquals("$48.08", PriceCommand.money(48.0833));
        assertEquals("$0.04", PriceCommand.money(0.035));
        assertEquals("under $0.01", PriceCommand.money(0.004));
    }

    // MockBukkit doesn't implement ItemStack.effectiveName() or Enchantment.displayName(); the bot
    // e2e covers the real names.
    private PriceCommand command() {
        return new PriceCommand(book, () -> config,
                stack -> Component.text(ItemNames.title(stack.getType().getKey().getKey())),
                (enchantment, level) -> Component.text(ItemNames.title(enchantment.getKey().getKey()) + " " + level));
    }

    private List<String> messages(PlayerMock player) {
        List<String> all = new ArrayList<>();
        for (var c = player.nextComponentMessage(); c != null; c = player.nextComponentMessage()) {
            all.add(PlainTextComponentSerializer.plainText().serialize(c));
        }
        return all;
    }

    private String lastMessage(PlayerMock player) {
        List<String> all = messages(player);
        return all.isEmpty() ? null : all.getLast();
    }

    @Test
    void commandQuotesEachAndStackForStackableItems() {
        PlayerMock player = server.addPlayer("Pricer");
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 3));
        command().run(player);
        assertEquals("Suggested price for Diamond: $20.00 each, $1280.00 for a stack of 64.", lastMessage(player));
    }

    @Test
    void commandQuotesOneItemForUnstackables() {
        PlayerMock player = server.addPlayer("Pricer");
        player.getInventory().setItemInMainHand(new ItemStack(Material.ELYTRA));
        command().run(player);
        assertEquals("Suggested price for Elytra: $100.00.", lastMessage(player));
    }

    @Test
    void commandBreaksDownAnEnchantedItem() {
        PlayerMock player = server.addPlayer("Pricer");
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        sword.addUnsafeEnchantment(Enchantment.MENDING, 1);
        sword.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
        Damageable meta = (Damageable) sword.getItemMeta();
        meta.setDamage(Material.DIAMOND_SWORD.getMaxDurability() / 4);
        sword.setItemMeta(meta);
        player.getInventory().setItemInMainHand(sword);
        PriceBook.Quote quote = book.quote(sword);
        command().run(player);
        List<String> lines = messages(player);
        assertEquals(List.of(
                "Suggested price for Diamond Sword: " + PriceCommand.money(quote.each()) + ".",
                " - Diamond sword (25% worn) " + PriceCommand.money(quote.item()),
                " - Sharpness 5 +$20.00",
                " - Mending 1 +$100.00",
                " - Vanishing Curse 1 -25%"), sortedEnchantLines(lines));
    }

    // An item's enchantments come back in no promised order; the curse always comes last.
    private static List<String> sortedEnchantLines(List<String> lines) {
        List<String> out = new ArrayList<>(lines.subList(0, 2));
        List<String> adds = new ArrayList<>(lines.subList(2, lines.size() - 1));
        adds.sort(Comparator.comparing((String l) -> !l.contains("Sharpness")));
        out.addAll(adds);
        out.add(lines.getLast());
        return out;
    }

    @Test
    void commandShowsNoBreakdownForAPlainItem() {
        PlayerMock player = server.addPlayer("Pricer");
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        command().run(player);
        assertEquals(1, messages(player).size());
    }

    @Test
    void commandAsksForAnItemWhenTheHandIsEmpty() {
        PlayerMock player = server.addPlayer("Pricer");
        command().run(player);
        assertEquals("Hold the item you want priced in your main hand.", lastMessage(player));
    }
}
