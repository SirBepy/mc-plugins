package dev.sirbepy.mc.tag.state;

import dev.sirbepy.mc.tag.logic.PendingAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateRepositoryTest {

    @Test
    void roundTripsEveryField(@TempDir Path tempDir) throws IOException {
        File file = tempDir.resolve("state.yml").toFile();

        UUID it = UUID.randomUUID();
        UUID tagger = UUID.randomUUID();
        UUID taggedOnce = UUID.randomUUID();
        UUID optedOut = UUID.randomUUID();
        UUID pendingPlayer = UUID.randomUUID();

        TagState original = new TagState();
        original.currentIt = it;
        original.lastTagger = tagger;
        original.tagbackExpiryMillis = 123_456_789L;
        original.optedOut.add(optedOut);
        original.pending.put(pendingPlayer, PendingAction.BECOME_IT);

        PlayerStats stats = original.statsFor(taggedOnce);
        stats.timesTagged = 3;
        stats.tagsMade = 1;
        stats.minutesItOnline = 12.5;
        stats.minutesItOffline = 2.0;
        stats.points = -4.5;

        StateRepository.save(original, file);
        assertTrue(file.isFile());

        TagState loaded = StateRepository.load(file);

        assertEquals(it, loaded.currentIt);
        assertEquals(tagger, loaded.lastTagger);
        assertEquals(123_456_789L, loaded.tagbackExpiryMillis);
        assertEquals(original.optedOut, loaded.optedOut);
        assertEquals(original.pending, loaded.pending);
        assertEquals(stats, loaded.statsFor(taggedOnce));
    }

    @Test
    void loadingAMissingFileGivesAnEmptyState(@TempDir Path tempDir) throws IOException {
        File file = tempDir.resolve("missing.yml").toFile();
        TagState state = StateRepository.load(file);

        assertNull(state.currentIt);
        assertNull(state.lastTagger);
        assertEquals(0L, state.tagbackExpiryMillis);
        assertTrue(state.stats.isEmpty());
        assertTrue(state.optedOut.isEmpty());
        assertTrue(state.pending.isEmpty());
    }
}
