package dev.sirbepy.mc.tag.config;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.tag.log.LogService;
import dev.sirbepy.mc.tag.logic.PointsCurve;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Typed view over config.yml, rebuilt on every `/tag reload`. */
public final class TagConfig {

    public final boolean allowProjectileTags;
    public final long tagbackSeconds;
    public final long tagbackMessageCooldownSeconds;

    public final double pointsOnTag;
    public final double pointsBase;
    public final double pointsOfflineFactor;
    public final PointsCurve pointsCurve;
    public final double pointsStep;
    public final long pointsIntervalSeconds;

    public final String prefixTemplate;
    public final int prefixPriority;

    public final HeadConfig head;
    public final long headWatchdogSeconds;
    public final AfkConfig afk;
    public final LeaderboardConfig leaderboard;
    public final TrackerConfig tracker;
    public final TeleportBlockConfig teleportBlock;
    public final Messages messages;
    public final LogService logs;

    private TagConfig(FileConfiguration root, LogService logs) {
        this.allowProjectileTags = root.getBoolean("tag.allow-projectiles", false);
        this.tagbackSeconds = root.getLong("tagback.seconds", 60);
        this.tagbackMessageCooldownSeconds = root.getLong("tagback.message-cooldown-seconds", 3);

        this.pointsOnTag = root.getDouble("points.on-tag", 10.0);
        this.pointsBase = root.getDouble("points.base", 1.0);
        this.pointsOfflineFactor = root.getDouble("points.offline-factor", 0.5);
        this.pointsCurve = PointsCurve.parse(root.getString("points.curve"), PointsCurve.LOG);
        this.pointsStep = root.getDouble("points.step", 0.5);
        this.pointsIntervalSeconds = root.getLong("points.interval-seconds", 60);

        this.prefixTemplate = root.getString("prefix.format", "<green>☣</green> ");
        this.prefixPriority = root.getInt("prefix.priority", 0);

        this.head = HeadConfig.from(root.getConfigurationSection("head"));
        this.headWatchdogSeconds = root.getLong("head.watchdog-interval-seconds", 5);
        this.afk = AfkConfig.from(root.getConfigurationSection("afk"));
        this.leaderboard = LeaderboardConfig.from(root.getConfigurationSection("leaderboard"));
        this.tracker = TrackerConfig.from(root.getConfigurationSection("tracker"));
        this.teleportBlock = TeleportBlockConfig.from(root.getConfigurationSection("teleport-block"));
        this.messages = new Messages(root.getConfigurationSection("messages"));
        this.logs = logs;
    }

    private static final String TAGBACK_FORMAT_PATH = "logs.tagback-blocked.format";
    private static final String LEGACY_TAGBACK_FORMAT = "<gray>You can't tag <player> back yet.</gray>";
    private static final String TAGBACK_FORMAT = "<gray>You can't tag <player> back for another <seconds>s.</gray>";

    /**
     * A server's config.yml is never overwritten, so one written before {@code <seconds>} existed
     * still carries the old default; swap that exact text, leaving an owner-customised format
     * alone. Returns true when it changed something, so the caller saves.
     */
    public static boolean upgradeLegacyDefaults(FileConfiguration root) {
        if (!LEGACY_TAGBACK_FORMAT.equals(root.getString(TAGBACK_FORMAT_PATH))) {
            return false;
        }
        root.set(TAGBACK_FORMAT_PATH, TAGBACK_FORMAT);
        return true;
    }

    public static TagConfig load(FileConfiguration root, LogService logs) {
        logs.reload(root.getConfigurationSection("logs"));
        return new TagConfig(root, logs);
    }
}
