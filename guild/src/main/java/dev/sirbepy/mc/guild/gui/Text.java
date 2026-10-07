package dev.sirbepy.mc.guild.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Small formatting helpers shared by the menus and commands. */
public final class Text {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Text() {
    }

    /** "2h 15m", "40m", "3d 4h". */
    public static String duration(long millis) {
        long minutes = Math.max(0, millis) / 60_000;
        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long mins = minutes % 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + mins + "m";
        }
        return Math.max(1, mins) + "m";
    }

    public static String dateTime(long millis, ZoneId zone) {
        return DATE_TIME.format(Instant.ofEpochMilli(millis).atZone(zone));
    }

    public static String coins(long amount) {
        return String.format(java.util.Locale.ROOT, "%,d", amount) + (Math.abs(amount) == 1 ? " coin" : " coins");
    }
}
