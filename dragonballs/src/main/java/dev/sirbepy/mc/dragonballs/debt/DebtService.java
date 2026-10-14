package dev.sirbepy.mc.dragonballs.debt;

import java.util.UUID;

/**
 * Per-player debt ledger: how much a player owes Shenron (a wish's money cost they couldn't pay
 * upfront, per docs/dragonballs.md "Nether and End dragons" - flat amount, no interest, no
 * deadline, indefinite until paid). Registered with Bukkit's {@code ServicesManager} by
 * {@link DebtLedger} (dragonballs owns the ledger), the same cross-plugin lookup pattern guild
 * already uses for Names' {@code NamesApi} ({@code getServicesManager().load(NamesApi.class)} in
 * {@code NamesTitleSink}): a consumer like market needs only a {@code compileOnly} dependency on
 * this interface (not implementation), a {@code softdepend} entry, and
 * {@code getServicesManager().load(DebtService.class)}, no hard runtime coupling to dragonballs.
 */
public interface DebtService {

    /** 0 when the player owes nothing. */
    double debtOf(UUID playerId);

    default boolean isInDebt(UUID playerId) {
        return debtOf(playerId) > 0;
    }

    /** Adds {@code amount} to the player's debt (e.g. the part of a wish's money cost they
     * couldn't pay on the spot); a no-op for a non-positive amount. */
    void charge(UUID playerId, double amount);
}
