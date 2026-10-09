package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.endfight.tier.Tier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code pending-rewards.yml}: survives being read back by a brand-new store instance pointed at
 * the same file (what a plugin restart looks like), and hands each entry out exactly once. */
class PendingRewardsStoreTest {

    @Test
    void anXpOnlyEntrySurvivesAFreshStoreOverTheSameFile(@TempDir File dir) {
        File file = new File(dir, "pending-rewards.yml");
        UUID id = UUID.randomUUID();

        PendingRewardsStore first = new PendingRewardsStore(file, Logger.getLogger("test"));
        first.add(id, new PendingReward(12000, null));

        // A fresh instance over the same file stands in for "the plugin restarted".
        PendingRewardsStore reloaded = new PendingRewardsStore(file, Logger.getLogger("test"));
        List<PendingReward> owed = reloaded.take(id);

        assertEquals(1, owed.size());
        assertEquals(12000, owed.get(0).xp());
        assertNull(owed.get(0).hoard());
    }

    @Test
    void aHoardEntryRoundTripsEveryField(@TempDir File dir) {
        File file = new File(dir, "pending-rewards.yml");
        UUID id = UUID.randomUUID();
        PendingRewardsStore store = new PendingRewardsStore(file, Logger.getLogger("test"));

        PendingReward.HoardSpec hoard = new PendingReward.HoardSpec(Tier.FEROCIOUS, "2026-10-10", 8, 6, 2, 0,
                List.of(new PendingReward.RollGrant(RollEngine.Kind.ARMOR, "HELMET")));
        store.add(id, new PendingReward(2000, hoard));

        PendingRewardsStore reloaded = new PendingRewardsStore(file, Logger.getLogger("test"));
        PendingReward owed = reloaded.take(id).get(0);

        assertEquals(2000, owed.xp());
        assertEquals(hoard, owed.hoard());
    }

    @Test
    void takingAnEntryRemovesItSoItIsNeverDeliveredTwice() {
        File file = new File(System.getProperty("java.io.tmpdir"), "endfight-pending-" + UUID.randomUUID() + ".yml");
        file.deleteOnExit();
        UUID id = UUID.randomUUID();
        PendingRewardsStore store = new PendingRewardsStore(file, Logger.getLogger("test"));
        store.add(id, new PendingReward(500, null));

        assertEquals(1, store.take(id).size());
        assertTrue(store.take(id).isEmpty(), "a second take must find nothing left to deliver");
    }

    @Test
    void multipleMissedFightsQueueAsSeparateEntries(@TempDir File dir) {
        File file = new File(dir, "pending-rewards.yml");
        UUID id = UUID.randomUUID();
        PendingRewardsStore store = new PendingRewardsStore(file, Logger.getLogger("test"));

        store.add(id, new PendingReward(12000, null));
        store.add(id, new PendingReward(500, null));

        assertEquals(2, store.totalEntries());
        List<PendingReward> owed = store.take(id);
        assertEquals(2, owed.size());
        assertEquals(0, store.totalEntries());
    }
}
