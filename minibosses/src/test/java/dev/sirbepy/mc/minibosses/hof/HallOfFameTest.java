package dev.sirbepy.mc.minibosses.hof;

import dev.sirbepy.mc.minibosses.registry.Tier;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MockBukkit (unlike {@code GuardianRegistryTest}'s plain-JUnit parsing test) because this class
 * checks online/offline state via {@code Bukkit.getPlayer}, pays through a registered Vault
 * {@code Economy}, and sends messages on join - all of which need a live server/player double.
 */
class HallOfFameTest {

    private ServerMock server;
    private PluginMock plugin;
    private HallOfFame hallOfFame;
    private FakeEconomy economy;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        economy = new FakeEconomy();
        server.getServicesManager().register(Economy.class, economy, plugin, ServicePriority.Normal);
        hallOfFame = new HallOfFame(tempDir, Logger.getLogger("minibosses-test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aFirstKillRecordsOncePaysEveryFighterAndIsNeverOverwritten() {
        PlayerMock alice = server.addPlayer("Alice");
        UUID aliceId = alice.getUniqueId();

        List<HallOfFame.Slot> recorded = hallOfFame.guardianKilled("test-guardian", Tier.NORMAL,
                List.of(aliceId), "world");

        assertEquals(List.of(HallOfFame.Slot.FIRST_KILL), recorded);
        assertTrue(hallOfFame.done("test-guardian", HallOfFame.Slot.FIRST_KILL));
        assertEquals(HallOfFame.COINS, economy.balance(aliceId));
        assertEquals(Map.of(aliceId, "Alice"), hallOfFame.party("test-guardian", HallOfFame.Slot.FIRST_KILL));
    }

    @Test
    void aSecondKillDoesNotOverwriteTheFirstKillRecordOrPayAgain() {
        PlayerMock alice = server.addPlayer("Alice");
        UUID aliceId = alice.getUniqueId();
        PlayerMock bob = server.addPlayer("Bob");
        UUID bobId = bob.getUniqueId();
        hallOfFame.guardianKilled("test-guardian", Tier.NORMAL, List.of(aliceId), "world");

        List<HallOfFame.Slot> recorded = hallOfFame.guardianKilled("test-guardian", Tier.NORMAL, List.of(bobId), "world");

        assertEquals(List.of(), recorded, "the slot is already spent");
        assertEquals(Map.of(aliceId, "Alice"), hallOfFame.party("test-guardian", HallOfFame.Slot.FIRST_KILL));
        assertEquals(0, economy.balance(bobId));
    }

    @Test
    void firstMythicClearIsTrackedIndependentlyOfFirstKillAnyTier() {
        PlayerMock alice = server.addPlayer("Alice");
        UUID aliceId = alice.getUniqueId();
        PlayerMock bob = server.addPlayer("Bob");
        UUID bobId = bob.getUniqueId();
        // Alice's Normal kill spends FIRST_KILL but must not touch FIRST_MYTHIC.
        hallOfFame.guardianKilled("test-guardian", Tier.NORMAL, List.of(aliceId), "world");
        assertFalse(hallOfFame.done("test-guardian", HallOfFame.Slot.FIRST_MYTHIC));

        List<HallOfFame.Slot> recorded = hallOfFame.guardianKilled("test-guardian", Tier.MYTHIC, List.of(bobId), "world");

        assertEquals(List.of(HallOfFame.Slot.FIRST_MYTHIC), recorded, "FIRST_KILL was already spent by Alice");
        assertTrue(hallOfFame.done("test-guardian", HallOfFame.Slot.FIRST_MYTHIC));
        assertEquals(Map.of(aliceId, "Alice"), hallOfFame.party("test-guardian", HallOfFame.Slot.FIRST_KILL));
        assertEquals(Map.of(bobId, "Bob"), hallOfFame.party("test-guardian", HallOfFame.Slot.FIRST_MYTHIC));
    }

    @Test
    void anOfflineFighterGetsQueuedAndIsPaidAndToldOnNextJoin() {
        PlayerMock alice = server.addPlayer("Alice");
        UUID aliceId = alice.getUniqueId();
        alice.disconnect();

        hallOfFame.guardianKilled("test-guardian", Tier.NORMAL, List.of(aliceId), "world");

        // Paid at once (offline deposit still works), but flagged to tell them on their next join.
        assertEquals(HallOfFame.COINS, economy.balance(aliceId));
        alice.reconnect();
        while (alice.nextComponentMessage() != null) {
            // drop anything said before the join
        }

        hallOfFame.joined(alice);

        assertTrue(PlainTextComponentSerializer.plainText().serialize(alice.nextComponentMessage())
                .contains("While you were away"));
        hallOfFame.joined(alice);
        assertEquals(null, alice.nextComponentMessage(), "told once");
    }

    @Test
    void coinsAnUnavailableEconomyRefusedArePaidOnTheNextJoin() {
        PlayerMock alice = server.addPlayer("Alice");
        UUID aliceId = alice.getUniqueId();
        economy.up = false;

        hallOfFame.guardianKilled("test-guardian", Tier.NORMAL, List.of(aliceId), "world");
        assertTrue(hallOfFame.unpaid("test-guardian", HallOfFame.Slot.FIRST_KILL, aliceId));
        assertEquals(0, economy.balance(aliceId));

        economy.up = true;
        hallOfFame.joined(alice);

        assertEquals(HallOfFame.COINS, economy.balance(aliceId));
        assertFalse(hallOfFame.unpaid("test-guardian", HallOfFame.Slot.FIRST_KILL, aliceId));
    }

    @Test
    void adminRepairAddsAFighterToAnAlreadyRecordedPartyWithNoCoins() {
        PlayerMock alice = server.addPlayer("Alice");
        UUID aliceId = alice.getUniqueId();
        PlayerMock bob = server.addPlayer("Bob");
        UUID bobId = bob.getUniqueId();
        hallOfFame.guardianKilled("test-guardian", Tier.NORMAL, List.of(aliceId), "world");

        HallOfFame.AddResult result = hallOfFame.addToParty("test-guardian", HallOfFame.Slot.FIRST_KILL, bobId, "Bob");

        assertEquals(HallOfFame.AddResult.ADDED, result);
        assertEquals(Map.of(aliceId, "Alice", bobId, "Bob"), hallOfFame.party("test-guardian", HallOfFame.Slot.FIRST_KILL));
        assertEquals(0, economy.balance(bobId), "repair pays no coins");
        assertEquals(HallOfFame.AddResult.ALREADY_IN_PARTY,
                hallOfFame.addToParty("test-guardian", HallOfFame.Slot.FIRST_KILL, bobId, "Bob"));
    }

    @Test
    void adminRepairRefusesWithNoRecordYet() {
        UUID aliceId = UUID.randomUUID();

        HallOfFame.AddResult result = hallOfFame.addToParty("test-guardian", HallOfFame.Slot.FIRST_KILL, aliceId, "Alice");

        assertEquals(HallOfFame.AddResult.NO_RECORD, result);
    }

    /** Minimal classic-Vault Economy: real balances, everything else unused by HallOfFame. */
    private static final class FakeEconomy implements Economy {
        private final Map<UUID, Double> balances = new HashMap<>();
        boolean up = true;

        double balance(UUID id) {
            return balances.getOrDefault(id, 0.0);
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
        @Override public double getBalance(OfflinePlayer player) { return balance(player.getUniqueId()); }
        @Override public double getBalance(String playerName, String world) { return 0; }
        @Override public double getBalance(OfflinePlayer player, String world) { return balance(player.getUniqueId()); }
        @Override public boolean has(String playerName, double amount) { return false; }
        @Override public boolean has(OfflinePlayer player, double amount) { return balance(player.getUniqueId()) >= amount; }
        @Override public boolean has(String playerName, String worldName, double amount) { return false; }
        @Override public boolean has(OfflinePlayer player, String worldName, double amount) { return has(player, amount); }
        @Override public EconomyResponse withdrawPlayer(String playerName, double amount) { return failure(); }
        @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
            double balance = balance(player.getUniqueId()) - amount;
            balances.put(player.getUniqueId(), balance);
            return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
        }
        @Override public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) { return failure(); }
        @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
            return withdrawPlayer(player, amount);
        }
        @Override public EconomyResponse depositPlayer(String playerName, double amount) { return failure(); }
        @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
            if (!up) {
                return failure();
            }
            double balance = balance(player.getUniqueId()) + amount;
            balances.put(player.getUniqueId(), balance);
            return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
        }
        @Override public EconomyResponse depositPlayer(String playerName, String worldName, double amount) { return failure(); }
        @Override public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
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
        @Override public List<String> getBanks() { return List.of(); }
        @Override public boolean createPlayerAccount(String playerName) { return true; }
        @Override public boolean createPlayerAccount(OfflinePlayer player) { return true; }
        @Override public boolean createPlayerAccount(String playerName, String worldName) { return true; }
        @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return true; }

        private EconomyResponse failure() {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "not implemented");
        }
    }
}
