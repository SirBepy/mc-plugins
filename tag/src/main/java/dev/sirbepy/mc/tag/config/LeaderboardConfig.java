package dev.sirbepy.mc.tag.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;

import java.util.Locale;

/**
 * Typed view over the `leaderboard:` section: the in-world {@code TextDisplay} placed by
 * `/tag leaderboard place`. Its `board.*` templates are distinct keys from
 * `messages.leaderboard-header/entry`, which stay chat-only for `/tag top`.
 */
public final class LeaderboardConfig {

    private static final String DEFAULT_HEADER = "<gold><bold>Tag leaderboard</bold></gold> <gray>(lowest wins)</gray>";
    private static final String DEFAULT_ENTRY = "<gray><rank>.</gray> <white><player></white> <yellow><points></yellow>";
    private static final String DEFAULT_FOOTER = "<gray>IT: <green>☣</green> <it></gray>";
    private static final String DEFAULT_NOBODY = "nobody";
    private static final String DEFAULT_EMPTY = "<gray>No points yet.</gray>";

    public final int size;
    public final Display.Billboard billboard;
    public final float scale;
    public final int lineWidth;
    public final boolean seeThrough;
    public final String boardHeader;
    public final String boardEntry;
    public final String boardFooter;
    public final String boardNobody;
    public final String boardEmpty;

    private LeaderboardConfig(int size, Display.Billboard billboard, float scale, int lineWidth, boolean seeThrough,
                               String boardHeader, String boardEntry, String boardFooter, String boardNobody,
                               String boardEmpty) {
        this.size = size;
        this.billboard = billboard;
        this.scale = scale;
        this.lineWidth = lineWidth;
        this.seeThrough = seeThrough;
        this.boardHeader = boardHeader;
        this.boardEntry = boardEntry;
        this.boardFooter = boardFooter;
        this.boardNobody = boardNobody;
        this.boardEmpty = boardEmpty;
    }

    public static LeaderboardConfig from(ConfigurationSection section) {
        if (section == null) {
            return new LeaderboardConfig(10, Display.Billboard.CENTER, 0.75f, 250, false,
                    DEFAULT_HEADER, DEFAULT_ENTRY, DEFAULT_FOOTER, DEFAULT_NOBODY, DEFAULT_EMPTY);
        }
        ConfigurationSection board = section.getConfigurationSection("board");
        return new LeaderboardConfig(
                section.getInt("size", 10),
                parseBillboard(section.getString("billboard")),
                (float) section.getDouble("scale", 0.75),
                section.getInt("line-width", 250),
                section.getBoolean("see-through", false),
                board != null ? board.getString("header", DEFAULT_HEADER) : DEFAULT_HEADER,
                board != null ? board.getString("entry", DEFAULT_ENTRY) : DEFAULT_ENTRY,
                board != null ? board.getString("footer", DEFAULT_FOOTER) : DEFAULT_FOOTER,
                board != null ? board.getString("nobody", DEFAULT_NOBODY) : DEFAULT_NOBODY,
                board != null ? board.getString("empty", DEFAULT_EMPTY) : DEFAULT_EMPTY);
    }

    private static Display.Billboard parseBillboard(String text) {
        if (text == null) {
            return Display.Billboard.CENTER;
        }
        try {
            return Display.Billboard.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Display.Billboard.CENTER;
        }
    }
}
