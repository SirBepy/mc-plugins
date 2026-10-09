package dev.sirbepy.mc.core.econ;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class VaultEconomyTest {

    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void findsTheRegisteredEconomy() {
        StubEconomy economy = new StubEconomy();
        server.getServicesManager().register(Economy.class, economy, plugin, ServicePriority.Normal);

        assertSame(economy, VaultEconomy.find(server));
    }

    @Test
    void nullWhenNoneIsRegistered() {
        assertNull(VaultEconomy.find(server));
    }

    /** Nothing here is exercised: find() only needs a registration to resolve, never a real provider. */
    private static final class StubEconomy implements Economy {

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getName() {
            return "StubEconomy";
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
            return 0;
        }

        @Override
        public double getBalance(String playerName, String world) {
            return 0;
        }

        @Override
        public double getBalance(OfflinePlayer player, String world) {
            return 0;
        }

        @Override
        public boolean has(String playerName, double amount) {
            return false;
        }

        @Override
        public boolean has(OfflinePlayer player, double amount) {
            return false;
        }

        @Override
        public boolean has(String playerName, String worldName, double amount) {
            return false;
        }

        @Override
        public boolean has(OfflinePlayer player, String worldName, double amount) {
            return false;
        }

        @Override
        public EconomyResponse withdrawPlayer(String playerName, double amount) {
            return failure();
        }

        @Override
        public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
            return failure();
        }

        @Override
        public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
            return failure();
        }

        @Override
        public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
            return failure();
        }

        @Override
        public EconomyResponse depositPlayer(String playerName, double amount) {
            return failure();
        }

        @Override
        public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
            return failure();
        }

        @Override
        public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
            return failure();
        }

        @Override
        public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
            return failure();
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
