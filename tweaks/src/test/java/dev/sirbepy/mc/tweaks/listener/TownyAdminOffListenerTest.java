package dev.sirbepy.mc.tweaks.listener;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TownyAdminOffListenerTest {

    private static final UUID BEPY = UUID.fromString("a6f30e22-f8d5-4ae8-a69c-2f81662b1950");

    private ServerMock server;
    private final Set<UUID> listed = new HashSet<>(Set.of(BEPY));
    private final Set<UUID> modeOn = new HashSet<>();
    private final List<String> turnedOn = new ArrayList<>();

    /** Stands in for Towny's toggle: turning it on while on switches it off, like /resident toggle. */
    private final TownyAdminOffListener.BypassMode mode = new TownyAdminOffListener.BypassMode() {
        @Override
        public boolean isOn(Player player) {
            return modeOn.contains(player.getUniqueId());
        }

        @Override
        public void turnOn(Player player) {
            turnedOn.add(player.getName());
            if (!modeOn.remove(player.getUniqueId())) modeOn.add(player.getUniqueId());
        }
    };

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.getPluginManager().registerEvents(
                new TownyAdminOffListener(MockBukkit.createMockPlugin("Tweaks"), () -> listed, mode),
                MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock join(String name, UUID uuid) {
        PlayerMock player = new PlayerMock(server, name, uuid);
        server.addPlayer(player);
        return player;
    }

    @Test
    void aListedPlayerGetsTheModeASecondAfterJoining() {
        join("SirBepy", BEPY);
        server.getScheduler().performTicks(TownyAdminOffListener.DELAY_TICKS - 1);
        assertEquals(List.of(), turnedOn, "Towny's own join task must have run first");
        server.getScheduler().performTicks(1);
        assertEquals(List.of("SirBepy"), turnedOn);
        assertEquals(Set.of(BEPY), modeOn);
    }

    @Test
    void theModeIsSetAgainOnEveryJoin() {
        PlayerMock bepy = join("SirBepy", BEPY);
        server.getScheduler().performTicks(TownyAdminOffListener.DELAY_TICKS);
        bepy.disconnect();
        modeOn.clear(); // Towny clears every mode on quit.
        join("SirBepy", BEPY);
        server.getScheduler().performTicks(TownyAdminOffListener.DELAY_TICKS);
        assertEquals(List.of("SirBepy", "SirBepy"), turnedOn);
        assertEquals(Set.of(BEPY), modeOn);
    }

    @Test
    void aModeAlreadyOnIsLeftOnNotToggledOff() {
        modeOn.add(BEPY);
        join("SirBepy", BEPY);
        server.getScheduler().performTicks(TownyAdminOffListener.DELAY_TICKS);
        assertEquals(List.of(), turnedOn);
        assertEquals(Set.of(BEPY), modeOn);
    }

    @Test
    void unlistedPlayersAreLeftAlone() {
        join("ImMijo", UUID.randomUUID());
        server.getScheduler().performTicks(TownyAdminOffListener.DELAY_TICKS * 2);
        assertEquals(List.of(), turnedOn);
    }

    @Test
    void aPlayerWhoLeftBeforeTheDelayIsSkipped() {
        PlayerMock bepy = join("SirBepy", BEPY);
        bepy.disconnect();
        server.getScheduler().performTicks(TownyAdminOffListener.DELAY_TICKS);
        assertEquals(List.of(), turnedOn);
    }
}
