package dev.sirbepy.mc.minibosses.sovereign;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Enderman;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused coverage for the Void Sovereign's Heroic-tier mechanic in isolation (see
 * {@code SovereignFightTest} for it firing as part of a real Heroic fight): standing inside a
 * rolled rift zone takes damage and gets displaced every check, standing outside every rift zone
 * takes nothing.
 */
class RiftZonesAbilityTest {

    private ServerMock server;
    private PluginMock plugin;
    private WorldMock world;
    private PlayerMock alice;
    private LivingEntity boss;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        boss = (Enderman) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.ENDERMAN);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void standingInARiftZoneTakesDamageAndIsDisplaced() {
        // Random(0) rolls the sole-distance-0 case repeatedly: every rift center lands exactly on
        // the boss's own position, so a player standing there is always inside one.
        alice.teleport(new Location(world, 0, 64, 0));
        double healthBefore = alice.getHealth();
        RiftZonesAbility ability = new RiftZonesAbility(plugin, new Random(0));

        ability.trigger(boss);
        server.getScheduler().performTicks((int) RiftZonesAbility.CHECK_INTERVAL_TICKS);

        assertTrue(alice.getHealth() < healthBefore, "standing in a rift zone through a check must deal damage");
        assertTrue(alice.getVelocity().lengthSquared() > 0, "a rift zone hit must also displace the player");
    }

    @Test
    void standingWellAwayFromEveryRiftZoneAvoidsTheDamage() {
        // A rift center can roll at most RIFT_SPREAD blocks from the boss; standing far outside
        // that plus the rift radius guarantees no overlap regardless of the roll.
        alice.teleport(new Location(world, 1000, 64, 1000));
        double healthBefore = alice.getHealth();
        RiftZonesAbility ability = new RiftZonesAbility(plugin, new Random());

        ability.trigger(boss);
        server.getScheduler().performTicks((int) RiftZonesAbility.CHECK_INTERVAL_TICKS);

        assertEquals(healthBefore, alice.getHealth(), "standing away from every rift zone must avoid the damage");
    }

    @Test
    void repeatedlyStandingInARiftZoneAccumulatesDamageAcrossMultipleChecks() {
        alice.teleport(new Location(world, 0, 64, 0));
        double healthBefore = alice.getHealth();
        RiftZonesAbility ability = new RiftZonesAbility(plugin, new Random(0));

        ability.trigger(boss);
        server.getScheduler().performTicks((int) (RiftZonesAbility.CHECK_INTERVAL_TICKS * 2));

        assertTrue(healthBefore - alice.getHealth() >= RiftZonesAbility.DAMAGE * 2 - 0.0001,
                "standing in a rift zone through two checks must deal damage twice");
    }
}
