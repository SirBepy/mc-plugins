package dev.sirbepy.mc.cartel;

import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.util.UUID;

public record CartelConfig(UUID owner, Duration guardLength, Duration guardCooldown, Duration hitLength,
                           Duration hitCooldown, int hitDistance, int loseTrailDistance, int retracks,
                           double health, double speed) {

    public static CartelConfig from(ConfigurationSection c) {
        return new CartelConfig(
                parseOwner(c.getString("owner", "")),
                Duration.ofMinutes(c.getLong("guard-minutes", 10)),
                Duration.ofMinutes(c.getLong("guard-cooldown-minutes", 120)),
                Duration.ofMinutes(c.getLong("hit-minutes", 3)),
                Duration.ofMinutes(c.getLong("hit-cooldown-minutes", 1440)),
                c.getInt("hit-distance", 10),
                c.getInt("lose-trail-distance", 48),
                c.getInt("retracks", 2),
                c.getDouble("health", 40),
                c.getDouble("speed", 0.33));
    }

    private static UUID parseOwner(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
