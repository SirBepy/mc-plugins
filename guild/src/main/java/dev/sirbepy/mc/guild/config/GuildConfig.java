package dev.sirbepy.mc.guild.config;

import org.bukkit.configuration.ConfigurationSection;

import java.time.ZoneId;
import java.util.UUID;

/** {@code plugins/Guild/config.yml}, read once per load or {@code /guildadmin reload}. */
public record GuildConfig(UUID guildmaster, ZoneId zone, int registrationFee, int dailyCoinCap, int dailyLevelCap,
                          double commissionRate, int guildmasterDailyWithdrawal, int commissionsPerWeek,
                          int commissionMinReward, int commissionMaxReward, int announcementCooldownMinutes,
                          double grandQuestShareCap, double farmEnvironmentShare, int farmChunkKillLimit,
                          int farmChunkWindowMinutes) {

    public static final UUID IMMIJO = UUID.fromString("8c0c13b9-057a-4ac8-aacf-7b6c77f8742b");

    public static GuildConfig defaults(UUID guildmaster, ZoneId zone) {
        return new GuildConfig(guildmaster, zone, 500, 2000, 15, 0.05, 1000, 3, 10, 1000, 30, 0.25, 0.5, 30, 10);
    }

    public static GuildConfig from(ConfigurationSection c) {
        UUID gm;
        try {
            gm = UUID.fromString(c.getString("guildmaster", IMMIJO.toString()));
        } catch (IllegalArgumentException e) {
            gm = IMMIJO;
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(c.getString("timezone", "Europe/Zagreb"));
        } catch (RuntimeException e) {
            zone = ZoneId.of("Europe/Zagreb");
        }
        // The dragon's caps are ceilings: a config can lower them but never lift them past the wish's terms.
        return new GuildConfig(gm, zone,
                Math.max(0, c.getInt("registration-fee", 500)),
                clamp(c.getInt("daily-coin-cap", 2000), 0, 2000),
                clamp(c.getInt("daily-level-cap", 15), 0, 15),
                Math.min(0.05, Math.max(0, c.getDouble("commission-rate", 0.05))),
                clamp(c.getInt("guildmaster-daily-withdrawal", 1000), 0, 1000),
                clamp(c.getInt("commissions-per-week", 3), 0, 3),
                Math.max(1, c.getInt("commission-min-reward", 10)),
                clamp(c.getInt("commission-max-reward", 1000), 1, 1000),
                Math.max(0, c.getInt("announcement-cooldown-minutes", 30)),
                // Below 2% (the share threshold) nobody could both qualify and leave room for others.
                Math.min(1, Math.max(0.02, c.getDouble("grand-quest-share-cap", 0.25))),
                Math.min(1, Math.max(0, c.getDouble("farm-environment-share", 0.5))),
                Math.max(1, c.getInt("farm-chunk-kill-limit", 30)),
                Math.max(1, c.getInt("farm-chunk-window-minutes", 10)));
    }

    public boolean isGuildmaster(UUID player) {
        return guildmaster.equals(player);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
