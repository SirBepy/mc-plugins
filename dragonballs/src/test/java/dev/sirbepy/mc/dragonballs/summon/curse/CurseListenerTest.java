package dev.sirbepy.mc.dragonballs.summon.curse;

import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffectType;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A persisted effect curse is applied the moment the cursed player (re)joins, with a message; an
 * expired one is silently dropped instead. */
class CurseListenerTest {

    private ServerMock server;
    private PluginMock plugin;
    private CurseStore curses;
    private PlayerMock player;
    private long[] clockBox;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        curses = new CurseStore(new File(tempDir.toFile(), "curses.yml"), plugin.getLogger());
        player = server.addPlayer("Vegeta");
        clockBox = new long[]{1_000_000L};
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private CurseListener listener() {
        return new CurseListener(curses, () -> clockBox[0]);
    }

    @Test
    void anActiveEffectCurseIsAppliedOnJoinWithTheRemainingDuration() {
        curses.addEffect(player.getUniqueId(), "minecraft:slowness", 1, clockBox[0] + 60_000L);
        CurseListener listener = listener();

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertTrue(player.hasPotionEffect(PotionEffectType.SLOWNESS), "the curse must be applied on join");
    }

    @Test
    void anExpiredEffectCurseIsDroppedRatherThanApplied() {
        curses.addEffect(player.getUniqueId(), "minecraft:slowness", 1, clockBox[0] - 1L);
        CurseListener listener = listener();

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertFalse(player.hasPotionEffect(PotionEffectType.SLOWNESS), "an expired curse must never be applied");
        assertTrue(curses.activeEffects(player.getUniqueId(), clockBox[0]).isEmpty(), "it must also be dropped from storage");
    }

    @Test
    void resolveTypeAcceptsBothBareAndNamespacedNames() {
        assertTrue(CurseListener.resolveType("slowness") == PotionEffectType.SLOWNESS);
        assertTrue(CurseListener.resolveType("minecraft:slowness") == PotionEffectType.SLOWNESS);
    }
}
