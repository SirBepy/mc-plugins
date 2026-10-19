package dev.sirbepy.mc.market;

import dev.sirbepy.mc.discordbot.api.DiscordBotApi;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.File;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Discord deal alert posted right after a rotate for anyone {@code /market watch}-ing an item
 * that landed as a great deal: pings every watcher with a linked Discord id for real ({@code <@id>}),
 * skips a watcher with no link entirely, and posts nothing at all when nobody watching has one.
 */
class DealAnnouncerTest {

    private static final String STALLS = """
            sign-block: spruce_wall_sign
            stalls:
              lumberjack:
                name: Lumberjack
                color: brown
                slots:
                  - {x: 0, y: 70, z: 0, facing: south}
                buys:
                  - {item: birch_log, amount: 64, price: 28.8}
            """;

    // threshold: 0 makes every roll count as a "great deal", so the test doesn't depend on the
    // random price Market.refresh() happens to pick.
    private static final String CONFIG = """
            price-spread: 0.5
            deal-alerts:
              enabled: true
              threshold: 0
            """;

    @TempDir
    File dir;

    private ServerMock server;
    private MarketLayout layout;
    private MobHeads heads;
    private MarketConfig config;
    private Market market;
    private MarketWatches watches;
    private MarketDiscordLinks discordLinks;
    private DealAnnouncer announcer;
    private FakeDiscordBotApi discord;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        layout = MarketLayout.from(YamlConfiguration.loadConfiguration(new StringReader(STALLS)));
        heads = new MobHeads(YamlConfiguration.loadConfiguration(new StringReader("mobs: {}")));
        var jarConfig = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"));
        YamlConfiguration jarDefaults = YamlConfiguration.loadConfiguration(new InputStreamReader(jarConfig, StandardCharsets.UTF_8));
        YamlConfiguration testConfig = YamlConfiguration.loadConfiguration(new StringReader(CONFIG));
        for (String key : jarDefaults.getKeys(true)) {
            if (!testConfig.contains(key)) {
                testConfig.set(key, jarDefaults.get(key));
            }
        }
        config = new MarketConfig(testConfig);
        market = new Market(layout, heads, () -> config, () -> "day-1", new Random(7), new File(dir, "state.yml"),
                Logger.getLogger("deal-announcer-test"));
        market.refresh();
        watches = new MarketWatches(market, layout, heads, () -> config, new File(dir, "watches.yml"),
                Logger.getLogger("deal-announcer-test"));
        discordLinks = new MarketDiscordLinks(new File(dir, "discord-links.yml"), Logger.getLogger("deal-announcer-test"));
        announcer = new DealAnnouncer(market, watches, discordLinks, () -> config, server);
        discord = new FakeDiscordBotApi();
        server.getServicesManager().register(DiscordBotApi.class, discord, plugin, ServicePriority.Normal);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pingsALinkedWatcherForReal() {
        UUID watcherId = UUID.randomUUID();
        discordLinks.link(watcherId, "123456789012345678");
        watches.add(watcherId, "birch_log");

        announcer.announce();

        assertEquals(1, discord.messages.size());
        assertTrue(discord.messages.get(0).contains("<@123456789012345678>"), discord.messages.get(0));
    }

    @Test
    void skipsAWatcherWithNoLinkedDiscordId() {
        UUID linked = UUID.randomUUID();
        UUID unlinked = UUID.randomUUID();
        discordLinks.link(linked, "123456789012345678");
        watches.add(linked, "birch_log");
        watches.add(unlinked, "birch_log");

        announcer.announce();

        assertEquals(1, discord.messages.size());
        String message = discord.messages.get(0);
        assertTrue(message.contains("<@123456789012345678>"), message);
        assertEquals(1, message.split("<@").length - 1, "only the linked watcher is mentioned");
    }

    @Test
    void postsNothingWhenNoWatcherHasALinkedDiscordId() {
        watches.add(UUID.randomUUID(), "birch_log");

        announcer.announce();

        assertTrue(discord.messages.isEmpty());
    }

    private static final class FakeDiscordBotApi implements DiscordBotApi {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void sendMarketDeal(String message) {
            messages.add(message);
        }

        @Override
        public void sendPresenceUpdate(String message) {
        }
    }
}
