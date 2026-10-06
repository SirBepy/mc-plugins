package dev.sirbepy.mc.tweaks.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.server.ServerListPingEvent;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DailyMotdListenerTest {

    private static final List<String> LINES = List.of("A", "B", "C");

    private static ServerListPingEvent ping() {
        return new ServerListPingEvent("localhost", InetAddress.getLoopbackAddress(),
                Component.text("The Claude SMP"), 0, 20);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void sameDayAlwaysGivesTheSameLine() {
        LocalDate day = LocalDate.of(2026, 10, 6);
        assertEquals(DailyMotdListener.lineFor(day, LINES), DailyMotdListener.lineFor(day, LINES));
    }

    @Test
    void consecutiveDaysWalkTheListAndWrap() {
        LocalDate day = LocalDate.of(2026, 10, 6);
        String first = DailyMotdListener.lineFor(day, LINES);
        String second = DailyMotdListener.lineFor(day.plusDays(1), LINES);
        String third = DailyMotdListener.lineFor(day.plusDays(2), LINES);
        assertEquals(3, List.of(first, second, third).stream().distinct().count());
        assertEquals(first, DailyMotdListener.lineFor(day.plusDays(3), LINES));
    }

    @Test
    void pingShowsTheFixedFirstLineAndTodaysLine() {
        AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 10, 6));
        DailyMotdListener listener = new DailyMotdListener("The Claude SMP", LINES, today::get);

        ServerListPingEvent monday = ping();
        listener.onPing(monday);
        String expected = "The Claude SMP\n" + DailyMotdListener.lineFor(today.get(), LINES);
        assertEquals(expected, plain(monday.motd()));

        today.set(today.get().plusDays(1));
        ServerListPingEvent tuesday = ping();
        listener.onPing(tuesday);
        assertNotEquals(expected, plain(tuesday.motd()), "the next day shows the next line");
    }

    @Test
    void anEmptyListLeavesTheServerPropertiesMotd() {
        DailyMotdListener listener = new DailyMotdListener("The Claude SMP", List.of(), LocalDate::now);
        ServerListPingEvent event = ping();
        listener.onPing(event);
        assertEquals("The Claude SMP", plain(event.motd()));
    }

    @Test
    void shippedConfigHasJoesTwelveLines() {
        var stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        List<String> lines = config.getStringList("motd.daily-lines");
        assertEquals(12, lines.size());
        assertFalse(lines.stream().anyMatch(String::isBlank));
        assertEquals("The Claude SMP", config.getString("motd.first-line"));
    }
}
