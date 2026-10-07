package dev.sirbepy.mc.guild.time;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/** Every clock the Guild reads, behind one seam so tests can pin the date. */
public interface GuildClock {

    /** The configured zone's wall-clock time. */
    LocalDateTime localNow();

    long nowMillis();

    default LocalDate today() {
        return localNow().toLocalDate();
    }

    /** Key of the current real day, e.g. "2026-10-08": dailies and every daily cap. */
    default String dayKey() {
        return today().toString();
    }

    /** Key of the current week, the date of its Monday: weeklies, commissions. */
    default String weekKey() {
        return today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
    }
}
