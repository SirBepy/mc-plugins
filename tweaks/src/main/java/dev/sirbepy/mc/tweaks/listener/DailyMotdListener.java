package dev.sirbepy.mc.tweaks.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/**
 * Rewrites the server-list MOTD on every ping: a fixed first line, then one of the configured
 * lines picked by today's date. Picking on each ping instead of on a timer means the line flips at
 * midnight with no restart and no scheduled task.
 */
public final class DailyMotdListener implements Listener {

    private final String firstLine;
    private final List<String> dailyLines;
    private final Supplier<LocalDate> today;

    public DailyMotdListener(String firstLine, List<String> dailyLines, Supplier<LocalDate> today) {
        this.firstLine = firstLine;
        this.dailyLines = List.copyOf(dailyLines);
        this.today = today;
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        if (dailyLines.isEmpty()) {
            return;
        }
        event.motd(Component.text(firstLine).appendNewline().append(Component.text(lineFor(today.get(), dailyLines))));
    }

    /** Consecutive days walk the list in order and wrap around, so every line gets its day. */
    public static String lineFor(LocalDate date, List<String> lines) {
        return lines.get((int) Math.floorMod(date.toEpochDay(), (long) lines.size()));
    }
}
