package dev.sirbepy.mc.tag.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Typed view over the `afk:` config section: idle detection, multiplier exclusion, IT auto-pass,
 * and the note IT gets for hitting an AFK (untaggable) player.
 */
public final class AfkConfig {

    // Code default: a server's existing config.yml predates this key and is never overwritten.
    private static final String DEFAULT_UNTAGGABLE_MESSAGE = "<gray><player> is AFK and can't be tagged.</gray>";

    public final boolean enabled;
    public final long thresholdSeconds;
    public final boolean excludeFromMultiplier;
    public final boolean autoPass;
    public final long itPassAfterSeconds;
    public final long itPassWarningSeconds;
    public final long checkIntervalSeconds;
    public final String untaggableMessage;

    private AfkConfig(boolean enabled, long thresholdSeconds, boolean excludeFromMultiplier, boolean autoPass,
                       long itPassAfterSeconds, long itPassWarningSeconds, long checkIntervalSeconds,
                       String untaggableMessage) {
        this.enabled = enabled;
        this.thresholdSeconds = thresholdSeconds;
        this.excludeFromMultiplier = excludeFromMultiplier;
        this.autoPass = autoPass;
        this.itPassAfterSeconds = itPassAfterSeconds;
        this.itPassWarningSeconds = itPassWarningSeconds;
        this.checkIntervalSeconds = checkIntervalSeconds;
        this.untaggableMessage = untaggableMessage;
    }

    public static AfkConfig from(ConfigurationSection section) {
        if (section == null) {
            return new AfkConfig(true, 300, true, true, 600, 60, 1, DEFAULT_UNTAGGABLE_MESSAGE);
        }
        return new AfkConfig(
                section.getBoolean("enabled", true),
                section.getLong("threshold-seconds", 300),
                section.getBoolean("exclude-from-multiplier", true),
                section.getBoolean("auto-pass", true),
                section.getLong("it-pass-after-seconds", 600),
                section.getLong("it-pass-warning-seconds", 60),
                section.getLong("check-interval-seconds", 1),
                section.getString("untaggable-message", DEFAULT_UNTAGGABLE_MESSAGE));
    }
}
