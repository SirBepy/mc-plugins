package dev.sirbepy.mc.guild.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuildConfigTest {

    @Test
    void theBoardHoldsFiveDailiesByDefault() {
        assertEquals(5, GuildConfig.from(new YamlConfiguration()).dailyContracts());
    }

    @Test
    void dailyContractsCanBeLoweredButNeverPastFive() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("daily-contracts", 3);
        assertEquals(3, GuildConfig.from(yaml).dailyContracts());
        yaml.set("daily-contracts", 9);
        assertEquals(5, GuildConfig.from(yaml).dailyContracts());
        yaml.set("daily-contracts", 0);
        assertEquals(1, GuildConfig.from(yaml).dailyContracts());
    }
}
