package dev.sirbepy.mc.merfolk.trade;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RareLedgerTest {

    private long now = 1_000_000_000_000L;
    private final RareLedger ledger = new RareLedger(() -> now, () -> { });

    @Test
    void oneTridentPerTwelveHoursKingdomWide() {
        assertEquals(0, ledger.waitFor(Material.TRIDENT));
        ledger.record(Material.TRIDENT);
        assertEquals(Duration.ofHours(12).toMillis(), ledger.waitFor(Material.TRIDENT));

        now += Duration.ofHours(12).toMillis();
        assertEquals(0, ledger.waitFor(Material.TRIDENT));
    }

    @Test
    void threeSpongesPerTwelveHoursInARollingWindow() {
        ledger.record(Material.SPONGE);
        now += Duration.ofHours(4).toMillis();
        ledger.record(Material.SPONGE);
        assertEquals(0, ledger.waitFor(Material.SPONGE));
        ledger.record(Material.SPONGE);

        // The first sale was 4 h ago, so the window opens 8 h from now.
        assertEquals(Duration.ofHours(8).toMillis(), ledger.waitFor(Material.SPONGE));
    }

    @Test
    void oneHeartOfTheSeaPerDay() {
        ledger.record(Material.HEART_OF_THE_SEA);
        now += Duration.ofHours(23).toMillis();
        assertTrue(ledger.waitFor(Material.HEART_OF_THE_SEA) > 0);
        now += Duration.ofHours(1).toMillis();
        assertEquals(0, ledger.waitFor(Material.HEART_OF_THE_SEA));
    }

    @Test
    void aResetMakesEverythingBuyableAgain() {
        ledger.record(Material.TRIDENT);
        ledger.record(Material.HEART_OF_THE_SEA);
        ledger.clear();
        assertEquals(0, ledger.waitFor(Material.TRIDENT));
        assertEquals(0, ledger.waitFor(Material.HEART_OF_THE_SEA));
    }

    @Test
    void commonsAreNeverCapped() {
        ledger.record(Material.COD);
        assertEquals(0, ledger.waitFor(Material.COD));
    }
}
