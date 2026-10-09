package dev.sirbepy.mc.endfight.reset;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.configuration.ConfigurationSection;

/** Snapshot of the {@code reset} config section, rebuilt by {@code /endfight reload}. Defaults
 * match the verified vanilla End spawn point ({@code ServerLevel.END_SPAWN_POINT}, javap'd off the
 * 26.3 server jar as {@code BlockPos(100, 50, 0)}) and its platform ({@code EndPlatformFeature}:
 * 5x5 obsidian one block below that point, 3 air blocks above it) - a player stands at the
 * point's own Y, on top of the obsidian. */
public final class ResetConfig {

    private final boolean enabled;
    private final int restoreBlocksPerTick;
    private final int pollIntervalTicks;
    private final int lockoutTickIntervalTicks;
    private final double lockoutRadius;
    private final double platformX;
    private final double platformY;
    private final double platformZ;
    private final float platformYaw;
    private final Messages messages;

    public ResetConfig(ConfigurationSection section) {
        this.enabled = section == null || section.getBoolean("enabled", true);
        this.restoreBlocksPerTick = section != null ? section.getInt("restore-blocks-per-tick", 40_000) : 40_000;
        this.pollIntervalTicks = section != null ? section.getInt("poll-interval-ticks", 10) : 10;
        this.lockoutTickIntervalTicks = section != null ? section.getInt("lockout-interval-ticks", 20) : 20;
        this.lockoutRadius = section != null ? section.getDouble("lockout-radius", 80) : 80;
        ConfigurationSection platform = section != null ? section.getConfigurationSection("platform") : null;
        this.platformX = platform != null ? platform.getDouble("x", 100.5) : 100.5;
        this.platformY = platform != null ? platform.getDouble("y", 50) : 50;
        this.platformZ = platform != null ? platform.getDouble("z", 0.5) : 0.5;
        this.platformYaw = platform != null ? (float) platform.getDouble("yaw", 90) : 90f;
        this.messages = new Messages(section != null ? section.getConfigurationSection("messages") : null);
    }

    public boolean enabled() {
        return enabled;
    }

    public int restoreBlocksPerTick() {
        return restoreBlocksPerTick;
    }

    public int pollIntervalTicks() {
        return pollIntervalTicks;
    }

    public int lockoutTickIntervalTicks() {
        return lockoutTickIntervalTicks;
    }

    public double lockoutRadius() {
        return lockoutRadius;
    }

    public double platformX() {
        return platformX;
    }

    public double platformY() {
        return platformY;
    }

    public double platformZ() {
        return platformZ;
    }

    public float platformYaw() {
        return platformYaw;
    }

    public Messages messages() {
        return messages;
    }
}
