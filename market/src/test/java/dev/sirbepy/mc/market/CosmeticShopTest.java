package dev.sirbepy.mc.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Cosmetics shop's sign: sells one {@link CosmeticToken} for $2,500 (Joe, 2026-10-09). */
class CosmeticShopTest {

    private ServerMock server;
    private PluginMock plugin;
    private MarketLayout layout;
    private MarketConfig config;
    private CosmeticToken token;
    private final List<Double> charged = new ArrayList<>();
    private double walletBalance = 100000;
    private boolean economyOpen = true;
    private Double debt = 0.0;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        layout = MarketLayout.load(Objects.requireNonNull(getClass().getResourceAsStream("/test-stalls.yml")));
        var stream = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"));
        config = new MarketConfig(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        token = new CosmeticToken(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private CosmeticShop shop() {
        return new CosmeticShop(layout, () -> config, token,
                (player, amount) -> {
                    if (!economyOpen || walletBalance < amount) {
                        return false;
                    }
                    walletBalance -= amount;
                    charged.add(amount);
                    return true;
                },
                player -> economyOpen ? walletBalance : null,
                player -> debt);
    }

    @Test
    void signShowsThePriceAndRightClickBuysAToken() {
        PlayerMock player = server.addPlayer();
        WorldMock world = server.addSimpleWorld("world");
        CosmeticShop shop = shop();
        shop.apply(world);

        MarketLayout.Shop cosmetics = layout.shops().get("cosmetics");
        Stall.Slot slot = cosmetics.signs().get(0);
        var block = world.getBlockAt(slot.x(), slot.y(), slot.z());
        assertEquals(Material.SPRUCE_WALL_SIGN, block.getType());
        Sign sign = (Sign) block.getState();
        assertEquals(Component.text("Cosmetic").decorate(TextDecoration.BOLD), sign.getSide(Side.FRONT).line(0));
        assertEquals(Component.text("Token"), sign.getSide(Side.FRONT).line(1));
        assertEquals(Component.text("$2,500"), sign.getSide(Side.FRONT).line(2));
        assertEquals(Component.text("Right-click"), sign.getSide(Side.FRONT).line(3));
        assertTrue(sign.getSide(Side.FRONT).isGlowingText());

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, ItemStack.empty(),
                block, slot.facing(), EquipmentSlot.HAND);
        shop.onInteract(event);

        assertTrue(event.isCancelled());
        assertEquals(List.of(2500.0), charged);
        ItemStack given = player.getInventory().getItemInMainHand();
        assertTrue(token.is(given), "the sign gives a real token");
    }

    @Test
    void aFullInventoryDropsTheTokenAtTheirFeet() {
        PlayerMock player = server.addPlayer();
        WorldMock world = server.addSimpleWorld("world");
        player.teleport(world.getSpawnLocation());
        fillInventory(player);
        int droppedBefore = world.getEntities().size();

        shop().buy(player);

        assertEquals(List.of(2500.0), charged);
        assertTrue(world.getEntities().size() > droppedBefore, "the token was dropped since the inventory was full");
    }

    // MockBukkit's addItem() looks for an empty slot across the whole backing array, armor/offhand
    // included (same fix as RadarShopTest.fillInventory), so "full" means every slot getSize()
    // reports, not just getStorageContents().
    private static void fillInventory(PlayerMock player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItem(slot, ItemStack.of(Material.STONE, 64));
        }
    }

    @Test
    void cannotAffordRefusesWithTheBalanceAndChargesNothing() {
        walletBalance = 10;
        PlayerMock player = server.addPlayer();
        shop().buy(player);
        assertTrue(charged.isEmpty());
        String message = player.nextMessage();
        assertTrue(message.contains("$2,500"), message);
        assertTrue(message.contains("$10"), message);
    }

    @Test
    void inDebtBlocksThePurchaseAndChargesNothingEvenWithEnoughMoney() {
        debt = 25000.0;
        PlayerMock player = server.addPlayer();
        shop().buy(player);
        assertTrue(charged.isEmpty(), "blocked by debt before ever reaching the charge");
        assertTrue(player.nextMessage().contains("$25,000"));
    }

    @Test
    void noEconomyClosesTheShop() {
        economyOpen = false;
        PlayerMock player = server.addPlayer();
        shop().buy(player);
        assertTrue(charged.isEmpty());
        assertTrue(player.nextMessage().contains("can't process"));
    }

    @Test
    void clickingAnUnrelatedBlockDoesNothing() {
        PlayerMock player = server.addPlayer();
        WorldMock world = server.addSimpleWorld("world");
        var block = world.getBlockAt(0, 70, 0);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, ItemStack.empty(),
                block, BlockFace.WEST, EquipmentSlot.HAND);
        shop().onInteract(event);
        assertFalse(event.isCancelled());
        assertTrue(charged.isEmpty());
    }

    @Test
    void aTokenIsRecognisedByItsPdcFlagNotByNameOrLook() {
        ItemStack renamedStar = ItemStack.of(Material.NETHER_STAR);
        renamedStar.editMeta(meta -> meta.displayName(Component.text("Cosmetic Token")));
        assertFalse(token.is(renamedStar), "name alone never makes it a token");
        assertTrue(token.is(token.create()));
    }
}
