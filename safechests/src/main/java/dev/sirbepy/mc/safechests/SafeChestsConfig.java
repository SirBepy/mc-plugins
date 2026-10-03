package dev.sirbepy.mc.safechests;

import dev.sirbepy.mc.core.Durations;
import dev.sirbepy.mc.core.Messages;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;

/** Snapshot of {@code config.yml}, rebuilt by {@code /safechests reload}. */
public final class SafeChestsConfig {

    private final boolean notifyOwnerOnAttempt;
    private final Duration fiveMinuteDuration;
    private final Duration requestExpiry;
    private final Duration debounce;
    private final String accessType;
    private final boolean bedrockFormsEnabled;
    private final boolean bedrockAutoOpenRequestForm;
    private final Messages messages;

    public SafeChestsConfig(FileConfiguration config) {
        this.notifyOwnerOnAttempt = config.getBoolean("alerts.notify-owner-on-attempt", false);
        this.fiveMinuteDuration = Durations.parse(config.getString("grants.five-minute-duration", "5m")).orElse(Duration.ofMinutes(5));
        this.requestExpiry = Durations.parse(config.getString("grants.request-expiry", "2m")).orElse(Duration.ofMinutes(2));
        this.debounce = Durations.parse(config.getString("grants.debounce", "3s")).orElse(Duration.ofSeconds(3));
        this.accessType = config.getString("access-type", "normal");
        this.bedrockFormsEnabled = config.getBoolean("bedrock.forms", true);
        this.bedrockAutoOpenRequestForm = config.getBoolean("bedrock.auto-open-request-form", false);
        this.messages = new Messages(config.getConfigurationSection("messages"));
    }

    public boolean notifyOwnerOnAttempt() {
        return notifyOwnerOnAttempt;
    }

    public Duration fiveMinuteDuration() {
        return fiveMinuteDuration;
    }

    public Duration requestExpiry() {
        return requestExpiry;
    }

    public Duration debounce() {
        return debounce;
    }

    public String accessType() {
        return accessType;
    }

    public boolean bedrockFormsEnabled() {
        return bedrockFormsEnabled;
    }

    public boolean bedrockAutoOpenRequestForm() {
        return bedrockAutoOpenRequestForm;
    }

    public Messages messages() {
        return messages;
    }
}
