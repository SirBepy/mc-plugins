package dev.sirbepy.mc.tag;

import dev.sirbepy.mc.names.api.NamesApi;
import dev.sirbepy.mc.tag.board.ScoreboardService;
import dev.sirbepy.mc.tag.config.TagConfig;
import dev.sirbepy.mc.tag.head.HeadService;
import dev.sirbepy.mc.tag.log.LogService;
import dev.sirbepy.mc.tag.prefix.PrefixService;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Reproduces the memory-leak-shaped correctness bug directly: {@code tagbackMessageCooldown} used to
 * survive a quit forever, so a player who quit still suppressed their own tagback-blocked warning on
 * their very first attempt after a later rejoin. {@link TagService#onQuit} forgetting the entry is
 * what a fresh warning after quit actually depends on, so this is the red/green proof for that fix.
 */
class TagServiceQuitTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private TagService service;
    private PlayerMock it;
    private PlayerMock victim;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        it = server.addPlayer();
        victim = server.addPlayer();

        YamlConfiguration root = new YamlConfiguration();
        root.set("logs.tagback-blocked.enabled", true);
        root.set("logs.tagback-blocked.console", false);
        root.set("logs.tagback-blocked.broadcast", false);
        root.set("logs.tagback-blocked.format", "<gray>blocked</gray>");

        TagConfig config = TagConfig.load(root, new LogService(null));
        File stateFile = new File(tempDir.toFile(), "state.yml");
        service = new TagService(plugin, stateFile, new HeadService(plugin), new PrefixService(new NoopNamesApi()),
                new ScoreboardService(new NoopNamesApi()), config);

        // "it" just became IT by tagging "victim", so "victim" is tagback-protected and attempting
        // to tag them back is what drives maybeWarnTagback() / the cooldown map under test.
        service.state().currentIt = it.getUniqueId();
        service.state().lastTagger = victim.getUniqueId();
        service.state().tagbackExpiryMillis = System.currentTimeMillis() + 60_000L;
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onQuitForgetsTheCooldownSoTheNextBlockedAttemptWarnsAgain() {
        service.attemptTag(it, victim);
        assertNotNull(it.nextComponentMessage(), "the first blocked attempt must warn the tagger");

        service.attemptTag(it, victim);
        assertNull(it.nextComponentMessage(), "a repeat within the debounce window must stay silent");

        service.onQuit(it);

        service.attemptTag(it, victim);
        assertNotNull(it.nextComponentMessage(),
                "onQuit must forget the cooldown entry, or this warning would stay suppressed forever");
    }

    /** Bare no-op stand-in: this test only exercises TagService's own cooldown bookkeeping. */
    private static final class NoopNamesApi implements NamesApi {
        @Override
        public void setPrefix(UUID player, String key, Component prefix, int priority) {
        }

        @Override
        public void removePrefix(UUID player, String key) {
        }

        @Override
        public Component nameComponent(Player player) {
            return Component.text(player.getName());
        }

        @Override
        public Component plainName(Player player) {
            return Component.text(player.getName());
        }

        @Override
        public void registerScoreboard(Scoreboard scoreboard) {
        }

        @Override
        public void unregisterScoreboard(Scoreboard scoreboard) {
        }

        @Override
        public void refresh(Player player) {
        }
    }
}
