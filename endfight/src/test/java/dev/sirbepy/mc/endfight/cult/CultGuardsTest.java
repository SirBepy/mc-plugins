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
    void theAutomaticCheckSkipsEntirelyWhileTheArenaIsBusyButSpawnIfDueStillWorks() {
        playerNearPortal();
        EnderDragon dragon = dragon();
        java.util.concurrent.atomic.AtomicBoolean busy = new java.util.concurrent.atomic.AtomicBoolean(true);
        CultGuards busyGuards = new CultGuards(plugin, registry, () -> config, busy::get);

        busyGuards.checkAndSpawn();
        assertEquals(0, busyGuards.aliveCount(), "the periodic check must skip while the island reset is busy");

        busy.set(false);
        busyGuards.checkAndSpawn();
        assertTrue(busyGuards.aliveCount() > 0, "the check resumes once the reset is no longer busy");
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

    // Joe, 2026-10-09: the Nether pillars are held by piglins and brutes, the rest by crossbows
    // and axes.
    @Test
    void netherPillarsGetPiglinsThatNeverZombifyAndTheRestGetIllagers() {
        dragon();
        guards.forceSpawn();

        List<LivingEntity> cult = end.getEntitiesByClass(LivingEntity.class).stream().filter(CultMarker::isCult).toList();
        long piglins = cult.stream().filter(e -> e instanceof org.bukkit.entity.Piglin).count();
        long brutes = cult.stream().filter(e -> e instanceof org.bukkit.entity.PiglinBrute).count();
        assertTrue(piglins >= 15, "a piglin on every Nether post (got " + piglins + ")");
        assertTrue(brutes >= 5, "a brute on every Nether outpost tier and balcony (got " + brutes + ")");
        for (LivingEntity e : cult) {
            if (e instanceof org.bukkit.entity.PiglinAbstract piglin) {
                assertTrue(piglin.isImmuneToZombification());
                assertTrue(piglin.isAdult());
                assertEquals(e instanceof org.bukkit.entity.Piglin ? "piglin" : "piglin_brute", CultMarker.type(e));
                assertTrue(CultMarker.postIndex(e) >= 0);
            }
        }
        List<dev.sirbepy.mc.endfight.arena.GuardPost> posts = registry.guardPosts();
        for (LivingEntity e : cult) {
            dev.sirbepy.mc.endfight.arena.GuardPost post = posts.get(CultMarker.postIndex(e));
            boolean piglin = e instanceof org.bukkit.entity.PiglinAbstract;
            assertEquals(post.faction() == dev.sirbepy.mc.endfight.arena.Faction.NETHER, piglin, "faction of " + post);
        }
    }

    // Performance (Joe, 2026-10-09): a guard with nobody near thinks nothing.
    @Test
    void guardsSleepWithNobodyNearAndWakeWhenSomeoneComesOrHurtsThem() {
        dragon();
        guards.forceSpawn();
        List<org.bukkit.entity.Mob> all = end.getEntitiesByClass(org.bukkit.entity.Mob.class).stream()
                .filter(m -> CultMarker.postIndex(m) >= 0).toList();
        org.bukkit.entity.Mob guard = all.get(0);

        GuardTether.tickAll(end, registry.guardPosts(), config.guards());
        assertTrue(all.stream().noneMatch(org.bukkit.entity.Mob::isAware), "nobody in the End: all asleep");

        PlayerMock player = server.addPlayer();
        player.teleport(guard.getLocation().clone().add(5, 0, 0));
        GuardTether.tickAll(end, registry.guardPosts(), config.guards());
        assertTrue(guard.isAware(), "awake with a player 5 blocks off");
        assertTrue(all.stream().anyMatch(m -> !m.isAware()), "guards far from that player stay asleep");

        org.bukkit.entity.Mob sleeper = all.stream().filter(m -> !m.isAware()).findFirst().orElseThrow();
        guards.onGuardHurt(new org.bukkit.event.entity.EntityDamageEvent(sleeper,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE, 2));
        assertTrue(sleeper.isAware(), "hurting a sleeping guard wakes it");
    }
}
