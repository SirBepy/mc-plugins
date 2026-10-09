package dev.sirbepy.mc.endfight.cult;

import dev.sirbepy.mc.endfight.arena.ArenaLayout;
import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.Pillar;
import dev.sirbepy.mc.endfight.arena.PillarScanner;
import dev.sirbepy.mc.endfight.fight.FightStats;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Vindicator;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CultWavesTest {

    private static final int SURFACE_Y = 64;
    private static final double MAX = 200;

    private ServerMock server;
    private WorldMock end;
    private ArenaRegistry registry;
    private CultConfig config;
    private double mspt;
    private WaveSpawner spawner;
    private CultWaves waves;

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
        registry = new ArenaRegistry();
        List<Pillar> pillars = samplePillars();
        registry.update(pillars, ArenaLayout.build(pillars, SURFACE_Y), true);
        config = new CultConfig(null);
        mspt = 10;
        Plugin plugin = MockBukkit.createMockPlugin();
        spawner = new WaveSpawner(plugin, registry, () -> config, () -> mspt);
        spawner.start();
        server.getPluginManager().registerEvents(spawner, plugin);
        waves = new CultWaves(() -> config, spawner);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EnderDragon dragon() {
        EnderDragon dragon = end.spawn(new Location(end, 0, 90, 0), EnderDragon.class);
        dragon.registerAttribute(Attribute.MAX_HEALTH);
        dragon.getAttribute(Attribute.MAX_HEALTH).setBaseValue(MAX);
        dragon.setHealth(MAX);
        return dragon;
    }

    /** A hit as the server fires it: the event sees the health from before the hit, which only
     * lands afterwards. */
    private void hit(EnderDragon dragon, double damage) {
        waves.onDamage(new EntityDamageEvent(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage));
        dragon.setHealth(Math.max(0, dragon.getHealth() - damage));
    }

    private <T extends LivingEntity> long cult(Class<T> type) {
        return end.getEntitiesByClass(type).stream().filter(CultMarker::isCult).count();
    }

    private int aliveCult() {
        return CultRange.aliveCultCount(end);
    }

    private PlayerMock fighter(double x, double z) {
        PlayerMock player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(new Location(end, x, SURFACE_Y + 1, z));
        return player;
    }

    @Test
    void ninetyPercentSendsScoutsOutOfTheGatesOverTheNextTicks() {
        EnderDragon dragon = dragon();

        hit(dragon, 30); // 200 -> 170, 85%

        assertEquals(0, aliveCult(), "nothing spawns in the hit's own tick");
        server.getScheduler().performTicks(60);
        // nobody fighting: 2 pillagers + 1 vindicator
        assertEquals(2, cult(Pillager.class));
        assertEquals(1, cult(Vindicator.class));
        assertEquals(3, aliveCult());
    }

    // The hit that crosses a threshold is the one that does it: the health the event sees is
    // from before the hit, so 151 -> 149 on a 200 dragon crosses 75% right then, not a hit later.
    @Test
    void theHitThatCrossesAThresholdFiresItsWave() {
        EnderDragon dragon = dragon();
        dragon.setHealth(181);
        hit(dragon, 2); // 181 -> 179, under 90%
        server.getScheduler().performTicks(60);

        assertEquals(3, aliveCult());
    }

    @Test
    void wavesScaleWithThePlayersFightingButNotWithCreativeOnes() {
        for (int i = 0; i < 4; i++) {
            fighter(10 + i, 10);
        }
        PlayerMock op = server.addPlayer();
        op.setGameMode(GameMode.CREATIVE);
        op.teleport(new Location(end, 5, SURFACE_Y + 1, 5));
        EnderDragon dragon = dragon();

        hit(dragon, 30);
        server.getScheduler().performTicks(60);

        // 4 fighters: pillagers 2 + 4/2, vindicators 1 + 4/3
        assertEquals(4, cult(Pillager.class));
        assertEquals(2, cult(Vindicator.class));
    }

    // A full arena has more guards (one per post, ~100) than the wave cap, which must not swallow
    // every wave: the cap only counts wave mobs.
    @Test
    void guardsBeyondTheCapStillLeaveRoomForAWave() {
        for (int i = 0; i < 160; i++) {
            CultMarker.markGuard(end.spawn(new Location(end, 20, 65, 0), Pillager.class), i);
        }
        EnderDragon dragon = dragon();

        hit(dragon, 30);
        server.getScheduler().performTicks(60);

        assertEquals(163, aliveCult(), "160 guards plus the scouts' 3 mobs");
    }

    @Test
    void theCapCountsMobsStillQueued() {
        config = new CultConfig(section("max-alive", 4));
        EnderDragon dragon = dragon();

        hit(dragon, 60); // 200 -> 140: 90% (3 mobs) and 75% (8 mobs) in one hit
        server.getScheduler().performTicks(100);

        assertEquals(4, aliveCult(), "never past the cap, even with two waves queued at once");
    }

    @Test
    void aSecondHitThatDoesNotCrossAnotherThresholdSpawnsNothingMore() {
        EnderDragon dragon = dragon();
        hit(dragon, 30);
        server.getScheduler().performTicks(60);
        int afterFirstWave = aliveCult();

        hit(dragon, 5);
        server.getScheduler().performTicks(60);

        assertEquals(afterFirstWave, aliveCult(), "still above 75%, no second wave yet");
    }

    @Test
    void oneHugeHitFiresEveryWaveAndTheyAllComeOut() {
        EnderDragon dragon = dragon();

        hit(dragon, 190); // to 5%

        server.getScheduler().performTicks(200);
        // nobody fighting, so no phantoms (no one to send them after):
        // 90%: 2 pillagers, 1 vindicator; 75%: 4 pillagers, 2 vindicators, 2 piglins;
        // 50%: 1 brute; 35%: 1 evoker, 1 ravager, 2 vindicators, 1 brute; 15%: 4 paratroopers
        assertEquals(6, cult(Pillager.class));
        assertEquals(5, cult(Vindicator.class));
        assertEquals(1, cult(Evoker.class));
        assertEquals(1, cult(Ravager.class));
        assertEquals(2, cult(Piglin.class));
        assertEquals(6, cult(PiglinBrute.class), "2 brutes and 4 paratroopers");
        assertEquals(0, cult(Phantom.class));
    }

    @Test
    void nothingNewSpawnsWhileTheServerIsOverItsTickBudget() {
        EnderDragon dragon = dragon();
        mspt = 45;

        hit(dragon, 30);
        server.getScheduler().performTicks(100);
        assertEquals(0, aliveCult(), "paused at 45 mspt");

        mspt = 30;
        server.getScheduler().performTicks(60);
        assertEquals(3, aliveCult(), "the wave comes out once the server recovers");
    }

    @Test
    void aDeadDragonsQueuedWaveNeverComes() {
        EnderDragon dragon = dragon();
        hit(dragon, 190);
        server.getScheduler().performTicks(2);
        int early = aliveCult();

        spawner.onDragonDeath(new EntityDeathEvent(dragon, org.bukkit.damage.DamageSource.builder(
                org.bukkit.damage.DamageType.GENERIC).build(), new ArrayList<>()));
        server.getScheduler().performTicks(200);

        assertTrue(early < 26, "some were still queued when it died");
        assertEquals(early, aliveCult(), "nothing more after the death");
        assertEquals(0, spawner.queued());
    }

    @Test
    void piglinsDroppedOffTheDragonFloatDownAsAdultsThatNeverZombify() {
        EnderDragon dragon = dragon();
        dragon.setHealth(151);

        hit(dragon, 2); // through 90% and 75%
        server.getScheduler().performTicks(60);

        List<Piglin> piglins = end.getEntitiesByClass(Piglin.class).stream().filter(CultMarker::isCult).toList();
        assertEquals(2, piglins.size());
        for (Piglin piglin : piglins) {
            assertTrue(piglin.isAdult());
            assertTrue(piglin.isImmuneToZombification());
            assertTrue(piglin.hasPotionEffect(PotionEffectType.SLOW_FALLING));
            assertEquals("piglin", CultMarker.type(piglin));
            assertTrue(piglin.getLocation().getY() <= 90 - WaveSpawner.BELOW_DRAGON + 0.01, "under the dragon, clear of its wings");
            assertTrue(Math.hypot(piglin.getLocation().getX(), piglin.getLocation().getZ()) < 5, "dropped where the dragon is");
        }
    }

    @Test
    void theLastFifteenPercentDropsGlidingParatroopersOffTheDragon() {
        EnderDragon dragon = dragon();
        dragon.setHealth(40);
        hit(dragon, 9); // 20% -> 15.5%, not yet
        server.getScheduler().performTicks(60);
        assertEquals(0, end.getEntitiesByClass(PiglinBrute.class).stream()
                .filter(b -> Paratroopers.TYPE.equals(CultMarker.type(b))).count(), "not before 15%");

        hit(dragon, 2);
        server.getScheduler().performTicks(60);

        List<PiglinBrute> brutes = end.getEntitiesByClass(PiglinBrute.class).stream()
                .filter(b -> Paratroopers.TYPE.equals(CultMarker.type(b))).toList();
        assertEquals(4, brutes.size(), "4 paratroopers with nobody in range");
        for (PiglinBrute brute : brutes) {
            assertTrue(brute.isImmuneToZombification());
            assertTrue(brute.isGliding());
            assertEquals(Material.ELYTRA, brute.getEquipment().getChestplate().getType());
            assertEquals(0f, brute.getEquipment().getChestplateDropChance());
            assertTrue(brute.getLocation().getY() > SURFACE_Y + 10, "they start up by the dragon");
        }
    }

    // Joe, 2026-10-09: at 50%, 3 phantoms on each of the top dragon damage dealers.
    @Test
    void fiftyPercentSendsThreePhantomsAfterEachTopDamageDealer() {
        List<PlayerMock> players = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            players.add(fighter(10 + 3 * i, -10));
        }
        EnderDragon dragon = dragon();
        dragon.setHealth(101);
        for (int i = 0; i < 8; i++) {
            // player i dealt 10 + i: players 2..7 are the top 6
            FightStats.addDamage(dragon.getPersistentDataContainer(), players.get(i).getUniqueId(), 10 + i);
        }

        hit(dragon, 2);
        server.getScheduler().performTicks(100);

        List<Phantom> phantoms = end.getEntitiesByClass(Phantom.class).stream().filter(CultMarker::isCult).toList();
        assertEquals(18, phantoms.size());
        for (int i = 0; i < 8; i++) {
            PlayerMock player = players.get(i);
            long on = phantoms.stream().filter(p -> Objects.equals(p.getTarget(), player)).count();
            assertEquals(i >= 2 ? 3 : 0, on, "phantoms on player " + i);
        }
        for (Phantom phantom : phantoms) {
            Location target = phantom.getTarget().getLocation();
            assertTrue(phantom.getLocation().getY() > target.getY() + 10, "they start high over their player");
        }
    }

    @Test
    void aZeroDamageHitNeverCountsAsCrossingAnything() {
        EnderDragon dragon = dragon();

        hit(dragon, 0);
        server.getScheduler().performTicks(60);

        assertEquals(0, aliveCult());
    }

    @Test
    void wavesDisabledInConfigSpawnsNothing() {
        config = new CultConfig(section("waves.enabled", false));
        EnderDragon dragon = dragon();

        hit(dragon, 30);
        server.getScheduler().performTicks(60);

        assertEquals(0, aliveCult());
    }

    @Test
    void shippedConfigMatchesTheBuiltInSchedule() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
        CultConfig shipped = new CultConfig(yaml.getConfigurationSection("cult"));
        CultConfig builtIn = new CultConfig(null);

        assertEquals(builtIn.waves().schedule(), shipped.waves().schedule());
        assertEquals(List.of(0.90, 0.75, 0.50, 0.35, 0.15), shipped.waves().thresholds());
        assertEquals(builtIn.guards().composition(), shipped.guards().composition());
        assertEquals(builtIn.guards().netherComposition(), shipped.guards().netherComposition());
        assertEquals(150, shipped.maxAlive());
        assertFalse(shipped.waves().schedule().isEmpty());
    }

    @Test
    void aDragonOutOverTheVoidDropsItsTroopsBackOverTheIsland() {
        double[] clamped = WaveSpawner.clampToRadius(120, 0, 60);
        assertEquals(60, clamped[0], 1e-9);
        assertEquals(0, clamped[1], 1e-9);
        double[] inside = WaveSpawner.clampToRadius(30, -20, 60);
        assertEquals(30, inside[0], 1e-9);
        assertEquals(-20, inside[1], 1e-9);
    }

    private static YamlConfiguration section(String key, Object value) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(key, value);
        return yaml;
    }
}
