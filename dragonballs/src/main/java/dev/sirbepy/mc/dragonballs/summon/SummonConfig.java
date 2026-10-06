package dev.sirbepy.mc.dragonballs.summon;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Phase 2b's own config keys, read directly from the plugin's {@code FileConfiguration} (never
 * through {@link dev.sirbepy.mc.dragonballs.config.DragonBallsConfig}, which this phase doesn't
 * touch) so a missing key just falls back to its default rather than failing to load. Call
 * {@link #from} fresh each time rather than caching: that's what makes {@code /dragonballs reload}
 * pick up a changed value for free, with no wiring into this phase at all.
 */
public record SummonConfig(int radius, int idleTimeoutSeconds, int summonerOfflineSeconds) {

    /** Fixed by the rulebook, not owner-configurable: how long after summoning, with no judge
     * command yet, the "considering your words" line is announced once. */
    public static final int CONSIDERING_ANNOUNCE_SECONDS = 90;

    /** Fixed: the dragon-choice dialog/form/chat prompt's own timeout; closing it or letting it
     * expire leaves the balls untouched, which falls out naturally from never starting a session. */
    public static final int DIALOG_TIMEOUT_SECONDS = 60;

    /** Fixed: longer wish chat is truncated with a note rather than rejected outright. */
    public static final int MAX_WISH_MESSAGE_CHARS = 400;

    public static SummonConfig from(FileConfiguration config) {
        return new SummonConfig(
                Math.max(1, config.getInt("summon.radius", 2)),
                Math.max(1, config.getInt("summon.idle-timeout-seconds", 600)),
                Math.max(1, config.getInt("summon.summoner-offline-seconds", 180)));
    }
}
