package dev.sirbepy.mc.minibosses.chronicle;

import dev.sirbepy.mc.guild.events.GuardianDefeatedEvent;
import dev.sirbepy.mc.minibosses.progress.GuardianKills;
import dev.sirbepy.mc.minibosses.registry.Tier;
import dev.sirbepy.mc.minibosses.sentinel.SentinelFight;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The player-facing half of the Chronicles: spoken lines and fragments on a kill. */
class ChronicleListenerTest {

    private static final Logger LOG = Logger.getLogger("chronicle-listener-test");

    private ServerMock server;
    private PlayerMock alice;
    private GuardianKills kills;
    private Chronicle chronicle;
    private ChronicleListener listener;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        server = MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        String yaml;
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/chronicles-test.yml"))) {
            yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        kills = new GuardianKills(tempDir.toFile(), LOG);
        chronicle = new Chronicle(ChronicleText.parse(yaml), new ChronicleProgress(tempDir.toFile(), LOG), kills, new Random(0));
        ChroniclerNpc npc = new ChroniclerNpc(plugin, tempDir.toFile(), LOG, id -> false);
        npc.setLook("Scribe", "Test Chronicler", net.kyori.adventure.text.format.TextColor.color(0x112233));
        listener = new ChronicleListener(plugin, chronicle, npc);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private String next() {
        var message = alice.nextComponentMessage();
        return message == null ? null : PlainTextComponentSerializer.plainText().serialize(message);
    }

    @Test
    void talkingSpeaksEachLineInTurnAndIgnoresClicksMidSpeech() {
        listener.talk(alice);
        listener.talk(alice);
        server.getScheduler().performTicks(ChronicleListener.LINE_GAP_TICKS * 2);

        assertEquals("Scribe: prologue 1", next());
        assertEquals("Scribe: prologue 2", next());
        assertNull(next(), "a click while she is still speaking must not start a second conversation");
        assertEquals(1, chronicle.stage(alice.getUniqueId()));
    }

    @Test
    void aGuardianKillHandsEachStartedFighterOneFragment() {
        chronicle.talk(alice.getUniqueId());
        GuardianDefeatedEvent event = new GuardianDefeatedEvent(SentinelFight.GUARDIAN_ID, Tier.NORMAL.name(),
                Set.of(alice.getUniqueId()));

        server.getPluginManager().callEvent(event);
        server.getPluginManager().callEvent(event);

        long fragments = Arrays.stream(alice.getInventory().getContents())
                .filter(item -> item != null && item.getType() == Material.PAPER).mapToInt(ItemStack::getAmount).sum();
        assertEquals(1, fragments, "a repeat kill must not drop a second copy");
    }

    @Test
    void aFighterWhoNeverMetTheChroniclerGetsNoFragment() {
        server.getPluginManager().callEvent(new GuardianDefeatedEvent(SentinelFight.GUARDIAN_ID, Tier.NORMAL.name(),
                Set.of(alice.getUniqueId())));

        assertTrue(Arrays.stream(alice.getInventory().getContents()).allMatch(item -> item == null));
    }
}
