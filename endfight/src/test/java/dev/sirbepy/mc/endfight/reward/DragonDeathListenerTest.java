package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.endfight.EndFightConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Bukkit-level filters (op's {@code /kill}, a dragon outside the End) that must spend no
 * reward at all - the same filters guild's {@code DragonSlayersTest} holds {@code DragonSlayers}
 * to for the First Dragon event. */
class DragonDeathListenerTest {

    private ServerMock server;
    private WorldMock end;
    private PlayerMock alice;
    private RewardService rewards;
    private DragonDeathListener listener;

    @BeforeEach
    void setUp(@org.junit.jupiter.api.io.TempDir File tempDir) {
        server = MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin("EndFight");
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        alice = server.addPlayer("Alice");
        EndFightConfig config = new EndFightConfig(new YamlConfiguration());
        rewards = new RewardService(plugin, () -> config,
                new PendingRewardsStore(new File(tempDir, "pending-rewards.yml"), Logger.getLogger("test")));
        listener = new DragonDeathListener(rewards);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EnderDragon dragon(World world) {
        return (EnderDragon) world.spawnEntity(new Location(world, 0, 80, 0), EntityType.ENDER_DRAGON);
    }

    private void melee(EnderDragon dragon) {
        listener.onHurt(new EntityDamageByEntityEvent(alice, dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), 6));
    }

    private boolean isTracking(EnderDragon dragon) {
        // An active tracking window suppresses the dragon's own orbs; no tracking means they pass through.
        ExperienceOrb orb = (ExperienceOrb) dragon.getWorld().spawnEntity(new Location(dragon.getWorld(), 0, 70, 0), EntityType.EXPERIENCE_ORB);
        orb.setExperience(1);
        return rewards.suppressIfDying(orb);
    }

    @Test
    void anOpsKillCommandSpendsNoReward() {
        EnderDragon dragon = dragon(end);
        melee(dragon);

        listener.onDeath(new EntityDeathEvent(dragon, DamageSource.builder(DamageType.GENERIC_KILL).build(), new ArrayList<>()));

        assertFalse(isTracking(dragon));
        assertEquals(0, alice.getTotalExperience());
    }

    @Test
    void aDragonDyingOutsideTheEndSpendsNoReward() {
        WorldMock overworld = server.addSimpleWorld("world");
        EnderDragon dragon = dragon(overworld);
        melee(dragon);

        listener.onDeath(new EntityDeathEvent(dragon, DamageSource.builder(DamageType.GENERIC).build(), new ArrayList<>()));

        assertFalse(isTracking(dragon));
        assertEquals(0, alice.getTotalExperience());
    }

    @Test
    void aRealKillInTheEndStartsTracking() {
        EnderDragon dragon = dragon(end);
        melee(dragon);

        listener.onDeath(new EntityDeathEvent(dragon, DamageSource.builder(DamageType.GENERIC).build(), new ArrayList<>()));

        assertTrue(isTracking(dragon));
    }

    @Test
    void theOrbListenerCancelsOnlyWhatRewardServiceSaysToSuppress() {
        EnderDragon dragon = dragon(end);
        melee(dragon);
        listener.onDeath(new EntityDeathEvent(dragon, DamageSource.builder(DamageType.GENERIC).build(), new ArrayList<>()));

        ExperienceOrb orb = (ExperienceOrb) end.spawnEntity(new Location(end, 0, 70, 0), EntityType.EXPERIENCE_ORB);
        orb.setExperience(40);
        EntitySpawnEvent spawnEvent = new EntitySpawnEvent(orb);
        listener.onOrbSpawn(spawnEvent);

        assertTrue(spawnEvent.isCancelled());
    }
}
