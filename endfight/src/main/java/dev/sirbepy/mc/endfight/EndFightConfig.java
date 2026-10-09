package dev.sirbepy.mc.endfight;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.configuration.file.FileConfiguration;

/** Snapshot of {@code config.yml}, rebuilt by {@code /endfight reload}. */
public final class EndFightConfig {

    private final boolean autoBuild;
    private final Messages messages;

    public EndFightConfig(FileConfiguration config) {
        this.autoBuild = config.getBoolean("arena.auto-build", true);
        this.messages = new Messages(config.getConfigurationSection("messages"));
    }

    public boolean autoBuild() {
        return autoBuild;
    }

    public Messages messages() {
        return messages;
    }
}
