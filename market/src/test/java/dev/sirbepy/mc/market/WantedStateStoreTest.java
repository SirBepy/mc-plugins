package dev.sirbepy.mc.market;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code wanted.yml} persistence (split out of WantedBoardTest). */
class WantedStateStoreTest {

    private static final Logger LOG = Logger.getLogger("wanted-state-store-test");

    @TempDir
    File dir;

    // Moved from WantedBoardTest.aStateFileFromTheFixedPriceBoardLoadsWithFactorOne.
    @Test
    void anOldFormatFileLoadsWithFactorOneAndItsResaveRoundTrips() throws Exception {
        File stateFile = new File(dir, "wanted.yml");
        UUID targetId = UUID.randomUUID();
        Files.writeString(stateFile.toPath(), """
                period: day-1
                cooldown: false
                target-id: %s
                target-name: Target
                price: '87'
                posted-at: '2026-10-09T00:00:00Z'
                """.formatted(targetId));
        WantedStateStore store = new WantedStateStore(stateFile, LOG);
        WantedState loaded = store.load();
        assertEquals(1.0, loaded.factor(), 1e-9);
        assertTrue(loaded.played(), "the old board never tracked it, so it doesn't cost the target a reward");
        assertFalse(loaded.died());

        // An existing wanted.yml loaded and re-saved must diff clean, old-format fixtures included:
        // nothing invented or dropped on the way back out.
        store.save(loaded);
        assertEquals(loaded, store.load(), "re-saving and reloading the same state changes nothing");
    }
}
