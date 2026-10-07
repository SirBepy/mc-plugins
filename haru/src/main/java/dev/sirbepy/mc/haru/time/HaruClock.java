package dev.sirbepy.mc.haru.time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;

/**
 * Every clock Haru reads, behind one seam so tests can pin the date and the world's day. "Real"
 * periods (quest board, curio, Secret Stock week) use the configured zone's calendar;
 * "Minecraft" periods (Everyday Wares restock, Wandering Wares rotation) use the world's full time.
 */
public interface HaruClock {

    LocalDate today();

    /** The configured zone's wall-clock time. */
    LocalDateTime localNow();

    /**
     * Which Spirit Request set is current: two a day, starting at midnight and noon, counted from the
     * epoch. Read from one {@link #localNow()} so midnight can't pair one day's date with the next's hour.
     */
    default long requestSet() {
        LocalDateTime now = localNow();
        return now.toLocalDate().toEpochDay() * 2 + now.getHour() / 12;
    }

    long nowMillis();

    /** The configured world's full time in ticks (24000 per Minecraft day). */
    long worldFullTime();

    default long minecraftDay() {
        return Math.floorDiv(worldFullTime(), 24000L);
    }

    default long rotationIndex(int rotationDays) {
        return Math.floorDiv(minecraftDay(), Math.max(1, rotationDays));
    }

    /** Minecraft days until the next rotation, counting today's remainder as one. */
    default long daysUntilRotation(int rotationDays) {
        int days = Math.max(1, rotationDays);
        return days - Math.floorMod(minecraftDay(), days);
    }

    default String weekKey() {
        LocalDate today = today();
        return today.get(IsoFields.WEEK_BASED_YEAR) + "-W" + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }
}
