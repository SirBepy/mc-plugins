package dev.sirbepy.mc.endfight.reward;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** The weekly Dragon Hoard limit resets Monday 00:00 Europe/Zagreb - a stable date key, not a
 * week-of-year number, so it survives a DST edge (see {@link RewardService}). */
class WeekKeysTest {

    @Test
    void aSaturdayKeysToThatWeeksMonday() {
        Clock saturday = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);
        assertEquals("2026-10-05", WeekKeys.current(saturday));
    }

    @Test
    void mondayZeroZeroZeroZagrebAlreadyKeysToTheNewWeek() {
        // 2026-10-05 00:00 Europe/Zagreb (CEST, UTC+2) is 2026-10-04 22:00Z.
        Clock mondayMidnightZagreb = Clock.fixed(Instant.parse("2026-10-04T22:00:00Z"), ZoneOffset.UTC);
        assertEquals("2026-10-05", WeekKeys.current(mondayMidnightZagreb));
    }

    @Test
    void justBeforeMondayZagrebIsStillTheOldWeek() {
        // 2026-10-04 23:59 Europe/Zagreb (CEST) is 2026-10-04 21:59Z - one minute before reset.
        Clock sundayNight = Clock.fixed(Instant.parse("2026-10-04T21:59:00Z"), ZoneOffset.UTC);
        assertEquals("2026-09-28", WeekKeys.current(sundayNight));
    }

    @Test
    void aDstTransitionWeekendStillResetsOnTheRightCalendarMonday() {
        // Europe/Zagreb falls back from CEST to CET on 2026-10-25. The following Monday
        // (2026-10-26) must still key correctly across that transition.
        Clock afterDstFallback = Clock.fixed(Instant.parse("2026-10-26T01:00:00Z"), ZoneOffset.UTC); // 02:00 CET
        assertEquals("2026-10-26", WeekKeys.current(afterDstFallback));
    }

    @Test
    void differentWeeksProduceDifferentKeys() {
        Clock weekOne = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);
        Clock weekTwo = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);
        assertNotEquals(WeekKeys.current(weekOne), WeekKeys.current(weekTwo));
    }
}
