package dev.sirbepy.mc.tag;

import dev.sirbepy.mc.tag.afk.AfkTracker;
import dev.sirbepy.mc.tag.board.LeaderboardEntityService;
import dev.sirbepy.mc.tag.board.ScoreboardService;
import dev.sirbepy.mc.tag.config.TagConfig;
import dev.sirbepy.mc.tag.head.HeadService;
import dev.sirbepy.mc.tag.log.LogService;
import dev.sirbepy.mc.tag.prefix.PrefixService;
import org.bukkit.Material;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A tagged IT player who is an active Ender Dragon fighter must keep/regain their real
 * helmet, without an admin running {@code tag clear} and without disturbing anyone else's IT
 * status; a tagged IT player who is NOT fighting the dragon must still get the sheep head forced
 * as before. Exercises the real wiring through {@code TagService} ({@code adminSet} for becoming
 * IT, {@code ensureHeadWorn} for the watchdog task's own entry point), with a fake "is fighting"
 * set standing in for {@code DragonFightCheck} so the test doesn't need a real EnderDragon.
 */
class TagServiceDragonFightTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private PluginMock plugin;
    private HeadService heads;
    private PlayerMock it;
    private PlayerMock other;
    private Set<UUID> fighters;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        heads = new HeadService(plugin);
        it = server.addPlayer();
        other = server.addPlayer();
        fighters = new HashSet<>();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TagService newService() {
        YamlConfiguration root = new YamlConfiguration();
        TagConfig config = TagConfig.load(root, new LogService(null));
        File stateFile = new File(tempDir.toFile(), "state.yml");
        LeaderboardEntityService leaderboard = new LeaderboardEntityService(plugin, config.leaderboard, () -> null);
        return new TagService(plugin, stateFile, heads, new PrefixService(new NoopNamesApi()),
                new ScoreboardService(new NoopNamesApi()), leaderboard, config,
                new AfkTracker(System::currentTimeMillis, config.afk.thresholdSeconds * 1000L),
                fighters::contains);
    }

    @Test
    void itPlayerFightingTheDragonKeepsRealHelmetAcrossBecomingItAndWatchdogTicks() {
        it.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        fighters.add(it.getUniqueId());

        TagService service = newService();
        service.adminSet(server.getConsoleSender(), it);

        assertEquals(Material.IRON_HELMET, it.getInventory().getHelmet().getType(),
                "becoming IT while actively fighting the dragon must not take the real helmet");

        // The watchdog task's own entry point, re-checked on every tick while the fight continues.
        service.ensureHeadWorn();

        assertEquals(Material.IRON_HELMET, it.getInventory().getHelmet().getType(),
                "the watchdog must keep honouring the exemption for as long as the fight continues");
    }

    @Test
    void nonFightingItPlayerStillGetsTheSheepHeadForced() {
        it.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        // it.getUniqueId() is deliberately NOT added to `fighters`.

        TagService service = newService();
        service.adminSet(server.getConsoleSender(), it);

        assertEquals(Material.PLAYER_HEAD, it.getInventory().getHelmet().getType(),
                "an IT player who is not fighting the dragon must still get the sheep head forced, as before");
    }

    @Test
    void endingTheFightResumesTheForcedHeadOnTheNextWatchdogTick() {
        it.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        fighters.add(it.getUniqueId());

        TagService service = newService();
        service.adminSet(server.getConsoleSender(), it);
        assertEquals(Material.IRON_HELMET, it.getInventory().getHelmet().getType(), "sanity: exempt while fighting");

        fighters.remove(it.getUniqueId());
        service.ensureHeadWorn();

        assertEquals(Material.PLAYER_HEAD, it.getInventory().getHelmet().getType(),
                "once the fight ends, the sheep head must resume being forced");
    }

    @Test
    void oneFightersExemptionDoesNotDisturbAnotherPlayersItStatus() {
        it.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        fighters.add(it.getUniqueId());

        TagService service = newService();
        service.adminSet(server.getConsoleSender(), it);

        assertEquals(it.getUniqueId(), service.state().currentIt, "the fighter is still IT, only exempt from the head");
        assertNotEquals(other.getUniqueId(), service.state().currentIt, "nobody else's IT status is touched by the exemption");
    }
}
