package dev.sirbepy.mc.dragonballs.effects;

import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Client-side-only night + storm: applied to whoever is already in the summon's world, applied
 * to anyone who later joins or wanders into it, and restored for everyone (including someone who
 * leaves mid-session) without ever touching the world's own real time/weather.
 */
class SkyEffectTest {

    private ServerMock server;
    private WorldMock world;
    private WorldMock otherWorld;
    private PluginMock plugin;
    private SkyEffect sky;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        otherWorld = server.addSimpleWorld("other");
        plugin = MockBukkit.createMockPlugin();
        sky = new SkyEffect(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void beginAppliesNightAndStormToEveryoneAlreadyInTheWorld() {
        PlayerMock player = server.addPlayer();
        player.setLocation(new Location(world, 0, 70, 0));

        sky.begin(world);

        assertEquals(18000L, player.getPlayerTime());
        assertEquals(WeatherType.DOWNFALL, player.getPlayerWeather());
    }

    @Test
    void endRestoresEveryoneInTheActiveWorld() {
        PlayerMock player = server.addPlayer();
        player.setLocation(new Location(world, 0, 70, 0));
        sky.begin(world);

        sky.end();

        assertNotEquals(WeatherType.DOWNFALL, player.getPlayerWeather());
    }

    @Test
    void aPlayerJoiningMidSessionGetsTheSameTreatment() {
        sky.begin(world);
        PlayerMock lateJoiner = server.addPlayer();
        lateJoiner.setLocation(new Location(world, 0, 70, 0));

        sky.onJoin(new PlayerJoinEvent(lateJoiner, (String) null));

        assertEquals(18000L, lateJoiner.getPlayerTime());
        assertEquals(WeatherType.DOWNFALL, lateJoiner.getPlayerWeather());
    }

    @Test
    void aPlayerWanderingIntoTheActiveWorldGetsTheEffect() {
        sky.begin(world);
        PlayerMock player = server.addPlayer();
        player.setLocation(new Location(world, 0, 70, 0));

        sky.onWorldChange(new PlayerChangedWorldEvent(player, otherWorld));

        assertEquals(WeatherType.DOWNFALL, player.getPlayerWeather());
    }

    @Test
    void aPlayerLeavingTheActiveWorldIsRestored() {
        sky.begin(world);
        PlayerMock player = server.addPlayer();
        player.setLocation(new Location(otherWorld, 0, 70, 0));

        sky.onWorldChange(new PlayerChangedWorldEvent(player, world));

        assertNotEquals(WeatherType.DOWNFALL, player.getPlayerWeather());
    }
}
