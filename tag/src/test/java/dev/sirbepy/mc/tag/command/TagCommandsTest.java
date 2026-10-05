package dev.sirbepy.mc.tag.command;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.tag.NoopNamesApi;
import dev.sirbepy.mc.tag.TagService;
import dev.sirbepy.mc.tag.board.LeaderboardEntityService;
import dev.sirbepy.mc.tag.board.ScoreboardService;
import dev.sirbepy.mc.tag.config.TagConfig;
import dev.sirbepy.mc.tag.head.HeadService;
import dev.sirbepy.mc.tag.log.LogService;
import dev.sirbepy.mc.tag.logic.LeaderboardRanking;
import dev.sirbepy.mc.tag.prefix.PrefixService;
import dev.sirbepy.mc.tag.state.PlayerStats;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        List<LeaderboardRanking.Row> ranked = List.of(new LeaderboardRanking.Row(1, UUID.randomUUID(), "Alex", 1.0));

        TagCommands.sendLeaderboard(player, messages, ranked);

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

    @Test
    void handleLeaderboardPlaceByConsoleIsRejectedAndNeverTouchesState(@TempDir Path tempDir) {
        YamlConfiguration root = new YamlConfiguration();
        root.set("messages.leaderboard-player-only", "<red>players only</red>");
        TagConfig config = TagConfig.load(root, new LogService(null));
        PluginMock plugin = MockBukkit.createMockPlugin();
        File stateFile = new File(tempDir.toFile(), "state.yml");
        TagService service = new TagService(plugin, stateFile, new HeadService(plugin),
                new PrefixService(new NoopNamesApi()), new ScoreboardService(new NoopNamesApi()),
                new LeaderboardEntityService(plugin, config.leaderboard, () -> null), config);

        TagCommands.handleLeaderboardPlace(server.getConsoleSender(), service);

        assertNull(service.state().leaderboardLocation, "console has no location: placing must never set one");
    }

    private static String plainText(net.kyori.adventure.text.Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }
}
