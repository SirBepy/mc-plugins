package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.endfight.tier.Tier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The approved default hoard-contents table per tier (Joe's reward spec, 2026-10-09), and that
 * {@code config.yml} can override any one field without disturbing the rest. */
class TierRewardRowTest {

    @Test
    void defaultsMatchTheApprovedTable() {
        Map<Tier, TierRewardRow> table = TierRewardRow.load(null);

        assertEquals(new TierRewardRow(500, 2, 2, 0, 0, 0, 0.10), table.get(Tier.FLEDGELING));
        assertEquals(new TierRewardRow(750, 3, 2, 1, 0, 0, 0.15), table.get(Tier.POWERFUL));
        assertEquals(new TierRewardRow(1000, 4, 4, 1, 0, 0, 0.25), table.get(Tier.CRUEL));
        assertEquals(new TierRewardRow(1500, 6, 4, 2, 0, 0, 0.50), table.get(Tier.IMMORTAL));
        assertEquals(new TierRewardRow(2000, 8, 6, 2, 0, 0, 0.75), table.get(Tier.FEROCIOUS));
        assertEquals(new TierRewardRow(3000, 9, 6, 3, 1, 2, 1.00), table.get(Tier.PRIMORDIAL));
        assertEquals(new TierRewardRow(4000, 10, 6, 4, 1, 2, 1.50), table.get(Tier.ASCENDED));
    }

    @Test
    // Joe, 2026-10-09: the first dragon is Ascended and rewards follow the dragon summoned.
    void firstKillIsTheAscendedRowWithTwelveThousandXp() {
        Map<Tier, TierRewardRow> table = TierRewardRow.load(null);
        TierRewardRow ascended = table.get(Tier.ASCENDED);
        TierRewardRow firstKill = TierRewardRow.loadFirstKill(null, table.get(Tier.FIRST_KILL));

        assertEquals(Tier.ASCENDED, Tier.FIRST_KILL);
        assertEquals(12000, firstKill.xp());
        assertEquals(new TierRewardRow(12000, ascended.diamonds(), ascended.shells(), ascended.gapples(),
                ascended.scrapMin(), ascended.scrapMax(), ascended.rollChance()), firstKill);
        assertEquals(1.5, firstKill.rollChance());
    }

    @Test
    void theShippedConfigKeepsTheAscendedRollChanceForTheFirstKill() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                java.util.Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")),
                java.nio.charset.StandardCharsets.UTF_8));
        ConfigurationSection rewards = config.getConfigurationSection("rewards");
        TierRewardRow firstKill = TierRewardRow.loadFirstKill(rewards, TierRewardRow.load(rewards).get(Tier.FIRST_KILL));

        assertEquals(1.5, firstKill.rollChance());
        assertEquals(12000, firstKill.xp());
    }

    @Test
    void aConfiguredFieldOverridesOnlyItself() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rewards.tiers.fledgeling.xp", 999);

        Map<Tier, TierRewardRow> table = TierRewardRow.load(config.getConfigurationSection("rewards"));

        assertEquals(999, table.get(Tier.FLEDGELING).xp());
        assertEquals(2, table.get(Tier.FLEDGELING).diamonds(), "an untouched field must keep its default");
    }
}
