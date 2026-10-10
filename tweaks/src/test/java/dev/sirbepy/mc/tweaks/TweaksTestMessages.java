package dev.sirbepy.mc.tweaks;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The jar's shipped {@code messages:} section, loaded the same way {@code TweaksPlugin.onEnable} builds it
 * from an empty server {@code config.yml}: {@code copyDefaults(true)} merges the jar's keys in since the
 * server file has none of its own. Tests assert against this instead of a hand-copied duplicate string, so
 * a wording change in config.yml fails the test that depends on it rather than drifting silently.
 */
public final class TweaksTestMessages {

    private TweaksTestMessages() {
    }

    public static Messages jarDefaults() {
        YamlConfiguration server = new YamlConfiguration();
        var stream = TweaksTestMessages.class.getClassLoader().getResourceAsStream("config.yml");
        if (stream == null) {
            throw new UncheckedIOException(new java.io.FileNotFoundException("tweaks' config.yml resource"));
        }
        server.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        server.options().copyDefaults(true);
        return new Messages(server.getConfigurationSection("messages"));
    }
}
