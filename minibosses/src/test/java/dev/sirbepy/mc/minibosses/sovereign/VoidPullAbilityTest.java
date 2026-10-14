package dev.sirbepy.mc.minibosses.sovereign;

import org.bukkit.Location;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused coverage for the Void Sovereign's Mythic-tier mechanic in isolation (see
 * {@code SovereignFightTest} for it firing as part of a real Mythic fight): a nearby fighter gets
 * shoved radially outward from the boss's trigger-time position every pulse, a fighter outside the
 * hazard's radius is left alone, and the shove direction actually points away from the boss.
 */
class VoidPullAbilityTest {

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
    void aNearbyPlayerIsShovedOutwardOnTheFirstPulse() {
        alice.teleport(new Location(world, 5, 64, 0));
        VoidPullAbility ability = new VoidPullAbility(plugin);

        ability.trigger(boss);
        server.getScheduler().performTicks((int) VoidPullAbility.PULSE_INTERVAL_TICKS);

        assertTrue(alice.getVelocity().getX() > 0,
                "a fighter standing on the +X side of the boss must be shoved further in +X");
        assertTrue(alice.getVelocity().getY() < 0, "the pull must carry a downward component toward a possible fall");
    }

    @Test
    void aPlayerOutsideTheRadiusIsUntouched() {
        alice.teleport(new Location(world, 1000, 64, 0));
        VoidPullAbility ability = new VoidPullAbility(plugin);

        ability.trigger(boss);
        server.getScheduler().performTicks((int) VoidPullAbility.PULSE_INTERVAL_TICKS);

        assertEquals(0.0, alice.getVelocity().lengthSquared(), 0.0001,
                "a fighter outside the hazard's radius must not be shoved");
    }

    @Test
    void pullKeepsFiringEveryPulseUntilTheBossDies() {
        alice.teleport(new Location(world, 5, 64, 0));
        VoidPullAbility ability = new VoidPullAbility(plugin);

        ability.trigger(boss);
        server.getScheduler().performTicks((int) (VoidPullAbility.PULSE_INTERVAL_TICKS * VoidPullAbility.PULSES) + 5);

        // After all PULSES have fired, the task cancels itself; a velocity reset confirms no more
        // pulses are still pending once that many ticks have elapsed.
        alice.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        server.getScheduler().performTicks((int) VoidPullAbility.PULSE_INTERVAL_TICKS);
        assertEquals(0.0, alice.getVelocity().lengthSquared(), 0.0001,
                "the void pull must stop after its bounded number of pulses");
    }
}
