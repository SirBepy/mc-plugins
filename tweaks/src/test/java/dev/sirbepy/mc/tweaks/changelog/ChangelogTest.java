package dev.sirbepy.mc.tweaks.changelog;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.tweaks.TweaksTestMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangelogTest {

    private final Messages messages = TweaksTestMessages.jarDefaults();

    private static String plain(Component page) {
        return PlainTextComponentSerializer.plainText().serialize(page);
    }

    private List<String> pages(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return Changelog.pages(Changelog.load(config), messages).stream().map(ChangelogTest::plain).toList();
    }

    @Test
    void eachRestartGetsItsOwnPageNewestFirst() throws Exception {
        List<String> pages = pages("""
                entries:
                  - {date: 2026-10-08, title: Older, lines: [old change]}
                  - {date: 2026-10-10, title: Latest, lines: [new change]}
                  - {date: 2026-10-10, title: Earlier that day, lines: [morning change]}
                """);
        assertEquals(3, pages.size());
        assertEquals("Latest\n10 Oct 2026\n- new change", pages.get(0));
        assertTrue(pages.get(1).startsWith("Earlier that day\n10 Oct 2026"));
        assertTrue(pages.get(2).startsWith("Older\n8 Oct 2026"));
    }

    @Test
    void aLongRestartCarriesOnToTheNextPageUnderAContinuedHeading() throws Exception {
        StringBuilder yaml = new StringBuilder("entries:\n  - date: 2026-10-10\n    title: Big one\n    lines:\n");
        for (int i = 1; i <= 20; i++) {
            yaml.append("      - change number ").append(i).append('\n');
        }
        List<String> pages = pages(yaml.toString());
        assertEquals(2, pages.size());
        // Heading (2 lines) + 12 one-line changes fills the first page's 14 lines.
        assertTrue(pages.get(0).endsWith("- change number 12"));
        assertTrue(pages.get(1).startsWith("Big one (cont.)\n10 Oct 2026\n- change number 13"));
        assertTrue(pages.get(1).endsWith("- change number 20"));
    }

    @Test
    void linesAreMiniMessage() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("entries:\n  - {date: 2026-10-10, title: T, lines: ['<red>Hot</red> and \\<item>']}\n");
        String page = plain(Changelog.pages(Changelog.load(config), messages).getFirst());
        assertTrue(page.endsWith("- Hot and <item>"), page);
    }

    @Test
    void wrappingCountsBookLinesByGlyphWidth() {
        assertEquals(1, Changelog.lineCount("- The End is open", false));
        // 19 sixes is 114 px, exactly one line; one more character wraps.
        assertEquals(1, Changelog.lineCount("a".repeat(19), false));
        assertEquals(2, Changelog.lineCount("a".repeat(20), false));
        assertEquals(2, Changelog.lineCount("- The End is open for the first dragon fight", false));
        // Narrow letters fit more per line.
        assertEquals(1, Changelog.lineCount("i".repeat(57), false));
    }

    @Test
    void anEmptyOrMissingChangelogStillOpensABook() throws Exception {
        assertEquals(List.of("Nothing in the changelog yet."), pages("entries: []\n"));
        assertEquals(List.of("Nothing in the changelog yet."), pages("other: 1\n"));
    }

    @Test
    void anEntryWithoutADateOrTitleIsSkipped() throws Exception {
        List<String> pages = pages("""
                entries:
                  - {title: No date, lines: [x]}
                  - {date: 2026-10-10, lines: [y]}
                  - {date: 2026-10-09, title: Kept}
                """);
        assertEquals(List.of("Kept\n9 Oct 2026"), pages);
    }

    @Test
    void theShippedChangelogFitsEveryLineAndKeepsItsDatesInOrder() {
        var stream = getClass().getClassLoader().getResourceAsStream("changelog.yml");
        YamlConfiguration shipped = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        List<Changelog.Entry> entries = Changelog.load(shipped);
        assertFalse(entries.isEmpty());
        List<String> dates = new ArrayList<>(entries.stream().map(Changelog.Entry::date).toList());
        List<String> sorted = new ArrayList<>(dates);
        sorted.sort(java.util.Comparator.reverseOrder());
        assertEquals(sorted, dates, "changelog.yml lists the newest restart first");
        for (Changelog.Entry entry : entries) {
            assertTrue(Changelog.shownDate(entry.date()).matches("\\d{1,2} [A-Z][a-z]{2} \\d{4}"), entry.date());
        }
        List<String> pages = Changelog.pages(entries, messages).stream().map(ChangelogTest::plain).toList();
        assertFalse(pages.stream().anyMatch(p -> p.contains("[missing message")));
    }
}
