package dev.sirbepy.mc.tweaks.changelog;

import dev.sirbepy.mc.core.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What changed at each live restart, as book pages for /changelog: one page per restart, newest
 * first, a restart with more lines than fit carrying on to the next page under a "(cont.)" heading.
 * Entry lines are MiniMessage.
 */
public final class Changelog {

    /** One restart: an ISO date, a short title and the player-facing lines. */
    public record Entry(String date, String title, List<String> lines) {
    }

    // A written book page is 114 px wide and shows 14 lines; the client wraps by pixel width, so the
    // page breaks are worked out from the default font's glyph widths (each including its 1 px gap).
    static final int PAGE_WIDTH = 114;
    static final int LINES_PER_PAGE = 14;
    private static final DateTimeFormatter SHOWN_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private Changelog() {
    }

    /** The {@code entries:} list of a changelog.yml; entries missing a date or title are skipped. */
    public static List<Entry> load(ConfigurationSection root) {
        List<Entry> entries = new ArrayList<>();
        if (root == null) {
            return entries;
        }
        for (Map<?, ?> raw : root.getMapList("entries")) {
            Object date = raw.get("date");
            Object title = raw.get("title");
            if (date == null || title == null) {
                continue;
            }
            List<String> lines = new ArrayList<>();
            if (raw.get("lines") instanceof List<?> list) {
                list.forEach(line -> lines.add(String.valueOf(line)));
            }
            entries.add(new Entry(isoDate(date), String.valueOf(title), lines));
        }
        return entries;
    }

    /** Newest date first; entries sharing a date keep the file's order, which lists the latest first. */
    public static List<Component> pages(List<Entry> entries, Messages messages) {
        List<Component> pages = new ArrayList<>();
        List<Entry> sorted = entries.stream().sorted(Comparator.comparing(Entry::date).reversed()).toList();
        for (Entry entry : sorted) {
            addPages(pages, entry, messages);
        }
        if (pages.isEmpty()) {
            pages.add(messages.render("changelog-empty"));
        }
        return pages;
    }

    private static void addPages(List<Component> pages, Entry entry, Messages messages) {
        TextComponent.Builder page = null;
        int used = 0;
        for (String raw : entry.lines()) {
            Component line = messages.render("changelog-line", Placeholder.component("line", Messages.mini().deserialize(raw)));
            int height = lineCount(PLAIN.serialize(line), false);
            if (page == null || used + height > LINES_PER_PAGE) {
                if (page != null) {
                    pages.add(page.build());
                }
                Component heading = messages.render(page == null ? "changelog-heading" : "changelog-heading-continued",
                        Placeholder.unparsed("title", entry.title()), Placeholder.unparsed("date", shownDate(entry.date())));
                page = Component.text().append(heading);
                used = headingLines(heading);
            }
            page.append(Component.newline(), line);
            used += height;
        }
        if (page == null) {
            page = Component.text().append(messages.render("changelog-heading",
                    Placeholder.unparsed("title", entry.title()), Placeholder.unparsed("date", shownDate(entry.date()))));
        }
        pages.add(page.build());
    }

    /** The heading template's lines may carry their own newlines; titles render bold, the rest plain. */
    private static int headingLines(Component heading) {
        int lines = 0;
        boolean first = true;
        for (String part : PLAIN.serialize(heading).split("\n", -1)) {
            lines += lineCount(part, first);
            first = false;
        }
        return lines;
    }

    /** SnakeYAML reads an unquoted 2026-10-10 as a java.util.Date at UTC midnight, a quoted one as text. */
    private static String isoDate(Object date) {
        if (date instanceof Date d) {
            return d.toInstant().atZone(ZoneOffset.UTC).toLocalDate().toString();
        }
        return String.valueOf(date);
    }

    static String shownDate(String iso) {
        try {
            return LocalDate.parse(iso).format(SHOWN_DATE);
        } catch (DateTimeParseException e) {
            return iso;
        }
    }

    /** How many book lines this text wraps onto, wrapping at spaces like the client does. */
    static int lineCount(String text, boolean bold) {
        int lines = 1;
        int x = 0;
        int space = width(' ', bold);
        for (String word : text.split(" ", -1)) {
            int w = 0;
            for (int i = 0; i < word.length(); i++) {
                w += width(word.charAt(i), bold);
            }
            int needed = x == 0 ? w : x + space + w;
            if (needed <= PAGE_WIDTH) {
                x = needed;
                continue;
            }
            if (x > 0) {
                lines++;
            }
            // A word wider than the page is broken mid-word by the client.
            while (w > PAGE_WIDTH) {
                w -= PAGE_WIDTH;
                lines++;
            }
            x = w;
        }
        return lines;
    }

    private static int width(char c, boolean bold) {
        int w = switch (c) {
            case '!', ',', '.', ':', ';', 'i', '|', '\'' -> 2;
            case 'l', '`' -> 3;
            case 'I', 't', '[', ']', ' ' -> 4;
            case 'f', 'k', '<', '>', '(', ')', '{', '}', '"', '*' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
        return bold ? w + 1 : w;
    }
}
