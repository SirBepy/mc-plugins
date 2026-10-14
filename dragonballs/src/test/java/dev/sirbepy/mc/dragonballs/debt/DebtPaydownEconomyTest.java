package dev.sirbepy.mc.dragonballs.debt;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 100%-to-debt redirect: every deposit pays the player's debt down first, in full,
 * and only the leftover (once debt is clear) ever reaches the real economy.
 */
class DebtPaydownEconomyTest {

    private static final Logger LOG = Logger.getLogger("debt-paydown-economy-test");

    private ServerMock server;
    private PlayerMock player;

    @TempDir
    File dir;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private DebtLedger ledger() {
        return new DebtLedger(new File(dir, "debt.yml"), LOG);
    }

    @Test
    void aDepositWhileInDebtPaysDownDebtInsteadOfCreditingBalance() {
        FakeEconomy real = new FakeEconomy(0);
        DebtLedger debt = ledger();
        debt.charge(player.getUniqueId(), 25000);
        DebtPaydownEconomy economy = new DebtPaydownEconomy(real, debt);

        economy.depositPlayer(player, 10000);

        assertEquals(15000, debt.debtOf(player.getUniqueId()));
        assertEquals(0, real.getBalance(player), "the real balance never moved - it all went to debt");
    }

    @Test
    void overflowOnceDebtClearsStillCreditsTheRealBalance() {
        FakeEconomy real = new FakeEconomy(0);
        DebtLedger debt = ledger();
        debt.charge(player.getUniqueId(), 25000);
        DebtPaydownEconomy economy = new DebtPaydownEconomy(real, debt);

        economy.depositPlayer(player, 30000);

        assertEquals(0, debt.debtOf(player.getUniqueId()));
        assertEquals(5000, real.getBalance(player), "only the leftover past the debt reaches the real balance");
    }

    @Test
    void aDepositWithNoDebtCreditsTheRealBalanceInFull() {
        FakeEconomy real = new FakeEconomy(0);
        DebtLedger debt = ledger();
        DebtPaydownEconomy economy = new DebtPaydownEconomy(real, debt);

        economy.depositPlayer(player, 500);

        assertEquals(500, real.getBalance(player));
    }

    @Test
    void withdrawalsAlwaysDelegateStraightToTheRealEconomyRegardlessOfDebt() {
        FakeEconomy real = new FakeEconomy(1000);
        DebtLedger debt = ledger();
        debt.charge(player.getUniqueId(), 25000);
        DebtPaydownEconomy economy = new DebtPaydownEconomy(real, debt);

        EconomyResponse response = economy.withdrawPlayer(player, 400);

        assertTrue(response.transactionSuccess());
        assertEquals(600, real.getBalance(player));
        assertEquals(25000, debt.debtOf(player.getUniqueId()), "a withdrawal never touches debt");
    }

    /** Minimal classic-Vault Economy: real balances, everything else unused by this test (same
     * pattern RadarShopTest's own FakeEconomy uses). */
    private static final class FakeEconomy implements Economy {
        private final Map<UUID, Double> balances = new HashMap<>();
        private final double initialBalance;

        FakeEconomy(double initialBalance) {
            this.initialBalance = initialBalance;
        }

        private double balanceOf(OfflinePlayer player) {
            return balances.computeIfAbsent(player.getUniqueId(), id -> initialBalance);
        }

        @Override public boolean isEnabled() { return true; }
        @Override public String getName() { return "FakeEconomy"; }
        @Override public boolean hasBankSupport() { return false; }
        @Override public int fractionalDigits() { return 0; }
        @Override public String format(double amount) { return String.valueOf(amount); }
        @Override public String currencyNamePlural() { return "coins"; }
        @Override public String currencyNameSingular() { return "coin"; }
        @Override public boolean hasAccount(String playerName) { return true; }
        @Override public boolean hasAccount(OfflinePlayer player) { return true; }
        @Override public boolean hasAccount(String playerName, String worldName) { return true; }
        @Override public boolean hasAccount(OfflinePlayer player, String worldName) { return true; }
        @Override public double getBalance(String playerName) { return 0; }
        @Override public double getBalance(OfflinePlayer player) { return balanceOf(player); }
        @Override public double getBalance(String playerName, String world) { return 0; }
        @Override public double getBalance(OfflinePlayer player, String world) { return balanceOf(player); }
        @Override public boolean has(String playerName, double amount) { return false; }
        @Override public boolean has(OfflinePlayer player, double amount) { return balanceOf(player) >= amount; }
        @Override public boolean has(String playerName, String worldName, double amount) { return false; }
        @Override public boolean has(OfflinePlayer player, String worldName, double amount) { return has(player, amount); }

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

        @Override public EconomyResponse createBank(String name, String player) { return failure(); }
        @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return failure(); }
        @Override public EconomyResponse deleteBank(String name) { return failure(); }
        @Override public EconomyResponse bankBalance(String name) { return failure(); }
        @Override public EconomyResponse bankHas(String name, double amount) { return failure(); }
        @Override public EconomyResponse bankWithdraw(String name, double amount) { return failure(); }
        @Override public EconomyResponse bankDeposit(String name, double amount) { return failure(); }
        @Override public EconomyResponse isBankOwner(String name, String playerName) { return failure(); }
        @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return failure(); }
        @Override public EconomyResponse isBankMember(String name, String playerName) { return failure(); }
        @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return failure(); }
        @Override public java.util.List<String> getBanks() { return java.util.List.of(); }
        @Override public boolean createPlayerAccount(String playerName) { return true; }
        @Override public boolean createPlayerAccount(OfflinePlayer player) { return true; }
        @Override public boolean createPlayerAccount(String playerName, String worldName) { return true; }
        @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return true; }

        private static EconomyResponse failure() {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "unused by this test");
        }
    }
}
