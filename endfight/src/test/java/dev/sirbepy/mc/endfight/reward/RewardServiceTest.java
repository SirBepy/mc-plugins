package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.endfight.EndFightConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.EnderDragonMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** @see RewardService for the javap trail backing the orb-summing XP mechanism this exercises. */
class RewardServiceTest {

    private ServerMock server;
    private PluginMock plugin;
    private WorldMock end;
    private PlayerMock alice;
    private PlayerMock bob;
    private RewardService rewards;
    private EndFightConfig enabledConfig;

    @BeforeEach
    void setUp(@org.junit.jupiter.api.io.TempDir File tempDir) {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("EndFight");
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        enabledConfig = new EndFightConfig(new YamlConfiguration());
        PendingRewardsStore pending = new PendingRewardsStore(new File(tempDir, "pending-rewards.yml"), Logger.getLogger("test"));
        rewards = new RewardService(plugin, () -> enabledConfig, pending);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EnderDragon dragon() {
        return (EnderDragon) end.spawnEntity(new Location(end, 0, 80, 0), EntityType.ENDER_DRAGON);
    }

    private void melee(PlayerMock player, EnderDragon dragon) {
        rewards.fighters().addFighter(dragon.getPersistentDataContainer(), player.getUniqueId());
    }

    private void giveOrb(World world, int experience) {
        ExperienceOrb orb = (ExperienceOrb) world.spawnEntity(new Location(world, 0, 70, 0), EntityType.EXPERIENCE_ORB);
        orb.setExperience(experience);
        assertTrue(rewards.suppressIfDying(orb), "expected an active death-animation window for this world");
    }

    @Test
    void participantsAreEveryFighterPlusTheKiller() {
        EnderDragon dragon = dragon();
        melee(alice, dragon);
        ((EnderDragonMock) dragon).setKiller(bob);

        rewards.onDragonDeath(dragon);
        giveOrb(end, 500);
        rewards.finalizeRewards(end.getUID());

        assertTrue(alice.getTotalExperience() > 0, "the fighter must be paid");
        assertTrue(bob.getTotalExperience() > 0, "the killer must be paid even without a melee hit");
    }

    @Test
    void firstKillAwardsTheFullTwelveThousandSummedFromSuppressedOrbs() {
        EnderDragon dragon = dragon();
        melee(alice, dragon);
        rewards.onDragonDeath(dragon);

        // Vanilla spreads the total across 10 batches; summing must equal the full total regardless
        // of batching, which is the whole point of reading it off the orbs instead of a flag.
        for (int i = 0; i < 10; i++) {
            giveOrb(end, 1200);
        }
        rewards.finalizeRewards(end.getUID());

        assertEquals(12000, alice.getTotalExperience());
    }

    @Test
    void aRespawnedDragonAwardsOnlyFiveHundred() {
        EnderDragon dragon = dragon();
        melee(alice, dragon);
        rewards.onDragonDeath(dragon);

        for (int i = 0; i < 10; i++) {
            giveOrb(end, 50);
        }
        rewards.finalizeRewards(end.getUID());

        assertEquals(500, alice.getTotalExperience());
    }

    @Test
    void aDragonWithNoFightersAndNoKillerNeverStartsTracking() {
        EnderDragon dragon = dragon();
        // Nobody melees, nobody is set as killer: there is nobody to pay, so nothing should track.
        rewards.onDragonDeath(dragon);

        ExperienceOrb orb = (ExperienceOrb) end.spawnEntity(new Location(end, 0, 70, 0), EntityType.EXPERIENCE_ORB);
        orb.setExperience(500);
        assertFalse(rewards.suppressIfDying(orb), "no participants means no death-animation window was opened");

        rewards.finalizeRewards(end.getUID());

        assertEquals(0, rewards.pendingCount());
        assertEquals(0, alice.getTotalExperience());
    }

    @Test
    void anOfflineParticipantIsPaidOnceOnTheirNextJoin() {
        UUID offlineId = UUID.randomUUID();
        EnderDragon dragon = dragon();
        rewards.fighters().addFighter(dragon.getPersistentDataContainer(), offlineId);
        rewards.onDragonDeath(dragon);
        giveOrb(end, 500);

        rewards.finalizeRewards(end.getUID());
        assertEquals(1, rewards.pendingCount(), "offline at the kill must queue, not drop, the reward");

        PlayerMock nowOnline = new PlayerMock(server, "Offliner", offlineId);
        server.addPlayer(nowOnline);
        rewards.deliverPending(nowOnline);

        assertEquals(500, nowOnline.getTotalExperience());
        assertEquals(0, rewards.pendingCount());

        // Delivered once: a second join must not pay again.
        rewards.deliverPending(nowOnline);
        assertEquals(500, nowOnline.getTotalExperience());
    }

    @Test
    void rewardsDisabledPaysNothingAndNeverSuppressesAnOrb() {
        YamlConfiguration disabled = new YamlConfiguration();
        disabled.set("rewards.enabled", false);
        EndFightConfig disabledConfig = new EndFightConfig(disabled);
        RewardService disabledRewards = new RewardService(plugin, () -> disabledConfig,
                new PendingRewardsStore(new File(plugin.getDataFolder(), "pending-rewards-disabled.yml"), Logger.getLogger("test")));

        EnderDragon dragon = dragon();
        disabledRewards.fighters().addFighter(dragon.getPersistentDataContainer(), alice.getUniqueId());
        disabledRewards.onDragonDeath(dragon);

        ExperienceOrb orb = (ExperienceOrb) end.spawnEntity(new Location(end, 0, 70, 0), EntityType.EXPERIENCE_ORB);
        orb.setExperience(500);
        assertFalse(disabledRewards.suppressIfDying(orb), "with rewards off, vanilla's own orbs must fall through untouched");

        disabledRewards.finalizeRewards(end.getUID());
        assertEquals(0, alice.getTotalExperience());
        assertEquals(0, disabledRewards.pendingCount());
    }
}
