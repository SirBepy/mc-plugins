package dev.sirbepy.mc.dragonballs.debt;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;

/**
 * Decorates whatever real classic-Vault {@code Economy} is registered (VaultUnlocked on the live
 * server), redirecting every deposit through {@link DebtLedger#payDown} first: while a player owes
 * anything, 100% of an incoming deposit (a market sale, a bounty, any payout) pays the debt down,
 * with only the overflow - once debt hits 0 - actually crediting their real balance (no
 * partial cut, the whole amount every time until clear). Every other {@code Economy} method,
 * withdrawals included, delegates straight to the real economy unchanged: a purchase flow blocks on
 * {@link DebtService#isInDebt} before ever reaching a withdrawal, so withdrawals themselves never
 * need to know about debt.
 *
 * <p>{@code DragonBallsPlugin} registers this at {@link org.bukkit.plugin.ServicePriority#Highest},
 * one tick after enable (once every plugin has had its own {@code onEnable} run), so every plugin's
 * existing {@code VaultEconomy.find(server)} / {@code getServicesManager().getRegistration
 * (Economy.class)} lookup (market, guild, the radar shop) returns this instead of the real
 * provider, with zero changes to any of those call sites: Bukkit's {@code ServicesManager} always
 * hands back the highest-priority registration, and real Vault-compatible economy plugins register
 * at {@code ServicePriority.Normal} (confirmed against this project's own {@code RadarShopTest},
 * which registers its fake economy at {@code Normal} to mirror live VaultUnlocked), so
 * {@code Highest} wins regardless of enable order.
 */
public final class DebtPaydownEconomy implements Economy {

    private final Economy real;
    private final DebtLedger debt;

    public DebtPaydownEconomy(Economy real, DebtLedger debt) {
        this.real = real;
        this.debt = debt;
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return depositWithPaydown(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositWithPaydown(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositWithPaydown(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositWithPaydown(Bukkit.getOfflinePlayer(playerName), amount);
    }

    private EconomyResponse depositWithPaydown(OfflinePlayer player, double amount) {
        double overflow = debt.payDown(player.getUniqueId(), amount);
        if (overflow <= 0) {
            // Fully absorbed by debt paydown: the real balance never moved, so report that
            // (unchanged) balance, not the deposited amount, in case a caller reads it.
            return new EconomyResponse(amount, real.getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
        }
        return real.depositPlayer(player, overflow);
    }

    // ---- everything else: straight passthrough to the real economy ----

    @Override
    public boolean isEnabled() {
        return real.isEnabled();
    }

    @Override
    public String getName() {
        return real.getName();
    }

    @Override
    public boolean hasBankSupport() {
        return real.hasBankSupport();
    }

    @Override
    public int fractionalDigits() {
        return real.fractionalDigits();
    }

    @Override
    public String format(double amount) {
        return real.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return real.currencyNamePlural();
    }

    @Override
    public String currencyNameSingular() {
        return real.currencyNameSingular();
    }

    @Override
    public boolean hasAccount(String playerName) {
        return real.hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return real.hasAccount(player);
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return real.hasAccount(playerName, worldName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return real.hasAccount(player, worldName);
    }

    @Override
    public double getBalance(String playerName) {
        return real.getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return real.getBalance(player);
    }

    @Override
    public double getBalance(String playerName, String world) {
        return real.getBalance(playerName, world);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return real.getBalance(player, world);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return real.has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return real.has(player, amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return real.has(playerName, worldName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return real.has(player, worldName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return real.withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return real.withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return real.withdrawPlayer(playerName, worldName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return real.withdrawPlayer(player, worldName, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return real.createBank(name, player);
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return real.createBank(name, player);
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return real.deleteBank(name);
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return real.bankBalance(name);
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return real.bankHas(name, amount);
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return real.bankWithdraw(name, amount);
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return real.bankDeposit(name, amount);
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return real.isBankOwner(name, playerName);
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return real.isBankOwner(name, player);
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return real.isBankMember(name, playerName);
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return real.isBankMember(name, player);
    }

    @Override
    public List<String> getBanks() {
        return real.getBanks();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return real.createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return real.createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return real.createPlayerAccount(playerName, worldName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return real.createPlayerAccount(player, worldName);
    }
}
