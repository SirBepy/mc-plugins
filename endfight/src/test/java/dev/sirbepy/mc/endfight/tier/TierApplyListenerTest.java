package dev.sirbepy.mc.endfight.tier;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierApplyListenerTest {

    private static final long DELAY_TICKS = 40L;
    private static final Logger LOGGER = Logger.getLogger("tier-apply-test");

    private ServerMock server;
    private WorldMock end;
    private WorldMock overworld;
    private PluginMock plugin;
    private TierService tiers;
    private DkeTierPusher pusher;
    private TierApplyListener listener;

    @BeforeEach
    void setUp(@TempDir File dir) {
        server = MockBukkit.mock();
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        overworld = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin("EndFight");
        tiers = new TierService(new File(dir, "tier.yml"), Tier.FLEDGELING, () -> false, LOGGER);
        // DKE absent -> the fallback sets health directly, an easy real signal to assert on.
        pusher = new DkeTierPusher(() -> false, command -> { }, LOGGER);
        listener = new TierApplyListener(plugin, tiers, pusher, DELAY_TICKS);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EnderDragon dragon(WorldMock world) {
        return world.spawn(new Location(world, 0, 80, 0), EnderDragon.class);
    }

    private boolean isTagged(EnderDragon dragon) {
        return dragon.getPersistentDataContainer().has(new NamespacedKey("endfight", "tier-applied"), PersistentDataType.BOOLEAN);
    }

    @Test
    void aFreshDragonInTheEndIsTaggedImmediatelyButOnlyPushedAfterTheDelay() {
        EnderDragon dragon = dragon(end);
        double vanillaMax = dragon.getMaxHealth();

        listener.onSpawn(new CreatureSpawnEvent(dragon, CreatureSpawnEvent.SpawnReason.DEFAULT));

        assertTrue(isTagged(dragon), "the PDC flag must be set the moment this plugin decides to apply, not after the delay");
        assertEquals(Tier.FEROCIOUS.health(), tiers.current().health(), "the server's first-ever fight is forced to Ferocious");

        server.getScheduler().performTicks(DELAY_TICKS - 1);
        assertEquals(vanillaMax, dragon.getMaxHealth(), "should not have pushed yet just before the delay");
        server.getScheduler().performTicks(2);

        assertEquals(Tier.FEROCIOUS.health(), dragon.getMaxHealth());
    }

    @Test
    void aDragonSpawningOutsideTheEndIsNeverTaggedOrPushed() {
        EnderDragon dragon = dragon(overworld);

        listener.onSpawn(new CreatureSpawnEvent(dragon, CreatureSpawnEvent.SpawnReason.DEFAULT));
        server.getScheduler().performTicks(DELAY_TICKS + 5);

        assertTrue(!isTagged(dragon));
    }

    @Test
    void theSameDragonIsNeverAppliedTwice() {
        EnderDragon dragon = dragon(end);
        listener.onSpawn(new CreatureSpawnEvent(dragon, CreatureSpawnEvent.SpawnReason.DEFAULT));
        server.getScheduler().performTicks(DELAY_TICKS + 1);
        double afterFirst = dragon.getMaxHealth();

        tiers.setNext(Tier.ASCENDED, null);
        tiers.recordKill(); // no longer the server's first fight, so a second apply would actually change the tier
        listener.onSpawn(new CreatureSpawnEvent(dragon, CreatureSpawnEvent.SpawnReason.DEFAULT));
        server.getScheduler().performTicks(DELAY_TICKS + 1);

        assertEquals(afterFirst, dragon.getMaxHealth(), "a dragon already tagged must never be re-applied");
    }

    @Test
    void applyToExistingDragonCatchesTheOneVanillaAlreadySpawnedBeforeThisPluginCouldListen() {
        EnderDragon dragon = dragon(end);
        assertTrue(!isTagged(dragon));

        listener.applyToExistingDragon();

        assertTrue(isTagged(dragon));
        server.getScheduler().performTicks(DELAY_TICKS + 1);
        assertEquals(Tier.FEROCIOUS.health(), dragon.getMaxHealth());
    }

    @Test
    void applyToExistingDragonIsSafeToCallTwice() {
        EnderDragon dragon = dragon(end);

        listener.applyToExistingDragon();
        server.getScheduler().performTicks(DELAY_TICKS + 1);
        double afterFirst = dragon.getMaxHealth();

        tiers.setNext(Tier.ASCENDED, null);
        tiers.recordKill();
        listener.applyToExistingDragon();
        server.getScheduler().performTicks(DELAY_TICKS + 1);

        assertEquals(afterFirst, dragon.getMaxHealth(), "an already-tagged dragon must not be re-applied on a second scan");
    }
}
