package dev.sirbepy.mc.endfight;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.endfight.cult.CultConfig;
import dev.sirbepy.mc.endfight.perch.PerchGateConfig;
import dev.sirbepy.mc.endfight.reward.RewardBundle;
import org.bukkit.configuration.file.FileConfiguration;

/** Snapshot of {@code config.yml}, rebuilt by {@code /endfight reload}. */
public final class EndFightConfig {

    private final boolean autoBuild;
    private final Messages messages;
    private final CultConfig cult;
    private final PerchGateConfig perchGate;
    private final boolean rewardsEnabled;
    private final RewardBundle rewardBundle;

    public EndFightConfig(FileConfiguration config) {
        this.autoBuild = config.getBoolean("arena.auto-build", true);
        this.messages = new Messages(config.getConfigurationSection("messages"));
        this.cult = new CultConfig(config.getConfigurationSection("cult"));
        this.perchGate = new PerchGateConfig(config.getConfigurationSection("perch-gate"));
        this.rewardsEnabled = config.getBoolean("rewards.enabled", true);
        this.rewardBundle = RewardBundle.from(config.getConfigurationSection("rewards.bundle"));
    }

    public boolean autoBuild() {
        return autoBuild;
    }

    public Messages messages() {
        return messages;
    }

    public CultConfig cult() {
        return cult;
    }

    public PerchGateConfig perchGate() {
        return perchGate;
    }

    /** Phase 3 (per-participant rewards) is live for the first fight (2026-10-10); false falls all
     * the way back to vanilla - no suppressed orbs, no bundles, no pending deliveries. */
    public boolean rewardsEnabled() {
        return rewardsEnabled;
    }

    public RewardBundle rewardBundle() {
        return rewardBundle;
    }
}
