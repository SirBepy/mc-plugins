package dev.sirbepy.mc.dragonballs.summon.curse;

import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A persisted effect curse is applied the moment the cursed player (re)joins, with a message; an
 * expired one is dropped instead, and its lingering vanilla effect (which only ticks down while the
 * player is online) is stripped on join, in the sweep and on lift, leaving legitimate effects alone. */
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
    void anExpiredCursesLingeringEffectIsStrippedOnJoin() {
        curses.addEffect(player.getUniqueId(), "minecraft:unluck", 1, clockBox[0] + 4 * 3_600_000L);
        CurseListener listener = listener();
        listener.applyNow(player);
        clockBox[0] += 17 * 3_600_000L; // offline 17 real hours: the vanilla effect never ticked down

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertFalse(player.hasPotionEffect(PotionEffectType.UNLUCK), "the expired curse's effect must be stripped");
    }

    @Test
    void aRejoinCutsTheCurseEffectToTheRealTimeLeft() {
        curses.addEffect(player.getUniqueId(), "minecraft:unluck", 1, clockBox[0] + 60_000L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.UNLUCK, 100_000, 1, true, true));

        listener().onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertEquals(1200, player.getPotionEffect(PotionEffectType.UNLUCK).getDuration(),
                "the re-applied curse must last the 60 real seconds left, not the stale longer effect");
    }

    @Test
    void theSweepStripsACurseThatExpiredWhileOnline() {
        curses.addEffect(player.getUniqueId(), "minecraft:slowness", 0, clockBox[0] + 60_000L);
        CurseListener listener = listener();
        listener.applyNow(player);
        clockBox[0] += 61_000L;

        listener.sweep(List.of(player));

        assertFalse(player.hasPotionEffect(PotionEffectType.SLOWNESS), "the sweep must end an expired curse's effect");
        assertTrue(curses.takeExpired(player.getUniqueId(), clockBox[0]).isEmpty(), "and drop the stored entry");
    }

    @Test
    void strippingAnExpiredCurseLeavesALegitimateEffectOfTheSameType() {
        curses.addEffect(player.getUniqueId(), "minecraft:unluck", 1, clockBox[0] - 1L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.UNLUCK, 6000, 1, false, true));

        listener().onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertTrue(player.hasPotionEffect(PotionEffectType.UNLUCK), "a non-ambient (potion or /effect) Bad Luck is not the curse's");
    }

    @Test
    void liftStripsTheEffectOfACurseThatAlreadyExpired() {
        curses.addEffect(player.getUniqueId(), "minecraft:unluck", 1, clockBox[0] + 4 * 3_600_000L);
        CurseListener listener = listener();
        listener.applyNow(player);
        clockBox[0] += 17 * 3_600_000L;

        listener.lift(player.getUniqueId(), player);

        assertFalse(player.hasPotionEffect(PotionEffectType.UNLUCK), "lift must remove an expired curse's lingering effect");
    }

    @Test
    void liftLeavesABeaconEffectOfAnotherAmplifier() {
        curses.addEffect(player.getUniqueId(), "minecraft:haste", 1, clockBox[0] + 60_000L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 300, 0, true, true));

        listener().lift(player.getUniqueId(), player);

        assertTrue(player.hasPotionEffect(PotionEffectType.HASTE), "only the curse's exact amplifier is curse-marked");
    }

    @Test
    void resolveTypeAcceptsBothBareAndNamespacedNames() {
        assertTrue(CurseListener.resolveType("slowness") == PotionEffectType.SLOWNESS);
        assertTrue(CurseListener.resolveType("minecraft:slowness") == PotionEffectType.SLOWNESS);
    }
}
