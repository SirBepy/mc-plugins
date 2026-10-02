package dev.sirbepy.mc.tag.log;

import org.bukkit.configuration.ConfigurationSection;

/** One `logs.<event>` config entry: whether it fires, who sees it, and how it reads. */
public final class LogEntry {

    public final boolean enabled;
    public final boolean broadcast;
    public final boolean console;
    public final String format;

    private LogEntry(boolean enabled, boolean broadcast, boolean console, String format) {
        this.enabled = enabled;
        this.broadcast = broadcast;
        this.console = console;
        this.format = format;
    }

    public static LogEntry from(ConfigurationSection section) {
        if (section == null) {
            return new LogEntry(false, false, false, "");
        }
        return new LogEntry(
                section.getBoolean("enabled", true),
                section.getBoolean("broadcast", false),
                section.getBoolean("console", true),
                section.getString("format", ""));
    }
}
