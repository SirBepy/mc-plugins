package dev.sirbepy.mc.endfight.arena;

import org.bukkit.World;
import org.bukkit.event.world.WorldLoadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndWorldLoadListenerTest {

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
    void anEndWorldLoadingFiresTheCallback() {
        WorldMock end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        List<World> seen = new ArrayList<>();
        EndWorldLoadListener listener = new EndWorldLoadListener(seen::add);

        listener.onWorldLoad(new WorldLoadEvent(end));

        assertEquals(1, seen.size());
        assertTrue(seen.contains(end));
    }

    @Test
    void anOverworldLoadingIsIgnored() {
        WorldMock overworld = server.addSimpleWorld("world");
        List<World> seen = new ArrayList<>();
        EndWorldLoadListener listener = new EndWorldLoadListener(seen::add);

        listener.onWorldLoad(new WorldLoadEvent(overworld));

        assertTrue(seen.isEmpty());
    }
}
