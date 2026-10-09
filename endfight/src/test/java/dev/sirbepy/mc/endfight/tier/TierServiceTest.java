package dev.sirbepy.mc.endfight.tier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierServiceTest {

    private static final Logger LOGGER = Logger.getLogger("tier-test");

    /** Never says a kill happened, unless flipped - lets a test drive {@code firstKillEver()}
     * without a real {@code World}. */
    private static final class FakeBattleHistory implements EndDragonBattleHistory {
        private boolean previouslyKilled;

        @Override
        public boolean hasBeenPreviouslyKilled() {
            return previouslyKilled;
        }
    }

    private TierService service(File dir) {
        return new TierService(new File(dir, "tier.yml"), Tier.FLEDGELING, new FakeBattleHistory(), LOGGER);
    }

    @Test
    void firstKillForcesAscendedRegardlessOfTheAltarsChoice(@TempDir File dir) {
        TierService service = service(dir);
        service.setNext(Tier.FLEDGELING, null);

        assertTrue(service.firstKillEver());
        assertEquals(Tier.ASCENDED, service.current());

        Tier locked = service.lockForSpawn();

        assertEquals(Tier.ASCENDED, locked);
        assertEquals(Tier.ASCENDED, service.current());
    }

    @Test
    void aChoiceMadeDuringAFightOnlyAppliesToTheNextOne(@TempDir File dir) {
        TierService service = service(dir);
        service.recordKill(); // not the server's first fight any more
        service.setNext(Tier.CRUEL, null);
        service.lockForSpawn(); // fight #1 locks in Cruel

        assertEquals(Tier.CRUEL, service.current());

        service.setNext(Tier.PRIMORDIAL, null); // picked mid-fight, for next time
        assertEquals(Tier.CRUEL, service.current(), "the fight in progress must not change");
        assertEquals(Tier.PRIMORDIAL, service.next());

        Tier lockedForFightTwo = service.lockForSpawn();

        assertEquals(Tier.PRIMORDIAL, lockedForFightTwo);
        assertEquals(Tier.PRIMORDIAL, service.current());
    }

    @Test
    void persistenceRoundTrip(@TempDir File dir) {
        File file = new File(dir, "tier.yml");
        FakeBattleHistory history = new FakeBattleHistory();
        TierService first = new TierService(file, Tier.FLEDGELING, history, LOGGER);
        first.recordKill();
        first.setNext(Tier.IMMORTAL, null);
        first.lockForSpawn();

        TierService reloaded = new TierService(file, Tier.FLEDGELING, history, LOGGER);

        assertEquals(Tier.IMMORTAL, reloaded.next());
        assertEquals(Tier.IMMORTAL, reloaded.current());
        assertFalse(reloaded.firstKillEver(), "the recorded kill must survive a restart");
    }

    @Test
    void theBattleHistoryAloneAlsoClearsFirstKillEver(@TempDir File dir) {
        FakeBattleHistory history = new FakeBattleHistory();
        TierService service = new TierService(new File(dir, "tier.yml"), Tier.FLEDGELING, history, LOGGER);
        assertTrue(service.firstKillEver());

        history.previouslyKilled = true;

        assertFalse(service.firstKillEver(), "the world's own battle record is enough on its own");
    }

    @Test
    void defaultNextChoiceComesFromConfig(@TempDir File dir) {
        TierService service = new TierService(new File(dir, "tier.yml"), Tier.PRIMORDIAL, new FakeBattleHistory(), LOGGER);
        assertEquals(Tier.PRIMORDIAL, service.next());
    }
}
