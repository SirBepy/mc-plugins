package dev.sirbepy.mc.endfight;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.endfight.cult.CultConfig;
import dev.sirbepy.mc.endfight.perch.PerchGateConfig;
import dev.sirbepy.mc.endfight.reset.ResetConfig;
import dev.sirbepy.mc.endfight.reward.TierRewardRow;
import dev.sirbepy.mc.endfight.tier.Tier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

/** Snapshot of {@code config.yml}, rebuilt by {@code /endfight reload}. */
public final class EndFightConfig {

    private final boolean autoBuild;
    private final Messages messages;
    private final CultConfig cult;
    private final PerchGateConfig perchGate;
    private final ResetConfig reset;
    private final boolean rewardsEnabled;
    private final Map<Tier, TierRewardRow> tierTable;
    private final TierRewardRow firstKillRow;
    private final Tier defaultTier;
    private final long tierApplyDelayTicks;
    private final Boolean dkeForceInstalled;

    public EndFightConfig(FileConfiguration config) {
        this.autoBuild = config.getBoolean("arena.auto-build", true);
        this.messages = new Messages(config.getConfigurationSection("messages"));
        this.cult = new CultConfig(config.getConfigurationSection("cult"));
        this.perchGate = new PerchGateConfig(config.getConfigurationSection("perch-gate"));
        this.reset = new ResetConfig(config.getConfigurationSection("reset"));
        this.rewardsEnabled = config.getBoolean("rewards.enabled", true);
        ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");
        this.tierTable = TierRewardRow.load(rewardsSection);
        this.firstKillRow = TierRewardRow.loadFirstKill(rewardsSection, tierTable.get(Tier.FEROCIOUS));
        this.defaultTier = parseTier(config.getString("tiers.default"));
        this.tierApplyDelayTicks = config.getLong("tiers.apply-delay-ticks", 60L);
        this.dkeForceInstalled = config.isSet("tiers.dke.force-installed")
                ? config.getBoolean("tiers.dke.force-installed") : null;
    }

    private static Tier parseTier(String name) {
        if (name == null) {
            return Tier.FLEDGELING;
        }
        try {
            return Tier.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Tier.FLEDGELING;
        }
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

    public ResetConfig reset() {
        return reset;
    }

    /** Phase 3 (per-participant rewards) is live for the first fight (2026-10-10); false falls all
     * the way back to vanilla - no orb sweeping, no Dragon Hoards, no pending deliveries. */
    public boolean rewardsEnabled() {
        return rewardsEnabled;
    }

    /** {@code rewards.tiers.<tier-name>}'s row, approved defaults if unconfigured. */
    public TierRewardRow tierRow(Tier tier) {
        return tierTable.get(tier);
    }

    /** {@code rewards.first-kill}'s row: the Ferocious row's materials with a guaranteed roll and
     * 12000 XP by default. */
    public TierRewardRow firstKillRow() {
        return firstKillRow;
    }

    /** The altar's tier choice before anyone has ever touched it, and what a fresh
     * {@code tier.yml} starts from. */
    public Tier defaultTier() {
        return defaultTier;
    }

    /** How long after a dragon spawns {@code TierApplyListener} waits before pushing the locked
     * tier - comfortably past Dragonkind Evolved's own 1-second tick (see {@code DkeTierPusher}'s
     * class docs), so DKE has already started the dragon and this plugin's push always lands
     * last. */
    public long tierApplyDelayTicks() {
        return tierApplyDelayTicks;
    }

    /** {@code null} auto-detects Dragonkind Evolved through the datapack list; {@code true}/
     * {@code false} overrides that for a live datapack filename the auto-detect heuristic misses. */
    public Boolean dkeForceInstalled() {
        return dkeForceInstalled;
    }
}
