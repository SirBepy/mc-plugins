package dev.sirbepy.mc.minibosses.sentinel;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Ravager;
import org.bukkit.potion.PotionEffectType;
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
 * Focused coverage for the Overgrown Sentinel's Heroic-tier mechanic in isolation (see
 * {@code SentinelFightTest} for it firing as part of a real Heroic fight): standing still near the
 * boss while the vines are up gets you bitten every check, moving between checks does not.
 */
class ThornVinesAbilityTest {

    private ServerMock server;
    private PluginMock plugin;
    private WorldMock world;
    private PlayerMock alice;
    private LivingEntity boss;
    private ThornVinesAbility ability;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        alice.teleport(new Location(world, 0, 64, 0));
        boss = (Ravager) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.RAVAGER);
        ability = new ThornVinesAbility(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void standingStillTakesDamageAndPoisonAtEveryCheck() {
        double healthBefore = alice.getHealth();
        ability.trigger(boss);

        server.getScheduler().performTicks((int) ThornVinesAbility.CHECK_INTERVAL_TICKS);

        assertTrue(alice.getHealth() < healthBefore, "standing still through a check must deal damage");
        assertTrue(alice.hasPotionEffect(PotionEffectType.POISON));
    }

    @Test
    void movingBetweenChecksAvoidsTheDamage() {
        double healthBefore = alice.getHealth();
        ability.trigger(boss);

        // Move well past the movement threshold before the first check lands.
        alice.teleport(new Location(world, 10, 64, 10));
        server.getScheduler().performTicks((int) ThornVinesAbility.CHECK_INTERVAL_TICKS);

        assertEquals(healthBefore, alice.getHealth(), "moving between checks must avoid the vine damage");
    }

    @Test
    void repeatedStandingStillAccumulatesDamageAcrossMultipleChecks() {
        double healthBefore = alice.getHealth();
        ability.trigger(boss);

        server.getScheduler().performTicks((int) (ThornVinesAbility.CHECK_INTERVAL_TICKS * 2));

        assertTrue(healthBefore - alice.getHealth() >= ThornVinesAbility.DAMAGE * 2 - 0.0001,
                "standing still through two checks must deal damage twice");
    }
}
