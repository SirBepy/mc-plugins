package dev.sirbepy.mc.dragonballs.debt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code debt.yml}: a flat, indefinite per-player balance - charges only ever add, {@link
 * DebtLedger#payDown} is the only thing that ever reduces it. */
class DebtLedgerTest {

    private static final Logger LOG = Logger.getLogger("debt-ledger-test");
    private static final UUID PLAYER = UUID.randomUUID();

    @TempDir
    File dir;

    private DebtLedger ledger(File file) {
        return new DebtLedger(file, LOG);
    }

    @Test
    void aFreshPlayerOwesNothing() {
        DebtLedger ledger = ledger(new File(dir, "debt.yml"));
        assertEquals(0, ledger.debtOf(PLAYER));
        assertFalse(ledger.isInDebt(PLAYER));
    }

    @Test
    void chargingAddsToTheBalanceAcrossMultipleCharges() {
        DebtLedger ledger = ledger(new File(dir, "debt.yml"));
        ledger.charge(PLAYER, 25000);
        ledger.charge(PLAYER, 5000);
        assertEquals(30000, ledger.debtOf(PLAYER));
        assertTrue(ledger.isInDebt(PLAYER));
    }

    @Test
    void chargingANonPositiveAmountIsANoOp() {
        DebtLedger ledger = ledger(new File(dir, "debt.yml"));
        ledger.charge(PLAYER, 0);
        ledger.charge(PLAYER, -100);
        assertEquals(0, ledger.debtOf(PLAYER));
    }

    @Test
    void payDownReducesDebtAndReturnsNoOverflowWhenNotFullyPaid() {
        DebtLedger ledger = ledger(new File(dir, "debt.yml"));
        ledger.charge(PLAYER, 25000);
        double overflow = ledger.payDown(PLAYER, 10000);
        assertEquals(0, overflow);
        assertEquals(15000, ledger.debtOf(PLAYER));
        assertTrue(ledger.isInDebt(PLAYER));
    }

    @Test
    void payDownClearsDebtAndReturnsTheOverflowOnceItsBiggerThanWhatsOwed() {
        DebtLedger ledger = ledger(new File(dir, "debt.yml"));
        ledger.charge(PLAYER, 25000);
        double overflow = ledger.payDown(PLAYER, 30000);
        assertEquals(5000, overflow);
        assertEquals(0, ledger.debtOf(PLAYER));
        assertFalse(ledger.isInDebt(PLAYER));
    }

    @Test
    void payDownOnAClearPlayerIsANoOpThatReturnsTheWholeAmount() {
        DebtLedger ledger = ledger(new File(dir, "debt.yml"));
        double overflow = ledger.payDown(PLAYER, 5000);
        assertEquals(5000, overflow);
    }

    @Test
    void debtSurvivesAReload() {
        File file = new File(dir, "debt.yml");
        ledger(file).charge(PLAYER, 42000);

        DebtLedger reloaded = ledger(file);
        assertEquals(42000, reloaded.debtOf(PLAYER));
    }
}
