package dev.sirbepy.mc.tag;

import dev.sirbepy.mc.names.api.NamesApi;
import dev.sirbepy.mc.tag.afk.AfkTracker;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers todo 01 (Tag AFK handling): excluding AFK players from the points multiplier's n,
 * warning IT once per idle stretch, and auto-passing IT to a random eligible player, including
 * the two cases where auto-pass must stay a no-op (IT offline, nobody eligible) and the one
 * where the receiver must get none of the usual tag rewards.
 */
class TagServiceAfkTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private AtomicLong now;
    private AfkTracker afkTracker;
    private TagService service;
    private PlayerMock it;
    private PlayerMock other;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        it = server.addPlayer();
        other = server.addPlayer();

        now = new AtomicLong(0L);
        afkTracker = new AfkTracker(now::get, 1000L);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TagService newService(YamlConfiguration root) {
        TagConfig config = TagConfig.load(root, new LogService(null));
        File stateFile = new File(tempDir.toFile(), "state.yml");
        PluginMock plugin = MockBukkit.createMockPlugin();
        return new TagService(plugin, stateFile, new HeadService(plugin), new PrefixService(new NoopNamesApi()),
                new ScoreboardService(new NoopNamesApi()), config, afkTracker);
    }

    private YamlConfiguration baseRoot() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("afk.enabled", true);
        root.set("afk.exclude-from-multiplier", true);
        root.set("afk.auto-pass", true);
        root.set("afk.it-pass-after-seconds", 1); // threshold here is driven by the injected AfkTracker, not seconds
        root.set("afk.it-pass-warning-seconds", 0);
        root.set("messages.afk-warning", "<yellow>idle <seconds></yellow>");
        root.set("logs.it-afk-passed.enabled", true);
        root.set("logs.it-afk-passed.console", false);
        root.set("logs.it-afk-passed.broadcast", true);
        root.set("logs.it-afk-passed.format", "<gray><old_it> -> <new_it></gray>");
        return root;
    }

    @Test
    void pointsTickNExcludesAfkPlayersWhenExclusionIsOn() {
        YamlConfiguration root = baseRoot();
        service = newService(root);
        service.state().currentIt = it.getUniqueId();
        afkTracker.markActive(it.getUniqueId());
        // "other" never marked active: idleMillis is 0 from a never-seen player, which is below
        // the threshold, so advance the clock past it to make them genuinely AFK.
        afkTracker.markActive(other.getUniqueId());
        now.addAndGet(1000L);

        double before = service.state().statsFor(it.getUniqueId()).points;
        service.pointsTick();
        double gainedWithAfkExcluded = service.state().statsFor(it.getUniqueId()).points - before;

        // n = 0 (the only other player is AFK and excluded) => multiplier 0 => zero gain.
        assertEquals(0.0, gainedWithAfkExcluded, 1e-9);
    }

    @Test
    void pointsTickNIncludesAfkPlayersWhenAfkIsDisabled() {
        YamlConfiguration root = baseRoot();
        root.set("afk.enabled", false);
        service = newService(root);
        service.state().currentIt = it.getUniqueId();
        afkTracker.markActive(other.getUniqueId());
        now.addAndGet(1000L); // "other" would be AFK if afk.enabled were true

        double before = service.state().statsFor(it.getUniqueId()).points;
        service.pointsTick();
        double gained = service.state().statsFor(it.getUniqueId()).points - before;

        assertNotEquals(0.0, gained, "with afk.enabled=false, n must count every online non-IT player");
    }

    @Test
    void warningFiresOncePerIdleStretchThenStopsUntilActiveAgain() {
        YamlConfiguration root = baseRoot();
        root.set("afk.it-pass-after-seconds", 10);
        root.set("afk.it-pass-warning-seconds", 3);
        service = newService(root);
        service.state().currentIt = it.getUniqueId();
        afkTracker.markActive(it.getUniqueId());
        afkTracker.markActive(other.getUniqueId());

        now.addAndGet(7_000L); // 10 - 3 = warn at 7s idle
        service.checkItAfk();
        assertNotNull(it.nextComponentMessage(), "must warn once the warning window is reached");

        service.checkItAfk();
        assertNull(it.nextComponentMessage(), "must not warn again on every tick within the same idle stretch");

        afkTracker.markActive(it.getUniqueId());
        now.addAndGet(7_000L);
        service.checkItAfk();
        assertNotNull(it.nextComponentMessage(), "becoming active must reset the warned flag for the next stretch");
    }

    @Test
    void autoPassDoesNotHappenWhenItIsOffline() {
        YamlConfiguration root = baseRoot();
        service = newService(root);
        UUID offlineIt = UUID.randomUUID();
        service.state().currentIt = offlineIt;
        afkTracker.markActive(offlineIt);
        now.addAndGet(5_000L);

        service.checkItAfk();

        assertEquals(offlineIt, service.state().currentIt, "offline IT must never be auto-passed");
    }

    @Test
    void autoPassDoesNothingWhenNobodyIsEligible() {
        YamlConfiguration root = baseRoot();
        service = newService(root);
        service.state().currentIt = it.getUniqueId();
        afkTracker.markActive(it.getUniqueId());
        afkTracker.markActive(other.getUniqueId());
        now.addAndGet(1_000L); // both idle past the threshold: "other" is AFK too, so not eligible

        service.checkItAfk();

        assertEquals(it.getUniqueId(), service.state().currentIt, "with nobody eligible, IT must not change");
    }

    @Test
    void autoPassMovesItAndGivesTheReceiverNoPointsAndNoTagbackCooldown() {
        YamlConfiguration root = baseRoot();
        service = newService(root);
        service.state().currentIt = it.getUniqueId();
        service.state().lastTagger = null;
        afkTracker.markActive(it.getUniqueId());
        // "other" stays active (not marked AFK), so they are the sole eligible receiver.
        afkTracker.markActive(other.getUniqueId());
        now.addAndGet(1_000L);
        // Re-mark "other" active after the clock jump so only IT is idle enough to be past threshold.
        afkTracker.markActive(other.getUniqueId());

        double otherPointsBefore = service.state().statsFor(other.getUniqueId()).points;
        service.checkItAfk();

        assertEquals(other.getUniqueId(), service.state().currentIt, "IT must pass to the sole eligible player");
        assertEquals(otherPointsBefore, service.state().statsFor(other.getUniqueId()).points, 1e-9,
                "auto-pass must not award the usual +10 for becoming IT");
        assertEquals(0L, service.state().tagbackExpiryMillis, "auto-pass must not apply a tagback cooldown");
        assertNull(service.state().lastTagger, "auto-pass must not set a tagback-protected lastTagger");
    }

    /** Bare no-op stand-in: these tests only exercise TagService's own AFK bookkeeping. */
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
