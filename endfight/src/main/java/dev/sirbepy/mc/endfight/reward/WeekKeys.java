package dev.sirbepy.mc.endfight.reward;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * The Monday (Europe/Zagreb) that starts the current week, as a stable {@code yyyy-MM-dd} key -
 * used instead of a week-of-year number so a kill right at a DST transition or a year boundary
 * still lands in exactly one weekly bucket (the weekly Dragon Hoard limit - see
 * {@link RewardService}). {@code Clock.withZone} converts correctly across DST since
 * {@code LocalDate.now(Clock)} reads the clock's instant through that zone, not a fixed offset.
 */
public final class WeekKeys {

    public static final ZoneId ZONE = ZoneId.of("Europe/Zagreb");

    private WeekKeys() {
    }

    public static String current(Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
    }
}
