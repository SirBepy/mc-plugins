package dev.sirbepy.mc.tweaks.listener;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bedrock players can't exist in the End below y 0 (.SuperspiderSam's join-kick loop, 2026-10-10). */
class EndFloorListenerTest {

    private static final int X = 1324;
    private static final int Z = 1549;

    private ServerMock server;
    private WorldMock overworld;
    private WorldMock end;
    private final Set<UUID> bedrock = new HashSet<>();
    private EndFloorListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        overworld = server.addSimpleWorld("world");
        overworld.setSpawnLocation(100, 64, -200);
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        clearColumn();
        Plugin plugin = MockBukkit.createMockPlugin("Tweaks");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
        listener = new EndFloorListener(plugin, bedrock::contains, new Messages(config.getConfigurationSection("messages")));
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void clearColumn() {
        for (int y = end.getMinHeight(); y < end.getMaxHeight(); y++) {
            end.getBlockAt(X, y, Z).setType(Material.AIR);
        }
    }

    private PlayerMock player(boolean isBedrock, World world, double y) {
        PlayerMock player = server.addPlayer();
        if (isBedrock) {
            bedrock.add(player.getUniqueId());
        }
        player.teleport(new Location(world, X + 0.5, y, Z + 0.9));
        return player;
    }

    private void join(PlayerMock player) {
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, net.kyori.adventure.text.Component.empty()));
        server.getScheduler().performOneTick();
    }

    @Test
    void aBedrockPlayerJoiningUnderTheEndFloorStandsOnTheHighestSolidBlock() {
        end.getBlockAt(X, 40, Z).setType(Material.END_STONE);
        PlayerMock player = player(true, end, -1.1);

        join(player);

        Location at = player.getLocation();
        assertSame(end, at.getWorld());
        assertEquals(41.0, at.getY());
        assertEquals(X + 0.5, at.getX());
        assertEquals(Z + 0.5, at.getZ());
        assertTrue(player.nextMessage() != null, "the player is told why they moved");
    }

    @Test
    void withNothingSolidInTheColumnTheyGoToOverworldSpawn() {
        PlayerMock player = player(true, end, -1.1);

        join(player);

        assertEquals(overworld.getSpawnLocation(), player.getLocation());
    }

    @Test
    void aJavaPlayerInTheSameSpotIsLeftAlone() {
        end.getBlockAt(X, 40, Z).setType(Material.END_STONE);
        PlayerMock player = player(false, end, -1.1);

        join(player);

        assertSame(end, player.getWorld());
        assertEquals(-1.1, player.getLocation().getY(), 1e-9);
    }

    @Test
    void aBedrockPlayerAboveTheFloorIsLeftAlone() {
        PlayerMock player = player(true, end, 0.0);

        join(player);

        assertEquals(0.0, player.getLocation().getY(), 1e-9);
    }

    @Test
    void belowZeroInTheOverworldIsFineForBedrock() {
        PlayerMock player = player(true, overworld, -30);

        join(player);

        assertSame(overworld, player.getWorld());
        assertEquals(-30.0, player.getLocation().getY(), 1e-9);
    }

    @Test
    void fallingThroughTheFloorRedirectsTheMove() {
        end.getBlockAt(X, 40, Z).setType(Material.END_STONE);
        PlayerMock player = player(true, end, 0.5);
        Location from = player.getLocation();
        Location to = from.clone().subtract(0, 1, 0);
        PlayerMoveEvent move = new PlayerMoveEvent(player, from, to);

        listener.onMove(move);

        assertEquals(41.0, move.getTo().getY());
    }

    @Test
    void movingAboutUnderTheFloorAsJavaIsUntouched() {
        PlayerMock player = player(false, end, 0.5);
        Location from = player.getLocation();
        Location to = from.clone().subtract(0, 1, 0);
        PlayerMoveEvent move = new PlayerMoveEvent(player, from, to);

        listener.onMove(move);

        assertEquals(to, move.getTo());
        assertFalse(listener.rescue(player));
    }
}
