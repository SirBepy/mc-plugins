package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.endfight.tier.Tier;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * One {@link Tier}'s reward table row (Joe's reward spec, 2026-10-09): the XP {@code giveExp}
 * every participant gets (weekly limit does not apply to it) and the Dragon Hoard's raw materials
 * (always in the hoard) plus its armor-roll chance - 0.10 to 1.0, except Ascended's 1.5, which
 * means one guaranteed roll plus a 50% chance at a second (see {@link RollEngine#rollCount}).
 * Every field is overridable per tier under {@code rewards.tiers.<tier-name>} in config.yml; the
 * values below are the approved defaults. {@code filler} is the hoard's bulk loot (10ca's spec, Joe
 * decided 2026-10-09).
 */
public record TierRewardRow(int xp, int diamonds, int shells, int gapples, int scrapMin, int scrapMax,
                             double rollChance, Filler filler) {

    /** The hoard's bulk loot: iron and gold ingots, emeralds, lapis and golden carrots. */
    public record Filler(int iron, int gold, int emeralds, int lapis, int carrots) {

        public static final Filler NONE = new Filler(0, 0, 0, 0, 0);
    }

    public TierRewardRow(int xp, int diamonds, int shells, int gapples, int scrapMin, int scrapMax, double rollChance) {
        this(xp, diamonds, shells, gapples, scrapMin, scrapMax, rollChance, Filler.NONE);
    }

    private static Map<Tier, TierRewardRow> defaults() {
        Map<Tier, TierRewardRow> map = new EnumMap<>(Tier.class);
        map.put(Tier.FLEDGELING, new TierRewardRow(500, 2, 2, 0, 0, 0, 0.10, new Filler(12, 12, 3, 16, 12)));
        map.put(Tier.POWERFUL, new TierRewardRow(750, 3, 2, 1, 0, 0, 0.15, new Filler(18, 18, 6, 24, 20)));
        map.put(Tier.CRUEL, new TierRewardRow(1000, 4, 4, 1, 0, 0, 0.25, new Filler(24, 24, 9, 32, 24)));
        map.put(Tier.IMMORTAL, new TierRewardRow(1500, 6, 4, 2, 0, 0, 0.50, new Filler(36, 36, 12, 48, 36)));
        map.put(Tier.FEROCIOUS, new TierRewardRow(2000, 8, 6, 2, 0, 0, 0.75, new Filler(48, 48, 18, 64, 48)));
        map.put(Tier.PRIMORDIAL, new TierRewardRow(3000, 9, 6, 3, 1, 2, 1.00, new Filler(72, 72, 24, 96, 52)));
        map.put(Tier.ASCENDED, new TierRewardRow(4000, 10, 6, 4, 1, 2, 1.50, new Filler(96, 96, 36, 128, 56)));
        return map;
    }

    /** {@code rewards.tiers.<tier-name>} for every tier, falling back to the approved defaults
     * for anything missing or when {@code rewardsSection} itself is null. */
    public static Map<Tier, TierRewardRow> load(ConfigurationSection rewardsSection) {
        Map<Tier, TierRewardRow> defaults = defaults();
        Map<Tier, TierRewardRow> table = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.values()) {
            TierRewardRow fallback = defaults.get(tier);
            ConfigurationSection section = rewardsSection == null ? null
                    : rewardsSection.getConfigurationSection("tiers." + tier.name().toLowerCase(Locale.ROOT));
            table.put(tier, fromConfig(section, fallback));
        }
        return table;
    }

    /** {@code rewards.first-kill}: the server's actual first-ever dragon kill, paid like the tier
     * it is fought at ({@link dev.sirbepy.mc.endfight.tier.Tier#FIRST_KILL}'s row, so a customised
     * row carries through: rewards follow the dragon summoned, Joe 2026-10-09) with its own XP
     * (default 12000); {@code roll-chance} overrides that row's chance only if set. */
    public static TierRewardRow loadFirstKill(ConfigurationSection rewardsSection, TierRewardRow tierRow) {
        ConfigurationSection section = rewardsSection == null ? null : rewardsSection.getConfigurationSection("first-kill");
        int xp = section == null ? 12000 : section.getInt("xp", 12000);
        double rollChance = section == null ? tierRow.rollChance() : section.getDouble("roll-chance", tierRow.rollChance());
        return new TierRewardRow(xp, tierRow.diamonds(), tierRow.shells(), tierRow.gapples(),
                tierRow.scrapMin(), tierRow.scrapMax(), rollChance, tierRow.filler());
    }

    private static TierRewardRow fromConfig(ConfigurationSection section, TierRewardRow fallback) {
        if (section == null) {
            return fallback;
        }
        return new TierRewardRow(
                section.getInt("xp", fallback.xp()),
                section.getInt("diamonds", fallback.diamonds()),
                section.getInt("shells", fallback.shells()),
                section.getInt("gapples", fallback.gapples()),
                section.getInt("scrap-min", fallback.scrapMin()),
                section.getInt("scrap-max", fallback.scrapMax()),
                section.getDouble("roll-chance", fallback.rollChance()),
                new Filler(
                        section.getInt("iron", fallback.filler().iron()),
                        section.getInt("gold", fallback.filler().gold()),
                        section.getInt("emeralds", fallback.filler().emeralds()),
                        section.getInt("lapis", fallback.filler().lapis()),
                        section.getInt("carrots", fallback.filler().carrots())));
    }
}
