package dev.sirbepy.mc.tag.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Typed view over the `teleport-block:` section: opted-in players near IT can't use command or
 * plugin teleports to escape. Code defaults because a server's existing plugins/Tag/config.yml
 * predates this section and is never overwritten.
 */
public final class TeleportBlockConfig {

    private static final double DEFAULT_RADIUS = 100.0;
    private static final String DEFAULT_MESSAGE =
            "<red>You're within <radius> blocks of IT (<it>), so you can't teleport away.</red>";

    public final boolean enabled;
    public final double radius;
    public final String message;

    private TeleportBlockConfig(boolean enabled, double radius, String message) {
        this.enabled = enabled;
        this.radius = radius;
        this.message = message;
    }

    public static TeleportBlockConfig from(ConfigurationSection section) {
        if (section == null) {
            return new TeleportBlockConfig(true, DEFAULT_RADIUS, DEFAULT_MESSAGE);
        }
        return new TeleportBlockConfig(
                section.getBoolean("enabled", true),
                section.getDouble("radius", DEFAULT_RADIUS),
                section.getString("message", DEFAULT_MESSAGE));
    }
}
