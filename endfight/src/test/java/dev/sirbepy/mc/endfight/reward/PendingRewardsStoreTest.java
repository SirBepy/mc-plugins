package dev.sirbepy.mc.endfight.reward;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code pending-rewards.yml}: survives being read back by a brand-new store instance pointed at
 * the same file (what a plugin restart looks like), and hands each entry out exactly once. */
class PendingRewardsStoreTest {

    @Test
    void anAddedEntrySurvivesAFreshStoreOverTheSameFile(@TempDir File dir) {
        File file = new File(dir, "pending-rewards.yml");
        UUID id = UUID.randomUUID();

        PendingRewardsStore first = new PendingRewardsStore(file, Logger.getLogger("test"));
        first.add(id, new PendingReward(12000, 1));

        // A fresh instance over the same file stands in for "the plugin restarted".
        PendingRewardsStore reloaded = new PendingRewardsStore(file, Logger.getLogger("test"));
        List<PendingReward> owed = reloaded.take(id);

        assertEquals(List.of(new PendingReward(12000, 1)), owed);
    }

    @Test
    void takingAnEntryRemovesItSoItIsNeverDeliveredTwice() {
        File file = new File(System.getProperty("java.io.tmpdir"), "endfight-pending-" + UUID.randomUUID() + ".yml");
        file.deleteOnExit();
        UUID id = UUID.randomUUID();
        PendingRewardsStore store = new PendingRewardsStore(file, Logger.getLogger("test"));
        store.add(id, new PendingReward(500, 1));

        assertEquals(1, store.take(id).size());
        assertTrue(store.take(id).isEmpty(), "a second take must find nothing left to deliver");
    }

    @Test
    void multipleMissedFightsQueueAsSeparateEntries(@TempDir File dir) {
        File file = new File(dir, "pending-rewards.yml");
        UUID id = UUID.randomUUID();
        PendingRewardsStore store = new PendingRewardsStore(file, Logger.getLogger("test"));

        store.add(id, new PendingReward(12000, 1));
        store.add(id, new PendingReward(500, 1));

        assertEquals(2, store.totalEntries());
        List<PendingReward> owed = store.take(id);
        assertEquals(2, owed.size());
        assertEquals(0, store.totalEntries());
    }
}
