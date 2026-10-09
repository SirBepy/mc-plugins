package dev.sirbepy.mc.endfight.reset;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaLockoutTest {

    private ServerMock server;
    private WorldMock end;
    private ResetConfig config;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        config = new ResetConfig(null);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock playerAt(double x, double y, double z, GameMode mode) {
        PlayerMock player = server.addPlayer();
        player.setGameMode(mode);
        player.teleport(new Location(end, x, y, z));
        return player;
    }

    @Test
    void picksSurvivalAndAdventurePlayersWithinTheRadius() {
        PlayerMock inRangeSurvival = playerAt(10, 70, 10, GameMode.SURVIVAL);
        PlayerMock inRangeAdventure = playerAt(-20, 70, 30, GameMode.ADVENTURE);

        List<org.bukkit.entity.Player> candidates = ArenaLockout.candidates(end, config);

        assertEquals(2, candidates.size());
        assertTrue(candidates.contains(inRangeSurvival));
        assertTrue(candidates.contains(inRangeAdventure));
    }

    @Test
    void neverPicksCreativeOrSpectatorPlayers() {
        playerAt(10, 70, 10, GameMode.CREATIVE);
        playerAt(10, 70, 20, GameMode.SPECTATOR);

        List<org.bukkit.entity.Player> candidates = ArenaLockout.candidates(end, config);

        assertTrue(candidates.isEmpty());
    }

    @Test
    void neverPicksAPlayerOutsideTheRadius() {
        playerAt(500, 70, 0, GameMode.SURVIVAL);

        assertTrue(ArenaLockout.candidates(end, config).isEmpty());
    }

    @Test
    void neverPicksAPlayerAlreadyStandingOnThePlatform() {
        // The platform sits at x=100, 20 blocks past the default 80-block lockout radius, so a
        // player already there is outside the radius check entirely - never a candidate.
        playerAt(config.platformX(), config.platformY(), config.platformZ(), GameMode.SURVIVAL);

        assertTrue(ArenaLockout.candidates(end, config).isEmpty());
    }

    @Test
    void ensurePlatformBuildsVanillasVerifiedShape() {
        ArenaLockout.ensurePlatform(end, config);

        int cx = (int) Math.floor(config.platformX());
        int cz = (int) Math.floor(config.platformZ());
        int standY = (int) Math.floor(config.platformY());

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                assertEquals(Material.OBSIDIAN, end.getBlockAt(cx + dx, standY - 1, cz + dz).getType(),
                        "obsidian pad at " + dx + "," + dz);
                for (int dy = 0; dy < 3; dy++) {
                    assertEquals(Material.AIR, end.getBlockAt(cx + dx, standY + dy, cz + dz).getType(),
                            "air clearance at " + dx + "," + dy + "," + dz);
                }
            }
        }
        // One block past the 5x5 pad must be untouched.
        assertFalse(Material.OBSIDIAN.equals(end.getBlockAt(cx + 3, standY - 1, cz).getType()));
    }

    @Test
    void teleportAllMovesEveryCandidateToThePlatformStandLocation() {
        PlayerMock player = playerAt(10, 70, 10, GameMode.SURVIVAL);

        ArenaLockout.teleportAll(end, config);

        Location at = player.getLocation();
        assertEquals(config.platformX(), at.getX(), 0.001);
        assertEquals(config.platformY(), at.getY(), 0.001);
        assertEquals(config.platformZ(), at.getZ(), 0.001);
    }
}
