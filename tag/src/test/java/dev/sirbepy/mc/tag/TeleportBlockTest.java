package dev.sirbepy.mc.tag;

import dev.sirbepy.mc.tag.afk.AfkTracker;
import dev.sirbepy.mc.tag.board.LeaderboardEntityService;
import dev.sirbepy.mc.tag.board.ScoreboardService;
import dev.sirbepy.mc.tag.config.TagConfig;
import dev.sirbepy.mc.tag.head.HeadService;
import dev.sirbepy.mc.tag.listener.TeleportBlockListener;
import dev.sirbepy.mc.tag.log.LogService;
import dev.sirbepy.mc.tag.prefix.PrefixService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opted-in players near IT can't command/plugin-teleport away; the tagback note says how long is left. */
class TeleportBlockTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private World world;
    private AtomicLong now;
    private AfkTracker afkTracker;
    private TagService service;
    private TeleportBlockListener listener;
    private PlayerMock it;
    private PlayerMock runner;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        it = server.addPlayer();
        runner = server.addPlayer();
        it.setLocation(new Location(world, 0, 64, 0));
        runner.setLocation(new Location(world, 60, 64, 60)); // ~85 blocks away, inside the default 100
        now = new AtomicLong(0L);
        afkTracker = new AfkTracker(now::get, 1000L);
        service = newService(new YamlConfiguration());
        service.state().currentIt = it.getUniqueId();
        afkTracker.markActive(it.getUniqueId());
        afkTracker.markActive(runner.getUniqueId());
        listener = new TeleportBlockListener(service);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TagService newService(YamlConfiguration root) {
        // A plain helmet keeps head granting off MockBukkit's unimplemented player-profile path.
        root.set("head.material", "IRON_HELMET");
        root.set("tracker.enabled", false);
        root.set("afk.enabled", true);
        TagConfig config = TagConfig.load(root, new LogService(null));
        File stateFile = new File(tempDir.toFile(), "state.yml");
        PluginMock plugin = MockBukkit.createMockPlugin();
        LeaderboardEntityService leaderboard = new LeaderboardEntityService(plugin, config.leaderboard, () -> service.state());
        return new TagService(plugin, stateFile, new HeadService(plugin), new PrefixService(new NoopNamesApi()),
                new ScoreboardService(new NoopNamesApi()), leaderboard, config, afkTracker);
    }

    private PlayerTeleportEvent teleport(PlayerMock player, TeleportCause cause) {
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, player.getLocation(),
                new Location(world, 5000, 64, 5000), cause);
        listener.onTeleport(event);
        return event;
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void aCommandTeleportNearItIsBlockedAndExplained() {
        assertTrue(teleport(runner, TeleportCause.COMMAND).isCancelled());
        Component message = runner.nextComponentMessage();
        assertNotNull(message);
        assertTrue(plain(message).contains("100 blocks of IT (" + it.getName() + ")"), plain(message));
    }

    @Test
    void aPluginTeleportNearItIsBlocked() {
        assertTrue(teleport(runner, TeleportCause.PLUGIN).isCancelled());
    }

    @Test
    void pearlsChorusAndPortalsStillWork() {
        for (TeleportCause cause : new TeleportCause[] {TeleportCause.ENDER_PEARL, TeleportCause.CONSUMABLE_EFFECT,
                TeleportCause.NETHER_PORTAL, TeleportCause.END_PORTAL}) {
            assertFalse(teleport(runner, cause).isCancelled(), cause.name());
        }
    }

    @Test
    void outsideTheRadiusIsFree() {
        runner.setLocation(new Location(world, 101, 64, 0));
        assertFalse(teleport(runner, TeleportCause.COMMAND).isCancelled());
    }

    @Test
    void theRadiusComesFromConfig() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("teleport-block.radius", 50);
        service.setConfig(TagConfig.load(root, new LogService(null)));
        assertFalse(teleport(runner, TeleportCause.COMMAND).isCancelled());
    }

    @Test
    void disabledBlocksNothing() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("teleport-block.enabled", false);
        service.setConfig(TagConfig.load(root, new LogService(null)));
        assertFalse(teleport(runner, TeleportCause.COMMAND).isCancelled());
    }

    @Test
    void optedOutPlayersAreFree() {
        service.state().optedOut.add(runner.getUniqueId());
        assertFalse(teleport(runner, TeleportCause.COMMAND).isCancelled());
    }

    @Test
    void creativeAndSpectatorPlayersAreFree() {
        runner.setGameMode(GameMode.CREATIVE);
        assertFalse(teleport(runner, TeleportCause.COMMAND).isCancelled());
        runner.setGameMode(GameMode.SPECTATOR);
        assertFalse(teleport(runner, TeleportCause.COMMAND).isCancelled());
    }

    @Test
    void itCanTeleportFreely() {
        runner.setLocation(new Location(world, 1, 64, 0));
        assertFalse(teleport(it, TeleportCause.COMMAND).isCancelled());
    }

    @Test
    void anAfkItPinsNobody() {
        now.addAndGet(1000L);
        afkTracker.markActive(runner.getUniqueId());
        assertFalse(teleport(runner, TeleportCause.COMMAND).isCancelled());
    }

    @Test
    void anItInAnotherWorldPinsNobody() {
        it.setLocation(new Location(server.addSimpleWorld("world_nether"), 0, 64, 0));
        assertFalse(teleport(runner, TeleportCause.COMMAND).isCancelled());
    }

    @Test
    void noItPinsNobody() {
        service.state().currentIt = null;
        assertFalse(teleport(runner, TeleportCause.COMMAND).isCancelled());
    }

    @Test
    void theTagbackNoteSaysHowLongIsLeft() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("tagback.seconds", 60);
        root.set("logs.tagback-blocked.console", false);
        root.set("logs.tagback-blocked.format", "<player> back in <seconds>s");
        service.setConfig(TagConfig.load(root, new LogService(null)));
        assertEquals(TagService.TagResult.OK, service.attemptTag(it, runner));

        assertEquals(TagService.TagResult.TAGBACK_BLOCKED, service.attemptTag(runner, it));

        Component message = runner.nextComponentMessage();
        assertNotNull(message);
        assertTrue(plain(message).matches(it.getName() + " back in (59|60)s"), plain(message));
    }
}
