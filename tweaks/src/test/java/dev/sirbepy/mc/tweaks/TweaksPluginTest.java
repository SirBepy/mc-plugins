package dev.sirbepy.mc.tweaks;

import dev.sirbepy.mc.core.Messages;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TweaksPluginTest {

    private static final UUID COFFIE = UUID.fromString("7dca8f8e-2981-4925-a7d7-128395ca11b9");
    private static final UUID LOGIKS = UUID.fromString("3e0a4456-e2a3-4ea8-9bfa-8e38ee4c863d");
    private static final UUID BEPY = UUID.fromString("a6f30e22-f8d5-4ae8-a69c-2f81662b1950");

    /** What getConfig() hands the plugin: the server's config.yml with the jar's config.yml as its defaults. */
    private YamlConfiguration serverConfig(String yaml) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        var stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        return config;
    }

    /**
     * Live's plugins/Tweaks/config.yml predates the {@code messages:} section entirely: this proves
     * {@code TweaksPlugin.onEnable}'s {@code copyDefaults(true)} step, exercised here the same way it
     * builds its {@code Messages} from a config.yml with no override, renders the jar's text instead of
     * "[missing message: ...]".
     */
    @Test
    void aServerConfigWithNoMessagesSectionStillRendersTheJarsText() throws Exception {
        YamlConfiguration config = serverConfig("motd:\n  enabled: true\n");
        config.options().copyDefaults(true);
        Messages messages = new Messages(config.getConfigurationSection("messages"));

        String text = PlainTextComponentSerializer.plainText().serialize(messages.render("wither-refusal-town"));

        assertEquals("You can't summon the Wither inside a town. Take the fight to the wilderness.", text);
    }

    @Test
    void anOldConfigWithoutTheWishKeysFallsBackToTheJarsOwners() throws Exception {
        // Live's config.yml on 2026-10-08 held only the motd section.
        YamlConfiguration config = serverConfig("motd:\n  enabled: true\n");
        assertEquals(COFFIE, TweaksPlugin.ownerAt(config, "nether-wishes.owner"));
        assertEquals(Set.of(LOGIKS, BEPY), seaGiftsOwners(config));
    }

    @Test
    void liveSeaGiftsConfigWithItsSingleOwnerKeepsHimAndGainsTheJarsList() throws Exception {
        // Live's config.yml since 2026-10-08 18:40 UTC: `seagifts owner` saved ItsLogiks under the old key.
        YamlConfiguration config = serverConfig("sea-gifts:\n  owner: " + LOGIKS + "\n");
        assertEquals(Set.of(LOGIKS, BEPY), seaGiftsOwners(config));
        UUID bot = UUID.fromString("00000000-0000-0000-0000-000000000001");
        config = serverConfig("sea-gifts:\n  owner: " + bot + "\n");
        assertEquals(Set.of(LOGIKS, BEPY, bot), seaGiftsOwners(config));
    }

    @Test
    void aSeaGiftsListInTheServerConfigReplacesTheJars() throws Exception {
        YamlConfiguration config = serverConfig("sea-gifts:\n  owners:\n    - " + BEPY + "\n    - not-a-uuid\n");
        assertEquals(Set.of(BEPY), seaGiftsOwners(config));
    }

    @Test
    void theJarKeepsSirBepyOffTownyAdminUntilTheServerFileSaysOtherwise() throws Exception {
        // Live's config.yml has no towny-admin-off key, so the jar's list applies.
        assertEquals(Set.of(BEPY), TweaksPlugin.uuidsAt(serverConfig("motd:\n  enabled: true\n"),
                TweaksPlugin.TOWNY_ADMIN_OFF));
        assertEquals(Set.of(), TweaksPlugin.uuidsAt(serverConfig("towny-admin-off: []\n"),
                TweaksPlugin.TOWNY_ADMIN_OFF));
    }

    private static Set<UUID> seaGiftsOwners(YamlConfiguration config) {
        return TweaksPlugin.ownersAt(config, "sea-gifts.owners", "sea-gifts.owner");
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
