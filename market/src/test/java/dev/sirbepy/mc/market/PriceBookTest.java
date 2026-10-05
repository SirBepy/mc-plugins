package dev.sirbepy.mc.market;

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
        assertEquals(16.0, each(new ItemStack(Material.DIAMOND)), 1e-9); // stall base $8
        assertEquals(1.0, each(new ItemStack(Material.RAW_IRON)), 1e-9); // stall base $0.50
    }

    @Test
    void craftedItemsCostTheirRecipe() {
        // 3 diamonds + 2 sticks, a stick being half a plank from a $1 log.
        assertEquals(48.0, each(new ItemStack(Material.DIAMOND_PICKAXE)), 0.1);
        assertEquals(144.0, each(new ItemStack(Material.DIAMOND_BLOCK)), 1e-9);
    }

    @Test
    void enchantmentsAddPerLevel() {
        ItemStack plain = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemStack enchanted = new ItemStack(Material.DIAMOND_PICKAXE);
        enchanted.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        enchanted.addUnsafeEnchantment(Enchantment.MENDING, 1);
        assertEquals(each(plain) + 5 * 3 + 20, each(enchanted), 1e-9);
    }

    @Test
    void enchantedBooksCountTheirStoredEnchantments() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(Enchantment.MENDING, 1, true);
        book.setItemMeta(meta);
        assertEquals(0.5 + 20, each(book), 1e-9);
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

    // MockBukkit doesn't implement ItemStack.effectiveName(); the bot e2e covers the real name.
    private PriceCommand command() {
        return new PriceCommand(book, () -> config, stack -> net.kyori.adventure.text.Component.text(
                stack.getType() == Material.DIAMOND ? "Diamond" : "Elytra"));
    }

    private String lastMessage(PlayerMock player) {
        String last = null;
        for (var c = player.nextComponentMessage(); c != null; c = player.nextComponentMessage()) {
            last = PlainTextComponentSerializer.plainText().serialize(c);
        }
        return last;
    }

    @Test
    void commandQuotesEachAndStackForStackableItems() {
        PlayerMock player = server.addPlayer("Pricer");
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 3));
        command().run(player);
        assertEquals("Suggested price for Diamond: $16.00 each, $1024.00 for a stack of 64.", lastMessage(player));
    }

    @Test
    void commandQuotesOneItemForUnstackables() {
        PlayerMock player = server.addPlayer("Pricer");
        player.getInventory().setItemInMainHand(new ItemStack(Material.ELYTRA));
        command().run(player);
        assertEquals("Suggested price for Elytra: $100.00.", lastMessage(player));
    }

    @Test
    void commandAsksForAnItemWhenTheHandIsEmpty() {
        PlayerMock player = server.addPlayer("Pricer");
        command().run(player);
        assertEquals("Hold the item you want priced in your main hand.", lastMessage(player));
    }
}
