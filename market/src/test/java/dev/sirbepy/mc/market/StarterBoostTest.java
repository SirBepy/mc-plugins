package dev.sirbepy.mc.market;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Starter Shop Boost (Joe, 2026-10-11): sell-price multiplier for a player under the
 * all-time-peak-net-worth threshold, tapering to 1x as their lifetime bonus nears the cap. */
class StarterBoostTest {

    private static final String STALLS = """
            sign-block: spruce_wall_sign
            stalls:
              lumberjack:
                name: Lumberjack
                color: green
                slots:
                  - {x: 5, y: 70, z: 0, facing: north}
                buys:
                  - {item: oak_log, amount: 64, price: 10}
            """;

    @TempDir
    File dir;

    private ServerMock server;
    private YamlConfiguration yaml;
    private MarketConfig config;
    private Market market;
    private StarterBoost boost;
    private final Map<UUID, Double> balances = new HashMap<>();
    private final List<Double> paid = new ArrayList<>();
    private boolean bankOpen = true;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
        config = new MarketConfig(yaml);
        MarketLayout layout = MarketLayout.from(YamlConfiguration.loadConfiguration(new StringReader(STALLS)));
        market = new Market(layout, new MobHeads(new YamlConfiguration()), () -> config, () -> "day-1",
                new Random(7), new File(dir, "state.yml"), Logger.getLogger("starter-boost-test"));
        newBoost();
    }

    private void newBoost() {
        boost = new StarterBoost(market, () -> config, p -> balances.get(p.getUniqueId()),
                (p, amount) -> {
                    if (bankOpen) {
                        paid.add(amount);
                        balances.merge(p.getUniqueId(), amount, Double::sum);
                    }
                    return bankOpen;
                }, new File(dir, "starter-boost.yml"), Logger.getLogger("starter-boost-test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock player(double balance) {
        PlayerMock mock = server.addPlayer();
        balances.put(mock.getUniqueId(), balance);
        return mock;
    }

    @Test
    void eligiblePlayerGetsMaxMultiplierOnFirstSale() {
        PlayerMock player = player(0);
        boost.onSale(player, 10.0);
        assertEquals(List.of(20.0), paid, "3x multiplier means a $10 base sale pays a $20 bonus on top");
        assertTrue(player.nextMessage().contains("$20.00"));
    }

    @Test
    void playerAtOrAboveTheThresholdGetsNoBonus() {
        PlayerMock player = player(5000);
        boost.onSale(player, 10.0);
        assertTrue(paid.isEmpty(), "peak net worth already at the threshold, never eligible");
    }

    @Test
    void bonusTapersAsLifetimeBonusApproachesTheCap() {
        PlayerMock player = player(0);
        // 480 of the 500 cap already paid: remaining share is 20/500 = 4%, so multiplier is
        // 1 + (3 - 1) * 0.04 = 1.08.
        boost.onSale(player, 240.0);
        paid.clear();
        boost.onSale(player, 10.0);
        assertEquals(List.of(0.8), paid, "(1.08 - 1) * 10 = 0.8");
    }

    @Test
    void bonusNeverExceedsTheLifetimeCapAcrossManySales() {
        PlayerMock player = player(0);
        for (int i = 0; i < 50; i++) {
            boost.onSale(player, 100.0);
        }
        double total = paid.stream().mapToDouble(Double::doubleValue).sum();
        assertTrue(total <= 500.0001, "never pays more than the $500 lifetime cap: paid " + total);
        assertTrue(total > 499.9, "the taper should still land very close to the cap: paid " + total);
    }

    @Test
    void disabledInConfigPaysNothing() {
        yaml.set("starter-boost.enabled", false);
        config = new MarketConfig(yaml);
        PlayerMock player = player(0);
        boost.onSale(player, 10.0);
        assertTrue(paid.isEmpty());
    }

    @Test
    void sellSignClickAppliesTheBonusFromTheObservedBalanceDelta() {
        PlayerMock player = player(0);
        WorldMock world = server.addSimpleWorld("world");
        var block = world.getBlockAt(5, 70, 0);
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, ItemStack.empty(), block,
                BlockFace.NORTH, EquipmentSlot.HAND);

        boost.onInteractBefore(event);
        // Essentials pays the base sale between the two priorities running.
        balances.merge(player.getUniqueId(), 10.0, Double::sum);
        boost.onInteractAfter(event);

        assertEquals(List.of(20.0), paid, "the stall paid $10, observed as a balance delta, boosted to a $20 bonus");
    }

    @Test
    void clickOutsideAnySellSlotIsIgnored() {
        PlayerMock player = player(0);
        WorldMock world = server.addSimpleWorld("world");
        var block = world.getBlockAt(999, 70, 0);
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, ItemStack.empty(), block,
                BlockFace.NORTH, EquipmentSlot.HAND);

        boost.onInteractBefore(event);
        balances.merge(player.getUniqueId(), 10.0, Double::sum);
        boost.onInteractAfter(event);

        assertTrue(paid.isEmpty());
    }

    @Test
    void aClickWithNoBalanceIncreaseIsNotTreatedAsASale() {
        PlayerMock player = player(0);
        WorldMock world = server.addSimpleWorld("world");
        var block = world.getBlockAt(5, 70, 0);
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, ItemStack.empty(), block,
                BlockFace.NORTH, EquipmentSlot.HAND);

        boost.onInteractBefore(event);
        boost.onInteractAfter(event);

        assertTrue(paid.isEmpty(), "nothing sold (Essentials refused, wrong item, etc.), so no bonus");
    }

    @Test
    void joiningWithHighBalanceLocksOutAVeteranBeforeAnySale() {
        PlayerMock player = player(9000);
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, net.kyori.adventure.text.Component.text("")));
        balances.put(player.getUniqueId(), 9000.0);
        boost.onSale(player, 10.0);
        assertTrue(paid.isEmpty(), "peak net worth observed at join already exceeds the threshold");
    }

    @Test
    void peakNetWorthAndBonusPaidSurviveAReload() {
        PlayerMock player = player(0);
        boost.onSale(player, 240.0);
        double firstBonus = paid.getFirst();
        paid.clear();

        newBoost();
        boost.onSale(player, 10.0);
        assertFalse(paid.isEmpty());
        assertEquals(0.8, paid.getFirst(), 0.001, "the loaded bonus-paid-so-far still tapers the multiplier the same way");
        assertEquals(480.0, firstBonus, 0.01);
    }
}
