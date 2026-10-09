package dev.sirbepy.mc.endfight.perch;

import org.bukkit.configuration.ConfigurationSection;

/** Snapshot of the {@code perch-gate} config section, rebuilt by {@code /endfight reload}. */
public final class PerchGateConfig {

    private final boolean enabled;
    private final double pillarTopRadius;
    private final double fallbackRadius;
    private final double fallbackMinY;

    public PerchGateConfig(ConfigurationSection section) {
        this.enabled = section == null || section.getBoolean("enabled", true);
        this.pillarTopRadius = section != null ? section.getDouble("pillar-top-radius", 3) : 3;
        this.fallbackRadius = section != null ? section.getDouble("fallback-radius", 60) : 60;
        this.fallbackMinY = section != null ? section.getDouble("fallback-min-y", 70) : 70;
    }

    public boolean enabled() {
        return enabled;
    }

    public double pillarTopRadius() {
        return pillarTopRadius;
    }

    public double fallbackRadius() {
        return fallbackRadius;
    }

    public double fallbackMinY() {
        return fallbackMinY;
    }
}
