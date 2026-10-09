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

/** The Homes shop (Joe, 2026-10-09): one wall sign per home it sells - huskhomes.max_homes.4/5/6
 * above the default group's own .3 - right-click the one you're up to. {@link ConsoleHomeGrantTest}
 * covers the real LuckPerms dispatch; this drives {@link HomeShop} against a stub
 * {@link HomeShop.HomeGrant} so each scenario (poor, double-click, grant failure, wrong sign) is
 * isolated from whether LuckPerms itself is installed. */
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
    void theFourthHomeSignChargesFiveHundredAndGrantsIt() {
        PlayerMock player = server.addPlayer();
        shop().purchase(player, 4);
        assertEquals(List.of(500.0), charged);
        assertEquals(List.of(4), grants);
        assertTrue(player.nextMessage().contains("set 4 homes"));
    }

    @Test
    void afterTheFourthTheFifthSignIsTheNextOneAndCharges1500() {
        PlayerMock player = server.addPlayer();
        HomeShop shop = shop();
        shop.purchase(player, 4);
        player.nextMessage(); // drains the 4th home's "success" message
        shop.purchase(player, 5);
        assertEquals(List.of(500.0, 1500.0), charged);
        assertEquals(List.of(4, 5), grants);
        assertTrue(player.nextMessage().contains("set 5 homes"));
    }

    @Test
    void clickingAnEarlierSignYouAlreadyOwnSaysSoAndChargesNothing() {
        PlayerMock player = server.addPlayer();
        HomeShop shop = shop();
        shop.purchase(player, 4);
        player.nextMessage();
        charged.clear();
        grants.clear();
        shop.purchase(player, 4);
        assertTrue(charged.isEmpty());
        assertTrue(grants.isEmpty());
        assertTrue(player.nextMessage().contains("already have your 4th home"));
    }

    @Test
    void clickingALaterSignBeforeTheOnesBeforeItSaysBuyThoseFirst() {
        PlayerMock player = server.addPlayer();
        // Still at the base 3 homes: the next sign to buy is the 4th, not the 6th.
        shop().purchase(player, 6);
        assertTrue(charged.isEmpty());
        assertTrue(grants.isEmpty());
        assertTrue(player.nextMessage().contains("Buy your 4th home first"));
    }

    @Test
    void atSixHomesEverySignRefusesAndChargesNothing() {
        PlayerMock player = server.addPlayer();
        HomeShop shop = shop();
        shop.purchase(player, 4);
        shop.purchase(player, 5);
        shop.purchase(player, 6);
        player.nextMessage();
        player.nextMessage();
        player.nextMessage();
        charged.clear();
        grants.clear();
        shop.purchase(player, 6);
        assertTrue(charged.isEmpty());
        assertTrue(grants.isEmpty());
        assertTrue(player.nextMessage().contains("already have the most"));
    }

    @Test
    void anAdminWithTheHundredNodeAlreadyHasTheMostTooAndIsRefused() {
        PlayerMock admin = server.addPlayer();
        admin.addAttachment(MockBukkit.createMockPlugin()).setPermission("huskhomes.max_homes.100", true);
        shop().purchase(admin, 4);
        assertTrue(charged.isEmpty());
        assertTrue(grants.isEmpty());
        assertTrue(admin.nextMessage().contains("already have the most"));
    }

    @Test
    void aPoorPlayerIsRefusedWithTheirBalanceAndNotCharged() {
        walletBalance = 100;
        PlayerMock player = server.addPlayer();
        shop().purchase(player, 4);
        assertTrue(charged.isEmpty());
        String message = player.nextMessage();
        assertTrue(message.contains("$500"), message);
        assertTrue(message.contains("$100"), message);
    }

    @Test
    void aFastDoubleClickOnTheSameSignChargesOnlyOnceAndThenSaysAlreadyOwned() {
        // Simulates LuckPerms not having applied the first grant yet: no permission reflects it,
        // only this shop's own sale record does - which is exactly what must stop a double charge.
        PlayerMock player = server.addPlayer();
        HomeShop shop = shop();
        shop.purchase(player, 4);
        assertEquals(List.of(500.0), charged);
        player.nextMessage();
        shop.purchase(player, 4);
        assertEquals(List.of(500.0), charged, "the second click on the same sign charged nothing more");
        assertTrue(player.nextMessage().contains("already have your 4th home"));
    }

    @Test
    void whenTheGrantDispatchFailsTheSaleIsRefundedAndNotRecorded() {
        grantSucceeds = false;
        PlayerMock player = server.addPlayer();
        HomeShop shop = shop();
        shop.purchase(player, 4);
        assertEquals(List.of(500.0), charged);
        assertEquals(List.of(500.0), refunded);
        assertTrue(player.nextMessage().contains("can't process"));

        // Record was rolled back, so a retry (say LuckPerms comes back) still offers the 4th, not the 5th.
        grantSucceeds = true;
        shop.purchase(player, 4);
        assertEquals(List.of(500.0, 500.0), charged);
        assertEquals(List.of(4), grants);
    }

    @Test
    void eachSignShowsItsOwnHomeNumberAndPrice() {
        MarketLayout.Shop homes = layout.shops().get("homes");
        WorldMock world = server.addSimpleWorld("world");
        shop().apply(world);

        assertEquals(3, homes.signs().size());
        assertSignReads(world, homes.signs().get(0), "4th home", "$500");
        assertSignReads(world, homes.signs().get(1), "5th home", "$1,500");
        assertSignReads(world, homes.signs().get(2), "6th home", "$4,000");
    }

    private static void assertSignReads(WorldMock world, Stall.Slot slot, String title, String price) {
        var block = world.getBlockAt(slot.x(), slot.y(), slot.z());
        assertEquals(Material.SPRUCE_WALL_SIGN, block.getType());
        assertEquals(BlockFace.WEST, ((Directional) block.getBlockData()).getFacing());
        Sign sign = (Sign) block.getState();
        assertEquals(Component.text(title).decorate(TextDecoration.BOLD), sign.getSide(Side.FRONT).line(0));
        assertEquals(Component.text(price), sign.getSide(Side.FRONT).line(1));
        assertEquals(Component.text("Right-click"), sign.getSide(Side.FRONT).line(2));
        assertEquals(Component.text("to buy"), sign.getSide(Side.FRONT).line(3));
        assertTrue(sign.getSide(Side.FRONT).isGlowingText());
    }

    @Test
    void rightClickingTheFourthHomeSignBuysItAndCancelsTheClick() {
        PlayerMock player = server.addPlayer();
        WorldMock world = server.addSimpleWorld("world");
        HomeShop shop = shop();
        shop.apply(world);
        MarketLayout.Shop homes = layout.shops().get("homes");
        var block = world.getBlockAt(homes.signs().get(0).x(), homes.signs().get(0).y(), homes.signs().get(0).z());

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                ItemStack.empty(), block, BlockFace.WEST, EquipmentSlot.HAND);
        shop.onInteract(event);

        assertTrue(event.isCancelled(), "no sign-edit screen opens on our own sign");
        assertEquals(List.of(500.0), charged);
    }

    @Test
    void rightClickingTheSixthHomeSignBeforeTheFifthRefusesWithNoCharge() {
        PlayerMock player = server.addPlayer();
        WorldMock world = server.addSimpleWorld("world");
        HomeShop shop = shop();
        shop.apply(world);
        MarketLayout.Shop homes = layout.shops().get("homes");
        shop.purchase(player, 4);
        player.nextMessage();
        charged.clear();
        var block = world.getBlockAt(homes.signs().get(2).x(), homes.signs().get(2).y(), homes.signs().get(2).z());

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                ItemStack.empty(), block, BlockFace.WEST, EquipmentSlot.HAND);
        shop.onInteract(event);

        assertTrue(event.isCancelled());
        assertTrue(charged.isEmpty());
        assertTrue(player.nextMessage().contains("Buy your 5th home first"));
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
