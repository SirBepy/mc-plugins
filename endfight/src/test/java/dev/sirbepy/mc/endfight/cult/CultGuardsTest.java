package dev.sirbepy.mc.endfight.cult;

import dev.sirbepy.mc.endfight.arena.ArenaLayout;
import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.Pillar;
import dev.sirbepy.mc.endfight.arena.PillarScanner;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Vindicator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CultGuardsTest {

    private static final int SURFACE_Y = 64;

    private ServerMock server;
    private WorldMock end;
    private PluginMock plugin;
    private ArenaRegistry registry;
    private CultGuards guards;
    private CultConfig config;

    /** Mirrors arena's own (package-private) EndFightTestSupport.samplePillars - a real layout so
     * {@link ArenaRegistry#guardPosts()} has real posts to spawn guards at, built straight from the
     * same public {@link ArenaLayout#build} this phase isn't allowed to touch. */
    private static List<Pillar> samplePillars() {
        List<int[]> centers = PillarScanner.vanillaCenters();
        List<Pillar> pillars = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            pillars.add(new Pillar(centers.get(i)[0], centers.get(i)[1], 2 + i / 3, 76 + i * 3, SURFACE_Y));
        }
        return pillars;
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        plugin = MockBukkit.createMockPlugin("EndFight");
        registry = new ArenaRegistry();
        List<Pillar> pillars = samplePillars();
        registry.update(pillars, ArenaLayout.build(pillars, SURFACE_Y), true);
        config = new CultConfig(null);
        guards = new CultGuards(plugin, registry, () -> config);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock playerNearPortal() {
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(end, 10, SURFACE_Y, 10));
        return player;
    }

    private EnderDragon dragon() {
        return end.spawn(new Location(end, 0, 90, 0), EnderDragon.class);
    }

    @Test
    void noPlayerNearbyMeansNoGuardsSpawnYet() {
        EnderDragon dragon = dragon();

        boolean spawned = guards.spawnIfDue(dragon, config);

        assertFalse(spawned);
        assertEquals(0, guards.aliveCount());
    }

    @Test
    void aPlayerNearThePortalTriggersTheFirstGuardSpawn() {
        playerNearPortal();
        EnderDragon dragon = dragon();

        boolean spawned = guards.spawnIfDue(dragon, config);

        assertTrue(spawned);
        assertTrue(guards.aliveCount() > 0);
    }

    @Test
    void aSecondCheckNeverRespawnsAKilledGuard() {
        playerNearPortal();
        EnderDragon dragon = dragon();
        assertTrue(guards.spawnIfDue(dragon, config));
        assertTrue(guards.aliveCount() > 0);

        guards.clearAll(); // simulate every guard having been killed in the fight
        assertEquals(0, guards.aliveCount());

        boolean spawnedAgain = guards.spawnIfDue(dragon, config);

        assertFalse(spawnedAgain, "this dragon already had its one guard spawn");
        assertEquals(0, guards.aliveCount(), "a second check must not bring the killed guards back");
    }

    @Test
    void aNewDragonGetsItsOwnFreshGuardSpawn() {
        playerNearPortal();
        EnderDragon first = dragon();
        assertTrue(guards.spawnIfDue(first, config));
        guards.clearAll();

        EnderDragon second = end.spawn(new Location(end, 0, 95, 0), EnderDragon.class);
        boolean spawnedForNewDragon = guards.spawnIfDue(second, config);

        assertTrue(spawnedForNewDragon, "a brand new dragon has no spawned-guards flag on its PDC");
        assertTrue(guards.aliveCount() > 0);
    }

    @Test
    void forceSpawnIgnoresTheOncePerDragonRuleAndTheRangeCheck() {
        // No player anywhere near the portal - the natural check would refuse.
        EnderDragon dragon = dragon();

        int spawned = guards.forceSpawn();

        assertTrue(spawned > 0, "/endfight cult spawn always spawns, range or not");
        assertTrue(guards.aliveCount() > 0);
    }

    @Test
    void guardCompositionIncludesTheConfiguredBonusMobs() {
        dragon();
        guards.forceSpawn();

        long vindicators = end.getEntitiesByClass(Vindicator.class).stream()
                .filter(CultMarker::isCult).count();
        long evokers = end.getEntitiesByClass(Evoker.class).stream()
                .filter(CultMarker::isCult).count();

        assertTrue(vindicators > 0, "tall pillars' stair outposts should each add a vindicator");
        assertTrue(evokers > 0, "each gatehouse should add an evoker");
    }

    @Test
    void clearAllRemovesEveryCultMobButNothingElse() {
        dragon();
        guards.forceSpawn();
        LivingEntity bystander = end.spawn(new Location(end, 100, SURFACE_Y, 100), org.bukkit.entity.Zombie.class);
        assertTrue(guards.aliveCount() > 0);

        int removed = guards.clearAll();

        assertTrue(removed > 0);
        assertEquals(0, guards.aliveCount());
        assertTrue(bystander.isValid(), "clear only removes cult mobs");
    }
}
