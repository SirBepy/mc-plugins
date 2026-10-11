package dev.sirbepy.mc.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayersTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anOnlinePlayerIsFoundByExactName() {
        PlayerMock alice = server.addPlayer("Alice");

        assertSame(alice, Players.known("Alice").orElseThrow());
        assertEquals(alice.getUniqueId(), Players.idOf("Alice"));
    }

    @Test
    void aPlayerWhoLeftIsStillKnown() {
        PlayerMock bob = server.addPlayer("Bob");
        UUID id = bob.getUniqueId();
        bob.disconnect();

        assertEquals(id, Players.known("Bob").orElseThrow().getUniqueId());
        assertEquals(id, Players.idOf("Bob"));
    }

    @Test
    void aRawUuidIsTakenAsTypedEvenIfItNeverJoined() {
        UUID stranger = UUID.fromString("3e0a4456-e2a3-4ea8-9bfa-8e38ee4c863d");

        assertEquals(stranger, Players.idOf(stranger.toString()));
    }

    @Test
    void aNameThatNeverJoinedIsUnknown() {
        assertTrue(Players.known("Nobody").isEmpty());
        assertNull(Players.idOf("Nobody"));
    }
}
