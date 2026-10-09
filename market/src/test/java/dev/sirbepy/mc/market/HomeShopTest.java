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
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Homes shop (Joe, 2026-10-09): sells huskhomes.max_homes.4/5/6 above the default group's own
 * .3. {@link ConsoleHomeGrantTest} covers the real LuckPerms dispatch; this drives {@link HomeShop}
 * against a stub {@link HomeShop.HomeGrant} so each scenario (poor, double-click, grant failure)
 * is isolated from whether LuckPerms itself is installed. */
class HomeShopTest {

    private static final Logger LOG = Logger.getLogger("home-shop-test");

    @TempDir
    File dir;

    private ServerMock server;
    private MarketLayout layout;
    private MarketConfig config;
    private final List<Double> charged = new ArrayList<>();
    private final List<Double> refunded = new ArrayList<>();
    private final List<Integer> grants = new ArrayList<>();
    private double walletBalance = 100000;
    private boolean economyOpen = true;
    private boolean grantSucceeds = true;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        layout = MarketLayout.load(Objects.requireNonNull(getClass().getResourceAsStream("/test-stalls.yml")));
        var stream = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"));
        config = new MarketConfig(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private HomeShop shop() {
        return new HomeShop(layout, () -> config,
                (player, amount) -> {
                    if (!economyOpen || walletBalance < amount) {
                        return false;
                    }
                    walletBalance -= amount;
                    charged.add(amount);
                    return true;
                },
                player -> economyOpen ? walletBalance : null,
                (player, amount) -> {
                    walletBalance += amount;
                    refunded.add(amount);
                    return true;
                },
                (playerId, next) -> {
                    if (grantSucceeds) {
                        grants.add(next);
                    }
                    return grantSucceeds;
                },
                new File(dir, "homes.yml"), LOG);
    }

    @Test
    void firstPurchaseChargesFiveHundredAndGrantsTheFourthHome() {
        PlayerMock player = server.addPlayer();
        shop().purchase(player);
        assertEquals(List.of(500.0), charged);
        assertEquals(List.of(4), grants);
        assertTrue(player.nextMessage().contains("set 4 homes"));
    }

    @Test
    void secondPurchaseChargesFifteenHundredForTheFifthHome() {
        PlayerMock player = server.addPlayer();
        HomeShop shop = shop();
        shop.purchase(player);
        player.nextMessage(); // drains the 4th home's "success" message
        shop.purchase(player);
        assertEquals(List.of(500.0, 1500.0), charged);
        assertEquals(List.of(4, 5), grants);
        assertTrue(player.nextMessage().contains("set 5 homes"));
    }

    @Test
    void atSixHomesTheShopRefusesAndChargesNothing() {
        PlayerMock player = server.addPlayer();
        HomeShop shop = shop();
        shop.purchase(player);
        shop.purchase(player);
        shop.purchase(player);
        player.nextMessage();
        player.nextMessage();
        player.nextMessage();
        charged.clear();
        grants.clear();
        shop.purchase(player);
        assertTrue(charged.isEmpty());
        assertTrue(grants.isEmpty());
        assertTrue(player.nextMessage().contains("already have the most"));
    }

    @Test
    void anAdminWithTheHundredNodeAlreadyHasTheMostTooAndIsRefused() {
        PlayerMock admin = server.addPlayer();
        admin.addAttachment(MockBukkit.createMockPlugin()).setPermission("huskhomes.max_homes.100", true);
        shop().purchase(admin);
        assertTrue(charged.isEmpty());
        assertTrue(grants.isEmpty());
        assertTrue(admin.nextMessage().contains("already have the most"));
    }

    @Test
    void aPoorPlayerIsRefusedWithTheirBalanceAndNotCharged() {
        walletBalance = 100;
        PlayerMock player = server.addPlayer();
        shop().purchase(player);
        assertTrue(charged.isEmpty());
        String message = player.nextMessage();
        assertTrue(message.contains("$500"), message);
        assertTrue(message.contains("$100"), message);
    }

    @Test
    void aFastDoubleClickChargesOnlyOnceAndThenOffersTheFifthHome() {
        // Simulates LuckPerms not having applied the first grant yet: no permission reflects it,
        // only this shop's own sale record does - which is exactly what must stop a double charge.
        PlayerMock player = server.addPlayer();
        HomeShop shop = shop();
        shop.purchase(player);
        assertEquals(List.of(500.0), charged);
        shop.purchase(player);
        assertEquals(List.of(500.0, 1500.0), charged, "the second click bought the 5th home, not a second 4th");
    }

    @Test
    void whenTheGrantDispatchFailsTheSaleIsRefundedAndNotRecorded() {
        grantSucceeds = false;
        PlayerMock player = server.addPlayer();
        HomeShop shop = shop();
        shop.purchase(player);
        assertEquals(List.of(500.0), charged);
        assertEquals(List.of(500.0), refunded);
        assertTrue(player.nextMessage().contains("can't process"));

        // Record was rolled back, so a retry (say LuckPerms comes back) still offers the 4th, not the 5th.
        grantSucceeds = true;
        shop.purchase(player);
        assertEquals(List.of(500.0, 500.0), charged);
        assertEquals(List.of(4), grants);
    }

    @Test
    void signShowsTheThreeTierPrices() {
        MarketLayout.Shop homes = layout.shops().get("homes");
        WorldMock world = server.addSimpleWorld("world");
        shop().apply(world);

        var block = world.getBlockAt(homes.sign().x(), homes.sign().y(), homes.sign().z());
        assertEquals(Material.SPRUCE_WALL_SIGN, block.getType());
        assertEquals(BlockFace.WEST, ((Directional) block.getBlockData()).getFacing());
        Sign sign = (Sign) block.getState();
        assertEquals(Component.text("Extra home").decorate(TextDecoration.BOLD), sign.getSide(Side.FRONT).line(0));
        assertEquals(Component.text("4th: $500"), sign.getSide(Side.FRONT).line(1));
        assertEquals(Component.text("5th: $1,500"), sign.getSide(Side.FRONT).line(2));
        assertEquals(Component.text("6th: $4,000"), sign.getSide(Side.FRONT).line(3));
        assertTrue(sign.getSide(Side.FRONT).isGlowingText());
    }

    @Test
    void rightClickingTheSignBuysAHomeAndCancelsTheClick() {
        PlayerMock player = server.addPlayer();
        WorldMock world = server.addSimpleWorld("world");
        HomeShop shop = shop();
        shop.apply(world);
        MarketLayout.Shop homes = layout.shops().get("homes");
        var block = world.getBlockAt(homes.sign().x(), homes.sign().y(), homes.sign().z());

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                ItemStack.empty(), block, BlockFace.WEST, EquipmentSlot.HAND);
        shop.onInteract(event);

        assertTrue(event.isCancelled(), "no sign-edit screen opens on our own sign");
        assertEquals(List.of(500.0), charged);
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
}
