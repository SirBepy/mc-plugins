package dev.sirbepy.mc.tag;

import dev.sirbepy.mc.tag.afk.AfkTracker;
import dev.sirbepy.mc.tag.board.LeaderboardEntityService;
import dev.sirbepy.mc.tag.board.ScoreboardService;
import dev.sirbepy.mc.tag.config.TagConfig;
import dev.sirbepy.mc.tag.head.HeadService;
import dev.sirbepy.mc.tag.log.LogService;
import dev.sirbepy.mc.tag.prefix.PrefixService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tracker compass follows IT through a tag, and AFK players can't be tagged at all. */
class TagServiceTrackerTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private AtomicLong now;
    private AfkTracker afkTracker;
    private TagService service;
    private PlayerMock it;
    private PlayerMock victim;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        it = server.addPlayer();
        victim = server.addPlayer();
        now = new AtomicLong(0L);
        afkTracker = new AfkTracker(now::get, 1000L);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TagService newService(boolean trackerEnabled) {
        YamlConfiguration root = new YamlConfiguration();
        // A plain helmet keeps head granting off MockBukkit's unimplemented player-profile path.
        root.set("head.material", "IRON_HELMET");
        root.set("tracker.enabled", trackerEnabled);
        root.set("afk.enabled", true);
        TagConfig config = TagConfig.load(root, new LogService(null));
        File stateFile = new File(tempDir.toFile(), "state.yml");
        PluginMock plugin = MockBukkit.createMockPlugin();
        LeaderboardEntityService leaderboard = new LeaderboardEntityService(plugin, config.leaderboard, () -> service.state());
        return new TagService(plugin, stateFile, new HeadService(plugin), new PrefixService(new NoopNamesApi()),
                new ScoreboardService(new NoopNamesApi()), leaderboard, config, afkTracker);
    }

    private boolean carriesTracker(PlayerMock player) {
        return Arrays.stream(player.getInventory().getContents()).anyMatch(service.tracker()::isTracker);
    }

    private void makeItWithTracker() {
        service.state().currentIt = it.getUniqueId();
        service.syncTracker(it);
        afkTracker.markActive(it.getUniqueId());
        afkTracker.markActive(victim.getUniqueId());
    }

    @Test
    void theTrackerPassesFromTaggerToTheNewIt() {
        service = newService(true);
        makeItWithTracker();
        assertTrue(carriesTracker(it));

        assertEquals(TagService.TagResult.OK, service.attemptTag(it, victim));

        assertFalse(carriesTracker(it), "the old IT must lose the tracker the moment they're no longer IT");
        assertTrue(carriesTracker(victim), "the new IT must get the tracker");
    }

    @Test
    void noTrackerIsHandedOutWhenDisabled() {
        service = newService(false);
        makeItWithTracker();

        service.attemptTag(it, victim);

        assertFalse(carriesTracker(victim));
    }

    @Test
    void anAfkPlayerCannotBeTaggedAndItIsToldWhyOnce() {
        service = newService(true);
        makeItWithTracker();
        now.addAndGet(1000L); // victim (and IT) idle past the 1s threshold
        afkTracker.markActive(it.getUniqueId());

        assertEquals(TagService.TagResult.AFK, service.attemptTag(it, victim));
        assertEquals(it.getUniqueId(), service.state().currentIt, "IT must stay IT");
        assertNotNull(it.nextComponentMessage(), "IT must be told the player is AFK");

        service.attemptTag(it, victim);
        assertNull(it.nextComponentMessage(), "spam-hitting must not spam the AFK note");
    }

    @Test
    void aStrayTrackerIsDeletedWhenANonItPlayerJoins() {
        service = newService(true);
        ItemStack stray = service.tracker().build(service.config().tracker);
        victim.getInventory().addItem(stray);

        service.onJoin(victim);

        assertFalse(carriesTracker(victim));
    }
}
