package dev.sirbepy.mc.tweaks;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TweaksPluginTest {

    private static final UUID COFFIE = UUID.fromString("7dca8f8e-2981-4925-a7d7-128395ca11b9");
    private static final UUID LOGIKS = UUID.fromString("3e0a4456-e2a3-4ea8-9bfa-8e38ee4c863d");

    /** What getConfig() hands the plugin: the server's config.yml with the jar's config.yml as its defaults. */
    private YamlConfiguration serverConfig(String yaml) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        var stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        return config;
    }

    @Test
    void anOldConfigWithoutTheWishKeysFallsBackToTheJarsOwners() throws Exception {
        // Live's config.yml on 2026-10-08 held only the motd section.
        YamlConfiguration config = serverConfig("motd:\n  enabled: true\n");
        assertEquals(COFFIE, TweaksPlugin.ownerAt(config, "nether-wishes.owner"));
        assertEquals(LOGIKS, TweaksPlugin.ownerAt(config, "sea-gifts.owner"));
    }

    @Test
    void anOwnerInTheServerConfigWinsOverTheJars() throws Exception {
        UUID bot = UUID.fromString("00000000-0000-0000-0000-000000000001");
        YamlConfiguration config = serverConfig("nether-wishes:\n  owner: " + bot + "\n");
        assertEquals(bot, TweaksPlugin.ownerAt(config, "nether-wishes.owner"));
    }

    @Test
    void aMissingOrBrokenOwnerIsNobody() throws Exception {
        YamlConfiguration config = serverConfig("nether-wishes:\n  owner: not-a-uuid\n");
        assertNull(TweaksPlugin.ownerAt(config, "nether-wishes.owner"));
        assertNull(TweaksPlugin.ownerAt(config, "no-such-wish.owner"));
    }
}
