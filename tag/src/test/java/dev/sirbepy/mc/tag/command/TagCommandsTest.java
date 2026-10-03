package dev.sirbepy.mc.tag.command;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.tag.state.PlayerStats;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * docs/plan.md requires every player-facing string to come from config.yml; {@code /tag stats} and
 * {@code /tag top} used to build their output from concatenated Java strings instead (todo 06). These
 * prove the owner's configured template is what actually gets sent, not just that the default still
 * reads the same.
 */
class TagCommandsTest {

    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Messages messagesFrom(Map<String, String> entries) {
        YamlConfiguration root = new YamlConfiguration();
        entries.forEach((key, value) -> root.set("messages." + key, value));
        return new Messages(root.getConfigurationSection("messages"));
    }

    @Test
    void sendStatsRendersTheConfiguredStatsLineTemplate() {
        Messages messages = messagesFrom(Map.of("stats-line",
                "<player> has <points> pts, tagged <tagged>, made <made>, online <minutes-online>, offline <minutes-offline>"));
        PlayerStats stats = new PlayerStats();
        stats.points = 3.5;
        stats.timesTagged = 2;
        stats.tagsMade = 4;
        stats.minutesItOnline = 1.0;
        stats.minutesItOffline = 2.0;

        TagCommands.sendStats(player, messages, "Steve", stats);

        String text = plainText(player.nextComponentMessage());
        assertEquals("Steve has 3.5 pts, tagged 2, made 4, online 1.0, offline 2.0", text,
                "a configured override of stats-line must be what the player actually receives");
    }

    @Test
    void sendLeaderboardRendersTheConfiguredHeaderAndEntryTemplates() {
        Messages messages = messagesFrom(Map.of(
                "leaderboard-header", "== Top Taggers ==",
                "leaderboard-entry", "#<rank> <player> (<points>)"));
        UUID first = UUID.randomUUID();
        PlayerStats firstStats = new PlayerStats();
        firstStats.points = 1.0;
        List<Map.Entry<UUID, PlayerStats>> ranked = List.of(Map.entry(first, firstStats));

        TagCommands.sendLeaderboard(player, messages, ranked, uuid -> "Alex");

        assertEquals("== Top Taggers ==", plainText(player.nextComponentMessage()),
                "a configured override of leaderboard-header must be what the player actually receives");
        assertEquals("#1 Alex (1.0)", plainText(player.nextComponentMessage()),
                "a configured override of leaderboard-entry must be what the player actually receives");
    }

    @Test
    void defaultTemplatesStillProduceTheWordingTheE2eScraperParses() {
        Messages messages = messagesFrom(Map.of("stats-line",
                "<player> - points: <points>, tagged: <tagged>, tags made: <made>, minutes IT (online/offline): <minutes-online>/<minutes-offline>"));
        PlayerStats stats = new PlayerStats();
        stats.points = 10.0;
        stats.timesTagged = 1;
        stats.tagsMade = 0;

        TagCommands.sendStats(player, messages, "Steve", stats);

        String text = plainText(player.nextComponentMessage());
        assertTrue(text.contains("points: 10.0") && text.contains("tagged: 1") && text.contains("tags made: 0"),
                "e2e/tag.mjs parses these exact labels out of the default template");
    }

    private static String plainText(net.kyori.adventure.text.Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }
}
