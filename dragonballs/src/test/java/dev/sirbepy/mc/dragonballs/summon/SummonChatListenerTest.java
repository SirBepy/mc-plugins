package dev.sirbepy.mc.dragonballs.summon;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.effects.DragonType;
import dev.sirbepy.mc.dragonballs.effects.SummonEffects;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.file.Path;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat capture: cancelled and rerouted for the active session's summoner only; untouched for
 * every other player, session or not. */
class SummonChatListenerTest {

    private ServerMock server;
    private PluginMock plugin;
    private SummonSessionManager sessions;
    private SummonChatListener listener;
    private PlayerMock summoner;
    private PlayerMock bystander;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        WorldMock world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        BallItems items = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        dev.sirbepy.mc.dragonballs.registry.BallScatterer noopScatterer = new dev.sirbepy.mc.dragonballs.registry.BallScatterer() {
            @Override
            public java.util.Map<Integer, Location> scatterAll(Location center, boolean stone, BallRegistry r) {
                return java.util.Map.of();
            }

            @Override
            public void scatterOne(int star, Location center, boolean stone, BallRegistry r) {
            }
        };
        BallRegistry registry = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items,
                BallSet.OVERWORLD);
        registry.setScatterer(noopScatterer);
        BallRegistry netherRegistry = new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), items,
                BallSet.NETHER);
        netherRegistry.setScatterer(noopScatterer);
        BallRegistry endRegistry = new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), items,
                BallSet.END);
        endRegistry.setScatterer(noopScatterer);
        BallSets sets = new BallSets(registry, netherRegistry, endRegistry, items, () -> new DragonBallsConfig(new YamlConfiguration()));
        for (int star = 1; star <= 7; star++) {
            registry.markPlaced(star, new Location(world, 100 + (star % 2), 64, 100 + (star % 2)), false);
        }
        sessions = new SummonSessionManager(plugin.getLogger(), sets, () -> new DragonBallsConfig(new YamlConfiguration()),
                () -> new SummonConfig(2, 600, 180), SummonEffects.NONE,
                new dev.sirbepy.mc.dragonballs.summon.curse.CurseStore(new File(tempDir.toFile(), "curses.yml"), plugin.getLogger()),
                new File(tempDir.toFile(), "wishes.jsonl"), message -> {}, System::currentTimeMillis, new Random(1));
        summoner = server.addPlayer("Goku");
        bystander = server.addPlayer("Krillin");
        sessions.tryStart(summoner, DragonType.SHENRON, new Location(world, 100, 64, 100), BallSet.OVERWORLD);

        listener = new SummonChatListener(plugin, sessions);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private AsyncChatEvent chatEvent(PlayerMock player, String message) {
        return new AsyncChatEvent(false, player, Set.of(), ChatRenderer.defaultRenderer(),
                Component.text(message), Component.text(message), null);
    }

    @Test
    void summonersChatIsCancelledAndCapturedAsAWish() {
        AsyncChatEvent event = chatEvent(summoner, "I wish for a diamond block");

        listener.onChat(event);
        server.getScheduler().performTicks(1);

        assertTrue(event.isCancelled(), "the summoner's normal chat must be cancelled");
        assertTrue(sessions.hasActiveSession(), "a chat message alone never ends the session");
    }

    @Test
    void anotherPlayersChatDuringTheSessionIsUntouched() {
        AsyncChatEvent event = chatEvent(bystander, "hello everyone");

        listener.onChat(event);

        assertFalse(event.isCancelled(), "only the summoner's chat is captured, never anyone else's");
    }
}
