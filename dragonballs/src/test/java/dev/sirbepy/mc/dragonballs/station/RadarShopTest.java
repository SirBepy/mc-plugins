package dev.sirbepy.mc.dragonballs.station;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.radar.RadarItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers can't-afford / buys / inventory-full / not-the-sign / no-economy, for each of the 3 signs;
 * economy itself is a tiny fake registered through MockBukkit's real {@code ServicesManager}, same
 * as a live Vault provider.
 */
class RadarShopTest {

    private ServerMock server;
    private WorldMock world;
    private PluginMock plugin;
    private RadarItems radarItems;
    private BallSets sets;
    private PlayerMock player;
    private Block signBlock;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        radarItems = new RadarItems(plugin);
        BallItems items = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        BallRegistry overworld = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items, BallSet.OVERWORLD);
        BallRegistry nether = new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), items, BallSet.NETHER);
        BallRegistry end = new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), items, BallSet.END);
        sets = new BallSets(overworld, nether, end, items, () -> new DragonBallsConfig(new YamlConfiguration()));

        player = server.addPlayer();
        player.teleport(new Location(world, 422, 116, -841));

        // Defaults (no station.radar-signs.* set) already match the overworld sign used by most tests.
        signBlock = world.getBlockAt(422, 116, -841);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private RadarShop shop() {
        return new RadarShop(plugin, () -> StationConfig.from(plugin.getConfig()),
                () -> new DragonBallsConfig(plugin.getConfig()), radarItems, sets);
    }

    private void registerEconomy(FakeEconomy economy) {
        server.getServicesManager().register(Economy.class, economy, plugin, ServicePriority.Normal);
    }

    private org.bukkit.event.player.PlayerInteractEvent rightClickSign() {
        return new org.bukkit.event.player.PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, signBlock,
                org.bukkit.block.BlockFace.NORTH, EquipmentSlot.HAND);
    }

    @Test
    void richEnoughPlayerBuysARadarAndIsCharged() {
        FakeEconomy economy = new FakeEconomy(5000);
        registerEconomy(economy);

        shop().onInteract(rightClickSign());

        assertEquals(4000, economy.getBalance(player));
        assertEquals(1, countRadars(player));
    }

    @Test
    void tooPoorPlayerGetsTheCantAffordMessageAndKeepsTheirMoney() {
        FakeEconomy economy = new FakeEconomy(500);
        registerEconomy(economy);

        shop().onInteract(rightClickSign());

        assertEquals(500, economy.getBalance(player));
        assertEquals(0, countRadars(player));
    }

    @Test
    void inventoryFullDropsTheRadarAtTheirFeetInstead() {
        FakeEconomy economy = new FakeEconomy(5000);
        registerEconomy(economy);
        fillInventory(player);

        shop().onInteract(rightClickSign());

        assertEquals(4000, economy.getBalance(player));
        var dropped = world.getEntitiesByClass(org.bukkit.entity.Item.class).stream()
                .filter(item -> radarItems.isRadar(item.getItemStack()))
                .toList();
        assertEquals(1, dropped.size());
    }

    @Test
    void clickingSomeOtherBlockDoesNothing() {
        FakeEconomy economy = new FakeEconomy(5000);
        registerEconomy(economy);
        Block other = world.getBlockAt(0, 64, 0);

        shop().onInteract(new org.bukkit.event.player.PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null,
                other, org.bukkit.block.BlockFace.NORTH, EquipmentSlot.HAND));

        assertEquals(5000, economy.getBalance(player));
        assertEquals(0, countRadars(player));
    }

    @Test
    void noRegisteredEconomyMeansTheShopIsClosed() {
        shop().onInteract(rightClickSign());

        assertEquals(0, countRadars(player));
    }

    @Test
    void theNetherSignSellsANetherRadarAtItsOwnPrice() {
        FakeEconomy economy = new FakeEconomy(10000);
        registerEconomy(economy);
        Block netherSign = world.getBlockAt(422, 116, -839);
        player.teleport(new Location(world, 422, 116, -839));

        shop().onInteract(new org.bukkit.event.player.PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null,
                netherSign, org.bukkit.block.BlockFace.NORTH, EquipmentSlot.HAND));

        assertEquals(5000, economy.getBalance(player), "the Nether sign's default price is 5000");
        var netherRadar = player.getInventory().getContents();
        assertTrue(java.util.Arrays.stream(netherRadar).anyMatch(item -> item != null
                && radarItems.isRadar(item) && radarItems.setOf(item) == BallSet.NETHER));
    }

    @Test
    void theEndSignRefusesWhileTheEndIsDisabledAndTakesNoMoney() {
        FakeEconomy economy = new FakeEconomy(50000);
        registerEconomy(economy);
        Block endSign = world.getBlockAt(422, 117, -840);
        player.teleport(new Location(world, 422, 117, -840));

        shop().onInteract(new org.bukkit.event.player.PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null,
                endSign, org.bukkit.block.BlockFace.NORTH, EquipmentSlot.HAND));

        assertEquals(50000, economy.getBalance(player), "a disabled End must never charge for its radar");
        assertEquals(0, countRadars(player));
    }

    private static int countRadars(PlayerMock player) {
        long count = 0;
        for (var item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COMPASS) {
                count++;
            }
        }
        return (int) count;
    }

    // MockBukkit's PlayerInventoryMock.addItem() (inherited from InventoryMock) looks for an empty
    // slot across the whole backing array, including the armor/offhand slots past index 35 - so
    // "full" here means every slot getSize() reports, not just getStorageContents().
    private static void fillInventory(PlayerMock player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItem(slot, new org.bukkit.inventory.ItemStack(Material.DIRT, 64));
        }
    }

    /** Minimal classic-Vault Economy: real balances, everything else unused by RadarShop. */
    private static final class FakeEconomy implements Economy {
        private final Map<UUID, Double> balances = new HashMap<>();
        private final double initialBalance;

        FakeEconomy(double initialBalance) {
            this.initialBalance = initialBalance;
        }

        private double balanceOf(OfflinePlayer player) {
            return balances.computeIfAbsent(player.getUniqueId(), id -> initialBalance);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getName() {
            return "FakeEconomy";
        }

        @Override
        public boolean hasBankSupport() {
            return false;
        }

        @Override
        public int fractionalDigits() {
            return 0;
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }

        @Override
        public String currencyNamePlural() {
            return "coins";
        }

        @Override
        public String currencyNameSingular() {
            return "coin";
        }

        @Override
        public boolean hasAccount(String playerName) {
            return true;
        }

        @Override
        public boolean hasAccount(OfflinePlayer player) {
            return true;
        }

        @Override
        public boolean hasAccount(String playerName, String worldName) {
            return true;
        }

        @Override
        public boolean hasAccount(OfflinePlayer player, String worldName) {
            return true;
        }

        @Override
        public double getBalance(String playerName) {
            return 0;
        }

        @Override
        public double getBalance(OfflinePlayer player) {
            return balanceOf(player);
        }

        @Override
        public double getBalance(String playerName, String world) {
            return 0;
        }

        @Override
        public double getBalance(OfflinePlayer player, String world) {
            return balanceOf(player);
        }

        @Override
        public boolean has(String playerName, double amount) {
            return false;
        }

        @Override
        public boolean has(OfflinePlayer player, double amount) {
            return balanceOf(player) >= amount;
        }

        @Override
        public boolean has(String playerName, String worldName, double amount) {
            return false;
        }

        @Override
        public boolean has(OfflinePlayer player, String worldName, double amount) {
            return has(player, amount);
        }

        @Override
        public EconomyResponse withdrawPlayer(String playerName, double amount) {
            return failure();
        }

        @Override
        public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
            double balance = balanceOf(player) - amount;
            balances.put(player.getUniqueId(), balance);
            return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
        }

        @Override
        public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
            return failure();
        }

        @Override
        public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
            return withdrawPlayer(player, amount);
        }

        @Override
        public EconomyResponse depositPlayer(String playerName, double amount) {
            return failure();
        }

        @Override
        public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
            double balance = balanceOf(player) + amount;
            balances.put(player.getUniqueId(), balance);
            return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
        }

        @Override
        public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
            return failure();
        }

        @Override
        public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
            return depositPlayer(player, amount);
        }

        @Override
        public EconomyResponse createBank(String name, String player) {
            return failure();
        }

        @Override
        public EconomyResponse createBank(String name, OfflinePlayer player) {
            return failure();
        }

        @Override
        public EconomyResponse deleteBank(String name) {
            return failure();
        }

        @Override
        public EconomyResponse bankBalance(String name) {
            return failure();
        }

        @Override
        public EconomyResponse bankHas(String name, double amount) {
            return failure();
        }

        @Override
        public EconomyResponse bankWithdraw(String name, double amount) {
            return failure();
        }

        @Override
        public EconomyResponse bankDeposit(String name, double amount) {
            return failure();
        }

        @Override
        public EconomyResponse isBankOwner(String name, String playerName) {
            return failure();
        }

        @Override
        public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
            return failure();
        }

        @Override
        public EconomyResponse isBankMember(String name, String playerName) {
            return failure();
        }

        @Override
        public EconomyResponse isBankMember(String name, OfflinePlayer player) {
            return failure();
        }

        @Override
        public List<String> getBanks() {
            return List.of();
        }

        @Override
        public boolean createPlayerAccount(String playerName) {
            return true;
        }

        @Override
        public boolean createPlayerAccount(OfflinePlayer player) {
            return true;
        }

        @Override
        public boolean createPlayerAccount(String playerName, String worldName) {
            return true;
        }

        @Override
        public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
            return true;
        }

        private EconomyResponse failure() {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "not implemented");
        }
    }
}
