package dev.sirbepy.mc.endfight.opening;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndOpeningTest {

    private static final ZoneId ZAGREB = ZoneId.of("Europe/Zagreb");
    private static final Instant OPENS = OffsetDateTime.parse("2026-10-10T20:30:00+02:00").toInstant();
    private static final Logger LOG = Logger.getLogger("test");

    @TempDir
    Path dir;

    private EndOpening opening(String opensAt) throws Exception {
        File file = dir.resolve("opening.yml").toFile();
        if (opensAt != null) {
            Files.writeString(file.toPath(), "opens-at: \"" + opensAt + "\"\n");
        }
        return new EndOpening(file, List.of(60, 30, 15, 10, 5, 1), Set.of(60), ZAGREB, LOG);
    }

    private static Instant at(String hhmm) {
        return OffsetDateTime.parse("2026-10-10T" + hhmm + ":00+02:00").toInstant();
    }

    @Test
    void closedBeforeTheOpeningTimeOpenAtAndAfterIt() throws Exception {
        EndOpening opening = opening("2026-10-10T20:30:00+02:00");

        assertTrue(opening.closed(at("19:00")));
        assertTrue(opening.closed(OPENS.minusSeconds(1)));
        assertFalse(opening.closed(OPENS));
        assertFalse(opening.closed(at("22:00")));
    }

    @Test
    void noFileOrABlankTimeMeansNoGate() throws Exception {
        assertNull(opening(null).opensAt());
        assertFalse(opening(null).closed(at("19:00")));
        assertFalse(opening("").closed(at("19:00")));
        assertFalse(opening("not a date").closed(at("19:00")));
    }

    @Test
    void eachCountdownStepFiresOnceAtItsTimeAndTheSixtyMinuteOneCarriesTheWarpHint() throws Exception {
        EndOpening opening = opening("2026-10-10T20:30:00+02:00");

        assertTrue(opening.due(at("19:29")).isEmpty());
        List<EndOpening.Step> at1930 = opening.due(at("19:30"));
        assertEquals(1, at1930.size());
        assertEquals(60, at1930.get(0).minutesLeft());
        assertTrue(at1930.get(0).warpHint());
        assertTrue(opening.due(at("19:30").plusSeconds(1)).isEmpty());

        List<EndOpening.Step> at2000 = opening.due(at("20:00"));
        assertEquals(30, at2000.get(0).minutesLeft());
        assertFalse(at2000.get(0).warpHint());

        List<EndOpening.Step> open = opening.due(OPENS);
        assertEquals(1, open.size());
        assertTrue(open.get(0).opening());
        assertTrue(opening.due(OPENS.plusSeconds(5)).isEmpty());
    }

    @Test
    void aRestartLongAfterAStepNeverReplaysIt() throws Exception {
        EndOpening opening = opening("2026-10-10T20:30:00+02:00");

        assertTrue(opening.due(at("22:00")).isEmpty());
        assertTrue(opening.due(at("19:35")).isEmpty());
        assertEquals(1, opening.due(at("19:31")).size());
    }

    @Test
    void joinersHearTheWarpHintOnlyFromTheSixtyMinuteMarkOn() throws Exception {
        EndOpening opening = opening("2026-10-10T20:30:00+02:00");

        assertFalse(opening.warpHintStarted(at("19:05")));
        assertTrue(opening.warpHintStarted(at("19:30")));
        assertTrue(opening.warpHintStarted(at("20:10")));
    }

    @Test
    void labelsShowTheLocalClockAndTimeLeftRoundedUp() throws Exception {
        EndOpening opening = opening("2026-10-10T20:30:00+02:00");

        assertEquals("20:30 CEST", opening.clockLabel());
        assertEquals("1h 30m", opening.leftLabel(at("19:00")));
        assertEquals("1h", opening.leftLabel(at("19:30")));
        assertEquals("5 minutes", opening.leftLabel(at("20:25")));
        assertEquals("1 minute", opening.leftLabel(OPENS.minusSeconds(20)));
    }

    @Test
    void settingATimeSavesItAndClearingRemovesTheGate() throws Exception {
        EndOpening opening = opening(null);

        opening.setOpensAt(opening.parse("20:30", at("12:00")));
        assertEquals(OPENS, opening.opensAt());
        EndOpening reloaded = opening(null);
        assertEquals(OPENS, reloaded.opensAt());

        reloaded.setOpensAt(null);
        assertFalse(opening(null).closed(at("19:00")));
    }

    @Test
    void parseTakesAFullIsoTimeToo() throws Exception {
        EndOpening opening = opening(null);

        assertEquals(OPENS, opening.parse("2026-10-10T18:30Z", at("12:00")));
        assertEquals(Duration.ZERO, Duration.between(OPENS, opening.parse("2026-10-10T20:30+02:00", at("12:00"))));
    }
}
